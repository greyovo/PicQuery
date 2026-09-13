#!/usr/bin/env python3
"""Export and verify the official MobileCLIP2-S0 towers without LiteRT imports."""

from __future__ import annotations

import argparse
import hashlib
import importlib.metadata
import json
from pathlib import Path

import numpy as np
import onnx
import onnxruntime as ort
import open_clip
import torch
from mobileclip.modules.common.mobileone import reparameterize_model
from open_clip.pretrained import download_pretrained
from onnxruntime.quantization import QuantType, quantize_dynamic
from torch import nn


class ImageTower(nn.Module):
    def __init__(self, model: nn.Module) -> None:
        super().__init__()
        self.model = model

    def forward(self, image: torch.Tensor) -> torch.Tensor:
        features = self.model.encode_image(image)
        return features / features.norm(dim=-1, keepdim=True)


class TextTower(nn.Module):
    def __init__(self, model: nn.Module) -> None:
        super().__init__()
        self.model = model

    def forward(self, text: torch.Tensor) -> torch.Tensor:
        features = self.model.encode_text(text)
        return features / features.norm(dim=-1, keepdim=True)


def sha256(path: Path) -> str:
    with path.open("rb") as source:
        return hashlib.file_digest(source, "sha256").hexdigest()


def quantize_text(reference_path: Path, output_path: Path) -> None:
    """Keep both linear and embedding weights compact with standard ORT tools."""
    quantize_dynamic(
        str(reference_path), str(output_path), weight_type=QuantType.QInt8,
        per_channel=True, op_types_to_quantize=["MatMul", "Gemm", "Gather"],
        extra_options={"MatMulConstBOnly": True},
    )


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output-dir", type=Path, default=Path("app/src/main/assets"))
    parser.add_argument("--reference-dir", type=Path, default=Path("build/mobileclip2-reference"))
    parser.add_argument("--threads", type=int, default=4)
    args = parser.parse_args()
    if args.threads < 1:
        parser.error("--threads must be positive")
    torch.set_num_threads(args.threads)
    torch.set_grad_enabled(False)
    # Trace portable attention ops instead of PyTorch's fused CPU-only MHA kernel.
    torch.backends.mha.set_fastpath_enabled(False)
    torch.manual_seed(20260913)

    config = open_clip.get_pretrained_cfg("MobileCLIP2-S0", "dfndr2b")
    checkpoint = Path(download_pretrained(config))
    model, _, preprocess = open_clip.create_model_and_transforms(
        "MobileCLIP2-S0", pretrained="dfndr2b",
        image_mean=(0, 0, 0), image_std=(1, 1, 1),
    )
    model = reparameterize_model(model.eval()).eval()
    tokenizer = open_clip.get_tokenizer("MobileCLIP2-S0")
    args.output_dir.mkdir(parents=True, exist_ok=True)
    args.reference_dir.mkdir(parents=True, exist_ok=True)
    session_options = ort.SessionOptions()
    session_options.intra_op_num_threads = args.threads
    session_options.inter_op_num_threads = 1
    session_options.execution_mode = ort.ExecutionMode.ORT_SEQUENTIAL

    metadata = {
        "model": "MobileCLIP2-S0",
        "pretrained": "dfndr2b",
        "checkpoint_sha256": sha256(checkpoint),
        "checkpoint_repository": config.get("hf_hub"),
        "official_source": "https://github.com/apple-aiml-research/ml-mobileclip",
        "normalized_outputs": True,
        "embedding_dimension": 512,
        "preprocessing": {
            "resize_mode": config["resize_mode"],
            "interpolation": config["interpolation"],
            "center_crop": 256,
            "color": "RGB", "pixel_range": [0, 1],
            "mean": [0, 0, 0], "std": [1, 1, 1],
            "open_clip_transform": str(preprocess),
        },
        "versions": {
            name: importlib.metadata.version(name)
            for name in ("torch", "open_clip_torch", "timm", "onnx", "onnxruntime")
        },
        "onnx_opset": 17,
        "verification_threads": args.threads,
    }
    examples = {
        "image": (
            ImageTower(model).eval(),
            [torch.rand(1, 3, 256, 256), torch.zeros(1, 3, 256, 256),
             torch.ones(1, 3, 256, 256)],
        ),
        "text": (
            TextTower(model).eval(),
            [tokenizer([text]).to(torch.int32) for text in
             ("a photo of a cat", "a city at night", "", "a dog playing on grass")],
        ),
    }
    for tower_name, (tower, inputs) in examples.items():
        reference_path = (args.reference_dir if tower_name == "text" else args.output_dir) / f"mobileclip2_s0_{tower_name}.onnx"
        with torch.inference_mode():
            # Legacy exporter keeps a single asset and opset compatible with Android ORT.
            torch.onnx.export(
                tower, (inputs[0],), str(reference_path), dynamo=False, opset_version=17,
                input_names=[tower_name], output_names=["embedding"],
                do_constant_folding=True, external_data=False,
            )
        onnx.checker.check_model(str(reference_path), full_check=True)
        path = reference_path
        if tower_name == "text":
            path = args.output_dir / "mobileclip2_s0_text_int8.onnx"
            quantize_text(reference_path, path)
            onnx.checker.check_model(str(path), full_check=True)
        reference_session = ort.InferenceSession(
            str(reference_path), sess_options=session_options, providers=["CPUExecutionProvider"]
        )
        session = ort.InferenceSession(
            str(path), sess_options=session_options, providers=["CPUExecutionProvider"]
        )
        max_error = 0.0
        minimum_cosine = 1.0
        for value in inputs:
            with torch.inference_mode():
                expected = tower(value).numpy()
            actual = session.run(None, {tower_name: value.numpy()})[0]
            reference = reference_session.run(None, {tower_name: value.numpy()})[0]
            np.testing.assert_allclose(reference, expected, rtol=2e-3, atol=2e-5)
            np.testing.assert_allclose(np.linalg.norm(actual, axis=-1), 1, atol=1e-5)
            max_error = max(max_error, float(np.max(np.abs(actual - expected))))
            cosine = np.sum(actual * expected) / (np.linalg.norm(actual) * np.linalg.norm(expected))
            if cosine < (0.95 if tower_name == "text" else 0.9999):
                raise AssertionError(f"{tower_name} cosine {cosine} below conversion sanity threshold")
            minimum_cosine = min(minimum_cosine, float(cosine))
        metadata[tower_name] = {
            "file": path.name, "input_name": tower_name,
            "input_dtype": str(inputs[0].numpy().dtype),
            "input_shape": list(inputs[0].shape),
            "output_name": "embedding", "output_dtype": "float32",
            "output_shape": [1, 512],
            "quantization": "dynamic_wi8_afp32" if tower_name == "text" else "none",
            "bytes": path.stat().st_size, "sha256": sha256(path),
            "pytorch_parity": {"samples": len(inputs), "max_absolute_error": max_error,
                               "minimum_cosine": minimum_cosine},
        }
        if tower_name == "text":
            metadata[tower_name]["quantization_details"] = {
                "linear": "per-channel signed INT8 weights; dynamic activation quantization",
                "embedding": "standard ORT Gather quantization (per-tensor 8-bit weights)",
                "activation_interface": "float32 between operators; int32 token input",
            }
        print(json.dumps(metadata[tower_name], indent=2), flush=True)
    metadata_path = args.output_dir / "mobileclip2_onnx_metadata.json"
    metadata_path.write_text(json.dumps(metadata, indent=2) + "\n", encoding="utf-8")
    print(f"Verified assets and metadata: {metadata_path}")


if __name__ == "__main__":
    main()
