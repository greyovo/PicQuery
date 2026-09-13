#!/usr/bin/env python3
"""Prepare official MobileCLIP v1 models for labelled host accuracy evaluation.

Only writes to build/mobileclip-accuracy-models by default. No Android assets
are changed. The image tower is FP32 and the text tower uses the same standard
ORT dynamic 8-bit weight recipe as the MobileCLIP2 comparison.
"""

from __future__ import annotations

import argparse
from concurrent.futures import ThreadPoolExecutor
import hashlib
import importlib.metadata
import json
from pathlib import Path
import urllib.request

import mobileclip
import numpy as np
import onnx
import onnxruntime as ort
import open_clip
from onnxruntime.quantization import QuantType, quantize_dynamic
import torch
from torch import nn
from mobileclip.modules.common.mobileone import reparameterize_model


CHECKPOINTS = {
    "s0": {
        "bytes": 215934653,
        "sha256": "809b408eff74f8058843e86a1f92967097d42ba782450e85b8f4867b7f0ca0b7",
    },
    "s2": {
        "bytes": 398067246,
        "sha256": "063a87b2a846791bcffafa9f7670ec3968d572a3e7f99e5e4b14348006631d6f",
    },
}


def sha256(path: Path) -> str:
    with path.open("rb") as source:
        return hashlib.file_digest(source, "sha256").hexdigest()


def download_checkpoint(variant: str, directory: Path) -> Path:
    directory.mkdir(parents=True, exist_ok=True)
    path = directory / f"mobileclip_{variant}.pt"
    expected = CHECKPOINTS[variant]
    if path.exists() and path.stat().st_size == expected["bytes"] and sha256(path) == expected["sha256"]:
        print(f"Using verified checkpoint {path}", flush=True)
        return path
    url = f"https://docs-assets.developer.apple.com/ml-research/datasets/mobileclip/mobileclip_{variant}.pt"
    temporary = path.with_suffix(".pt.part")
    print(f"Downloading {url}", flush=True)
    with urllib.request.urlopen(url, timeout=60) as response, temporary.open("wb") as output:
        while chunk := response.read(4 * 1024 * 1024):
            output.write(chunk)
    if temporary.stat().st_size != expected["bytes"] or sha256(temporary) != expected["sha256"]:
        raise ValueError(f"Checkpoint integrity mismatch: {temporary}")
    temporary.replace(path)
    print(f"Verified downloaded checkpoint {path}", flush=True)
    return path


def load_reference(variant: str, checkpoint: Path):
    """Return official eval/reparameterized PyTorch model, transform and tokenizer."""
    model_name = f"mobileclip_{variant}"
    model, _, preprocess = mobileclip.create_model_and_transforms(
        model_name, pretrained=str(checkpoint), reparameterize=False, device="cpu"
    )
    model = reparameterize_model(model.eval()).eval()
    return model, preprocess, mobileclip.get_tokenizer(model_name)


def verify_tokenizers(variant: str, directory: Path, data_root: Path) -> None:
    """Verify exactly the CIFAR100 class prompts used by evaluate_accuracy.py."""
    from torchvision.datasets import CIFAR100

    dataset = CIFAR100(data_root, train=False, download=False)
    prompts = [f"a photo of a {name.replace('_', ' ')}" for name in dataset.classes]
    native = mobileclip.get_tokenizer(f"mobileclip_{variant}")(prompts).numpy().astype(np.int32)
    shared = open_clip.get_tokenizer("MobileCLIP2-S0")(prompts).numpy().astype(np.int32)
    if native.shape != (100, 77) or not np.array_equal(native, shared):
        raise AssertionError(f"v1 {variant} / MobileCLIP2 tokenizer mismatch for CIFAR100 prompts")
    path = directory / "metadata.json"
    metadata = json.loads(path.read_text(encoding="utf-8"))
    metadata["tokenizer"]["cifar100_prompt_verification"] = {
        "reference": "open_clip.get_tokenizer('MobileCLIP2-S0')",
        "prompt_template": "a photo of a {class}", "class_name_normalization": "underscore to space",
        "prompt_count": len(prompts), "shape": list(native.shape), "identical": True,
        "prompts_sha256": hashlib.sha256(json.dumps(prompts).encode()).hexdigest(),
        "tokens_int32_le_sha256": hashlib.sha256(native.astype("<i4").tobytes()).hexdigest(),
    }
    path.write_text(json.dumps(metadata, indent=2) + "\n", encoding="utf-8")
    print(f"Verified all 100 CIFAR100 prompts: {path}", flush=True)


def quantization_coverage(path: Path) -> dict:
    graph = onnx.load(str(path))
    return {
        "initializer_elements_by_dtype": {
            onnx.TensorProto.DataType.Name(dtype): sum(
                int(np.prod(value.dims)) for value in graph.graph.initializer if value.data_type == dtype
            ) for dtype in sorted({value.data_type for value in graph.graph.initializer})
        },
        "float_convolution_count": sum(node.op_type == "Conv" for node in graph.graph.node),
        "integer_matmul_count": sum(node.op_type == "MatMulInteger" for node in graph.graph.node),
        "precision_note": "Same ORT operator recipe across generations; S0 text convolutions remain FP32.",
    }


class ImageTower(nn.Module):
    def __init__(self, model: nn.Module):
        super().__init__()
        self.model = model

    def forward(self, image):
        features = self.model.encode_image(image)
        return features / features.norm(dim=-1, keepdim=True)


class TextTower(nn.Module):
    def __init__(self, model: nn.Module):
        super().__init__()
        self.model = model

    def forward(self, text):
        features = self.model.encode_text(text)
        return features / features.norm(dim=-1, keepdim=True)


def prepare(variant: str, checkpoint: Path, directory: Path, threads: int):
    directory.mkdir(parents=True, exist_ok=True)
    model, preprocess, tokenizer = load_reference(variant, checkpoint)
    metadata = {
        "model": f"MobileCLIP-{variant.upper()}", "generation": 1,
        "checkpoint": str(checkpoint), "checkpoint_sha256": sha256(checkpoint),
        "checkpoint_source": f"https://huggingface.co/apple/MobileCLIP-{variant.upper()}",
        "pretrained": "datacompdr", "normalized_outputs": True,
        "preprocessing": {"size": 256, "resize_mode": "shortest", "center_crop": True,
                          "interpolation": "bilinear", "mean": [0, 0, 0], "std": [1, 1, 1],
                          "official_transform": str(preprocess)},
        "tokenizer": {"api": f"mobileclip.get_tokenizer('mobileclip_{variant}')",
                      "context_length": 77, "vocab_size": 49408, "dtype": "int32"},
        "versions": {name: importlib.metadata.version(name) for name in
                     ("torch", "mobileclip", "open_clip_torch", "onnx", "onnxruntime")},
        "notes": "Official v1 reference, not proof of the old app's unidentified .ort checkpoint variant.",
    }
    samples = {
        "image": [torch.rand(1, 3, 256, 256), torch.zeros(1, 3, 256, 256)],
        "text": [tokenizer([text]).to(torch.int32) for text in
                 ("a photo of a dog", "a photo of a cat", "an astronaut in a space suit", "")],
    }
    options = ort.SessionOptions()
    options.intra_op_num_threads = threads
    options.inter_op_num_threads = 1
    for kind, tower in (("image", ImageTower(model).eval()), ("text", TextTower(model).eval())):
        reference = directory / f"mobileclip_{variant}_{kind}_fp32.onnx"
        with torch.inference_mode():
            torch.onnx.export(tower, (samples[kind][0],), str(reference),
                              dynamo=False, opset_version=17, external_data=False,
                              input_names=[kind], output_names=["embedding"],
                              do_constant_folding=True)
        onnx.checker.check_model(str(reference), full_check=True)
        runtime = ort.InferenceSession(str(reference), options, providers=["CPUExecutionProvider"])
        max_error = 0.0
        reference_vectors = []
        for value in samples[kind]:
            with torch.inference_mode():
                expected = tower(value).numpy()
            actual = runtime.run(None, {kind: value.numpy()})[0]
            np.testing.assert_allclose(actual, expected, atol=3e-5, rtol=3e-3)
            np.testing.assert_allclose(np.linalg.norm(actual, axis=-1), 1, atol=1e-5)
            reference_vectors.append(expected)
            max_error = max(max_error, float(np.max(np.abs(actual - expected))))
        metadata[kind] = {
            "file": reference.name, "bytes": reference.stat().st_size,
            "sha256": sha256(reference), "input_name": kind,
            "input_dtype": str(samples[kind][0].numpy().dtype),
            "input_shape": list(samples[kind][0].shape), "output_shape": [1, 512],
            "fp32_pytorch_max_absolute_error": max_error,
        }
        if kind == "text":
            quantized = directory / f"mobileclip_{variant}_text_int8.onnx"
            quantize_dynamic(str(reference), str(quantized), weight_type=QuantType.QInt8,
                             per_channel=True, op_types_to_quantize=["MatMul", "Gemm", "Gather"],
                             extra_options={"MatMulConstBOnly": True})
            onnx.checker.check_model(str(quantized), full_check=True)
            quant_runtime = ort.InferenceSession(str(quantized), options, providers=["CPUExecutionProvider"])
            cosines = []
            for value, expected in zip(samples[kind], reference_vectors):
                actual = quant_runtime.run(None, {kind: value.numpy()})[0]
                np.testing.assert_allclose(np.linalg.norm(actual, axis=-1), 1, atol=1e-5)
                cosines.append(float((actual * expected).sum() / (np.linalg.norm(actual) * np.linalg.norm(expected))))
            metadata["text_int8"] = {
                "file": quantized.name, "bytes": quantized.stat().st_size,
                "sha256": sha256(quantized), "input_name": "text", "input_dtype": "int32",
                "input_shape": [1, 77], "output_shape": [1, 512],
                "quantization": "ORT dynamic per-channel INT8 MatMul/Gemm + per-tensor 8-bit Gather",
                "pytorch_sample_cosines": cosines,
                **quantization_coverage(quantized),
            }
        print(f"Verified {variant} {kind}: {metadata[kind]}", flush=True)
    path = directory / "metadata.json"
    path.write_text(json.dumps(metadata, indent=2) + "\n", encoding="utf-8")
    print(f"READY {path}", flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--variants", nargs="+", choices=CHECKPOINTS, default=["s0", "s2"])
    parser.add_argument("--output-dir", type=Path, default=Path("build/mobileclip-accuracy-models"))
    parser.add_argument("--threads", type=int, default=4)
    parser.add_argument("--download-only", action="store_true")
    parser.add_argument("--verify-tokenizers-only", action="store_true")
    parser.add_argument("--data-root", type=Path, default=Path("build/mobileclip-accuracy-data"))
    args = parser.parse_args()
    if args.threads < 1:
        parser.error("--threads must be positive")
    torch.set_num_threads(args.threads)
    torch.set_grad_enabled(False)
    torch.backends.mha.set_fastpath_enabled(False)
    torch.manual_seed(20260913)
    if args.verify_tokenizers_only:
        for name in args.variants:
            verify_tokenizers(name, args.output_dir / name, args.data_root)
        return
    with ThreadPoolExecutor(max_workers=2) as pool:
        downloads = {name: pool.submit(download_checkpoint, name, args.output_dir / "checkpoints")
                     for name in args.variants}
        for name in args.variants:
            checkpoint = downloads[name].result()
            if not args.download_only:
                prepare(name, checkpoint, args.output_dir / name, args.threads)


if __name__ == "__main__":
    main()
