#!/usr/bin/env python3
"""Compare exported ONNX and existing LiteRT on identical images and CLIP tokens.

Default text precision matches the previous Android app: dynamic INT8 weights.
Timings are host CPU inference only, not measurements of Android performance.
"""

from __future__ import annotations

import argparse
import hashlib
import importlib.metadata
import json
import platform
import time
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path

import numpy as np
import onnx
import onnxruntime as ort
import open_clip
from ai_edge_litert.interpreter import Interpreter
from PIL import Image, ImageOps


DEFAULT_TEXTS = [
    "a photo of a dog", "a photo of a cat", "a photo of a red car",
    "a city at night", "a mountain landscape", "a sandy beach",
    "a plate of food", "a technical diagram", "a flower", "a person",
    "an astronaut in a space suit", "the leaning tower of Pisa", "handmade pottery",
]


def digest(path: Path) -> str:
    with path.open("rb") as source:
        return hashlib.file_digest(source, "sha256").hexdigest()


def preprocess(path: Path) -> np.ndarray:
    with Image.open(path) as source:
        image = ImageOps.exif_transpose(source).convert("RGB")
        width, height = image.size
        # Matches OpenCLIP's dfndr2b PIL Resize(BILINEAR) + CenterCrop.
        if width < height:
            resized = (256, int(256 * height / width))
        else:
            resized = (int(256 * width / height), 256)
        image = image.resize(resized, Image.Resampling.BILINEAR)
        left = round((resized[0] - 256) / 2)
        top = round((resized[1] - 256) / 2)
        image = image.crop((left, top, left + 256, top + 256))
        return np.asarray(image, dtype=np.float32).transpose(2, 0, 1)[None] / 255


def runtime(path: Path, threads: int):
    if path.suffix in (".onnx", ".ort"):
        options = ort.SessionOptions()
        options.intra_op_num_threads = threads
        options.inter_op_num_threads = 1
        options.execution_mode = ort.ExecutionMode.ORT_SEQUENTIAL
        session = ort.InferenceSession(str(path), options, providers=["CPUExecutionProvider"])
        input_info = session.get_inputs()[0]
        dtype = np.int32 if input_info.type == "tensor(int32)" else np.float32
        if input_info.type == "tensor(int64)":
            dtype = np.int64

        def invoke(value):
            return session.run(None, {input_info.name: value.astype(dtype, copy=False)})[0]

        return invoke
    interpreter = Interpreter(model_path=str(path), num_threads=threads)
    interpreter.allocate_tensors()
    input_info = interpreter.get_input_details()[0]
    output_info = interpreter.get_output_details()[0]

    def invoke(value):
        interpreter.set_tensor(input_info["index"], value.astype(input_info["dtype"], copy=False))
        interpreter.invoke()
        return interpreter.get_tensor(output_info["index"])

    return invoke


def measure(path: Path, samples: list[np.ndarray], args) -> tuple[np.ndarray, dict]:
    start = time.perf_counter()
    invoke = runtime(path, args.threads)
    load_ms = (time.perf_counter() - start) * 1000
    for _ in range(args.warmup):
        invoke(samples[0])
    embeddings = []
    elapsed = []
    for sample in samples:
        for _ in range(args.repeats):
            start = time.perf_counter()
            result = invoke(sample)
            elapsed.append((time.perf_counter() - start) * 1000)
        if result.shape != (1, 512) or not np.isfinite(result).all():
            raise AssertionError(f"Invalid embedding from {path}: {result.shape}")
        np.testing.assert_allclose(np.linalg.norm(result, axis=-1), 1, atol=1e-4)
        embeddings.append(result[0])
    metrics = {
        "file": path.name, "bytes": path.stat().st_size, "sha256": digest(path),
        "load_ms": load_ms, "mean_ms": float(np.mean(elapsed)),
        "median_ms": float(np.median(elapsed)), "p95_ms": float(np.percentile(elapsed, 95)),
        "invocations": len(elapsed),
    }
    if path.suffix == ".onnx":
        model = onnx.load(str(path))
        metrics["operators"] = dict(Counter(node.op_type for node in model.graph.node))
        metrics["int8_initializer_elements"] = sum(
            int(np.prod(value.dims)) for value in model.graph.initializer
            if value.data_type == onnx.TensorProto.INT8
        )
        metrics["uint8_initializer_elements"] = sum(
            int(np.prod(value.dims)) for value in model.graph.initializer
            if value.data_type == onnx.TensorProto.UINT8
        )
    return np.stack(embeddings), metrics


def parity(first: np.ndarray, second: np.ndarray) -> dict:
    cosine = (first * second).sum(-1) / (np.linalg.norm(first, axis=-1) * np.linalg.norm(second, axis=-1))
    return {"minimum_cosine": float(cosine.min()), "mean_cosine": float(cosine.mean()),
            "max_absolute_error": float(np.abs(first - second).max())}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--asset-dir", type=Path, default=Path("app/src/main/assets"))
    parser.add_argument("--reference-dir", type=Path, default=Path("build/mobileclip2-reference"))
    parser.add_argument("--output", type=Path, default=Path("build/mobileclip2-comparison.json"))
    parser.add_argument("--image", type=Path, action="append", default=[])
    parser.add_argument("--text", action="append")
    parser.add_argument("--threads", type=int, default=4)
    parser.add_argument("--warmup", type=int, default=3)
    parser.add_argument("--repeats", type=int, default=5)
    args = parser.parse_args()
    if args.threads < 1 or args.warmup < 0 or args.repeats < 1:
        parser.error("threads/repeats must be positive and warmup non-negative")
    texts = args.text or DEFAULT_TEXTS
    tokenizer = open_clip.get_tokenizer("MobileCLIP2-S0")
    tokens = [tokenizer([text]).numpy().astype(np.int32) for text in texts]
    image_inputs = [preprocess(path) for path in args.image]
    image_names = [path.name for path in args.image]
    if not image_inputs:
        rng = np.random.default_rng(20260913)
        image_inputs = [rng.random((1, 3, 256, 256), dtype=np.float32),
                        np.zeros((1, 3, 256, 256), np.float32),
                        np.ones((1, 3, 256, 256), np.float32)]
        image_names = ["synthetic_noise", "black", "white"]
    report = {
        "created_utc": datetime.now(timezone.utc).isoformat(),
        "host": platform.platform(), "processor": platform.processor(),
        "precision": "image FP32 / text dynamic INT8 weights, same as previous app",
        "threads": args.threads, "warmup": args.warmup, "repeats": args.repeats,
        "timing_scope": "Host CPU, inference only, per sample; excludes preprocessing and tokenization. Not Android latency.",
        "evaluation_scope": "Small conversion/ranking smoke test; not an accuracy benchmark.",
        "images": image_names, "texts": texts,
        "image_sha256": {path.name: digest(path) for path in args.image},
        "versions": {name: importlib.metadata.version(name) for name in
                     ("onnxruntime", "ai-edge-litert", "open_clip_torch", "numpy")},
        "models": {}, "parity": {}, "rankings": {},
    }
    embeddings = {}
    configurations = {
        "onnx_image": (args.asset_dir / "mobileclip2_s0_image.onnx", image_inputs),
        "tflite_image": (args.asset_dir / "image_model.tflite", image_inputs),
        "onnx_text_int8": (args.asset_dir / "mobileclip2_s0_text_int8.onnx", tokens),
        "tflite_text_int8": (args.asset_dir / "text_model_dynamic_wi8.tflite", tokens),
        "onnx_text_reference": (args.reference_dir / "mobileclip2_s0_text.onnx", tokens),
        "tflite_text_reference": (args.asset_dir / "text_model.tflite", tokens),
    }
    for name, (path, samples) in configurations.items():
        embeddings[name], report["models"][name] = measure(path, samples, args)
        print(f"{name}: {report['models'][name]['median_ms']:.2f} ms median", flush=True)
    for label, first, second in [
        ("image_onnx_vs_tflite", "onnx_image", "tflite_image"),
        ("text_int8_onnx_vs_tflite", "onnx_text_int8", "tflite_text_int8"),
        ("text_onnx_int8_vs_fp32", "onnx_text_int8", "onnx_text_reference"),
        ("text_tflite_int8_vs_fp32", "tflite_text_int8", "tflite_text_reference"),
        ("text_fp32_onnx_vs_tflite", "onnx_text_reference", "tflite_text_reference"),
    ]:
        report["parity"][label] = parity(embeddings[first], embeddings[second])
    ranks = {}
    retrieval_ranks = {}
    for backend in ("onnx", "tflite"):
        scores = embeddings[f"{backend}_image"] @ embeddings[f"{backend}_text_int8"].T
        ranks[backend] = np.argsort(-scores, axis=-1)
        retrieval_ranks[backend] = np.argsort(-scores.T, axis=-1)
        report["rankings"][backend] = [
            {"image": name, "top_3": [{"text": texts[index], "cosine": float(row[index])}
                                      for index in order[:3]]}
            for name, row, order in zip(image_names, scores, ranks[backend])
        ]
        report["rankings"][backend + "_text_to_image"] = [
            {"text": text, "top_3": [{"image": image_names[index], "cosine": float(row[index])}
                                     for index in order[:3]]}
            for text, row, order in zip(texts, scores.T, retrieval_ranks[backend])
        ]
    report["top1_agreement"] = float(np.mean(ranks["onnx"][:, 0] == ranks["tflite"][:, 0]))
    report["top3_overlap"] = float(np.mean([
        len(set(a[:3]) & set(b[:3])) / min(3, len(texts)) for a, b in zip(ranks["onnx"], ranks["tflite"])
    ]))
    report["retrieval_top1_agreement"] = float(np.mean(
        retrieval_ranks["onnx"][:, 0] == retrieval_ranks["tflite"][:, 0]
    ))
    report["retrieval_top3_overlap"] = float(np.mean([
        len(set(a[:3]) & set(b[:3])) / min(3, len(image_names))
        for a, b in zip(retrieval_ranks["onnx"], retrieval_ranks["tflite"])
    ]))
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    lines = ["# MobileCLIP2-S0 host comparison", "", report["precision"], "",
             report["timing_scope"], report["evaluation_scope"], "",
             f"CPU threads: {args.threads}; warm-up: {args.warmup}; repeats per sample: {args.repeats}.", "",
             "| Model | MiB | Median ms | P95 ms |", "|---|---:|---:|---:|"]
    for name, metric in report["models"].items():
        lines.append(f"| {name} | {metric['bytes'] / 1024**2:.2f} | {metric['median_ms']:.2f} | {metric['p95_ms']:.2f} |")
    lines.extend(["", "| Embedding comparison | Minimum cosine | Maximum absolute error |",
                  "|---|---:|---:|"])
    for name, metric in report["parity"].items():
        lines.append(f"| {name} | {metric['minimum_cosine']:.8f} | {metric['max_absolute_error']:.8f} |")
    lines.extend(["", f"Image-to-text top-1 agreement: {report['top1_agreement']:.0%}; top-3 overlap: {report['top3_overlap']:.0%}.",
                  f"Text-to-image top-1 agreement: {report['retrieval_top1_agreement']:.0%}; top-3 overlap: {report['retrieval_top3_overlap']:.0%}.",
                  "", "Input images: " + ", ".join(image_names) + ".", ""])
    args.output.with_suffix(".md").write_text("\n".join(lines), encoding="utf-8")
    print(f"Results: {args.output}")


if __name__ == "__main__":
    main()
