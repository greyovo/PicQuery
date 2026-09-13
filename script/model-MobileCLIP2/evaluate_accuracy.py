#!/usr/bin/env python3
"""Ground-truth zero-shot classification and class-query retrieval, with cached features.

Run from the repository root. Does not train models, change app assets, translate
queries, or tune prompts on the test set. FP32 text rows are offline controls only.
"""
from __future__ import annotations

import argparse
import hashlib
import importlib.metadata
import json
import platform
import time
from pathlib import Path

import numpy as np
import open_clip
import torch
from PIL import Image, ImageOps
from torchvision.datasets import CIFAR100, Imagenette

from benchmark_mobileclip2 import digest, runtime

SEED = 20260913
PROTOCOL = "classification-class-retrieval-v1"
VERSIONS = {name: importlib.metadata.version(name) for name in
            ("numpy", "Pillow", "torch", "torchvision", "open_clip_torch", "onnxruntime", "ai-edge-litert")}


def json_hash(value):
    return hashlib.sha256(json.dumps(value, sort_keys=True).encode()).hexdigest()


def save_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def datasets(root, names, download, per_class):
    result = {}
    for name in names:
        if name == "cifar100":
            ds = CIFAR100(root, train=False, download=download)
            labels = np.asarray(ds.targets)
            if len(ds) != 10000 or set(labels) != set(range(100)):
                raise ValueError("Incomplete CIFAR-100 test split")
            rng = np.random.default_rng(SEED)
            indices = sorted(np.concatenate([
                rng.choice(np.flatnonzero(labels == c), per_class, replace=False)
                for c in range(100)
            ]).tolist())
            classes = [s.replace("_", " ") for s in ds.classes]
            ids = [f"test/{i:05d}" for i in indices]
            hashes = [hashlib.sha256(ds.data[i].tobytes()).hexdigest() for i in indices]
            source = "https://www.cs.toronto.edu/~kriz/cifar.html"
            selection = f"test: {per_class} per fine class, NumPy default_rng seed {SEED}"
        else:
            ds = Imagenette(root, split="val", size="320px", download=download)
            # Explicit sorting also makes selection independent of directory order.
            ds._samples.sort(key=lambda sample: sample[0])
            indices = list(range(len(ds)))
            labels = np.asarray([y for _, y in ds._samples])
            if len(ds) != 3925 or set(labels) != set(range(10)):
                raise ValueError("Incomplete Imagenette validation split; finish extracting the archive")
            classes = [names[0] for names in ds.classes]
            ids = [str(Path(p).relative_to(root)) for p, _ in ds._samples]
            hashes = [digest(Path(p)) for p, _ in ds._samples]
            source = "https://github.com/fastai/imagenette"
            selection = "imagenette2-320: complete val split, original directory labels (not noisy CSV)"
        manifest = {
            "dataset": name, "source": source, "selection": selection,
            "classes": classes, "prompts": [f"a photo of a {c}" for c in classes],
            "samples": [{"id": key, "index": int(i), "label": int(labels[i]), "sha256": sha}
                        for key, i, sha in zip(ids, indices, hashes)],
            "sample_hash_format": "RGB uint8 array bytes" if name == "cifar100" else "original JPEG file",
        }
        result[name] = (ds, indices, labels[indices], manifest)
    return result


def preprocess(image, spec):
    image = ImageOps.exif_transpose(image).convert("RGB")
    size = spec["size"]
    mode = Image.Resampling.BILINEAR
    if spec.get("interpolation") == "bicubic":
        mode = Image.Resampling.BICUBIC
    if spec["resize"] == "stretch":
        image = image.resize((size, size), mode)
    else:
        width, height = image.size
        shape = (size, int(size * height / width)) if width < height else (int(size * width / height), size)
        image = image.resize(shape, mode)
        left, top = round((shape[0] - size) / 2), round((shape[1] - size) / 2)
        image = image.crop((left, top, left + size, top + size))
    value = np.asarray(image, dtype=np.float32) / 255
    value = (value - np.array(spec["mean"], dtype=np.float32)) / np.array(spec["std"], dtype=np.float32)
    return value.transpose(2, 0, 1)[None]


def normalized(value):
    value = np.asarray(value, dtype=np.float32)
    if value.shape != (1, 512) or not np.isfinite(value).all():
        raise ValueError(f"Invalid model output: shape={value.shape}")
    norm = np.linalg.norm(value, axis=-1, keepdims=True)
    if np.any(norm < 1e-8):
        raise ValueError("Zero embedding")
    return (value / norm)[0]


def features(path, values, count, signature, cache, threads, label):
    key = json_hash({"protocol": PROTOCOL, "versions": VERSIONS, "model": digest(path), "input": signature})
    target = cache / f"{key}.npz"
    if target.exists():
        with np.load(target, allow_pickle=False) as data:
            result = data["features"]
        if result.shape == (count, 512) and np.isfinite(result).all():
            print(f"{label}: cached {count}", flush=True)
            return result
    invoke = runtime(path, threads)
    result = []
    start = time.perf_counter()
    for i, value in enumerate(values):
        result.append(normalized(invoke(value)))
        if (i + 1) % 250 == 0:
            print(f"{label}: {i + 1}/{count}, {time.perf_counter() - start:.1f}s", flush=True)
    result = np.stack(result)
    if result.shape != (count, 512):
        raise ValueError("Incomplete input sequence")
    cache.mkdir(parents=True, exist_ok=True)
    np.savez_compressed(target, features=result)
    return result


def metrics(scores, labels, classes):
    ranks = np.argsort(-scores, axis=1, kind="stable")
    correct = ranks[:, 0] == labels
    # Each class prompt retrieves from the same full image pool. Multiple relevant
    # images per query: report precision and AP, not caption-pair Recall@K.
    retrieval = []
    for c, name in enumerate(classes):
        order = np.argsort(-scores[:, c], kind="stable")
        relevant = (labels[order] == c).astype(np.float64)
        precision = np.cumsum(relevant) / np.arange(1, len(labels) + 1)
        retrieval.append({
            "class": name, "support": int((labels == c).sum()),
            "top1": float(correct[labels == c].mean()),
            "p_at_10": float(relevant[:10].mean()),
            "ap": float(np.sum(precision * relevant) / relevant.sum()),
        })
    return {
        "n": len(labels), "top1_correct": int(correct.sum()),
        "top1": float(correct.mean()),
        "top5": float((ranks[:, :5] == labels[:, None]).any(axis=1).mean()),
        "macro_top1": float(np.mean([r["top1"] for r in retrieval])),
        "class_query_p_at_10": float(np.mean([r["p_at_10"] for r in retrieval])),
        "class_query_map": float(np.mean([r["ap"] for r in retrieval])),
        "per_class": retrieval, "predicted_class": ranks[:, 0].tolist(),
    }


def comparisons(rows, labels):
    result = []
    keys = list(rows)
    for i, a in enumerate(keys):
        for b in keys[i + 1:]:
            first = np.asarray(rows[a]["predicted_class"]) == labels
            second = np.asarray(rows[b]["predicted_class"]) == labels
            delta = second.astype(np.int8) - first.astype(np.int8)
            rng = np.random.default_rng(SEED)
            samples = np.array([rng.choice(delta, len(delta), replace=True).mean() for _ in range(2000)])
            result.append({
                "a": a, "b": b, "b_minus_a_top1_pp": float(delta.mean() * 100),
                "paired_bootstrap_95ci_pp": (np.quantile(samples, [.025, .975]) * 100).tolist(),
                "a_only_correct": int(np.sum(first & ~second)),
                "b_only_correct": int(np.sum(second & ~first)),
                "both_correct": int(np.sum(first & second)),
                "both_wrong": int(np.sum(~first & ~second)),
            })
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--models", type=Path, default=Path("script/model-MobileCLIP2/accuracy_models.json"))
    parser.add_argument("--data-root", type=Path, default=Path("build/mobileclip-accuracy-data"))
    parser.add_argument("--cache", type=Path, default=Path("build/mobileclip-accuracy-cache"))
    parser.add_argument("--output", type=Path, default=Path("script/model-MobileCLIP2/results/accuracy-results.json"))
    parser.add_argument("--dataset", choices=["cifar100", "imagenette"], action="append")
    parser.add_argument("--model", action="append", help="Run selected model IDs only")
    parser.add_argument("--download", action="store_true")
    parser.add_argument("--per-class", type=int, default=20)
    parser.add_argument("--threads", type=int, default=4)
    args = parser.parse_args()
    if not 1 <= args.per_class <= 100 or args.threads < 1:
        parser.error("per-class must be 1..100; threads must be positive")
    torch.set_num_threads(args.threads)
    specs = json.loads(args.models.read_text(encoding="utf-8"))
    if args.model:
        unknown = set(args.model) - {s["id"] for s in specs}
        if unknown:
            parser.error(f"Unknown models: {unknown}")
        specs = [s for s in specs if s["id"] in args.model]
    for spec in specs:
        for tower in ("image", "text"):
            if not Path(spec[tower]).is_file():
                parser.error(f"Missing {spec[tower]}; prepare models first")
    bundles = datasets(args.data_root, args.dataset or ["cifar100", "imagenette"], args.download, args.per_class)
    manifests = {name: bundle[3] for name, bundle in bundles.items()}
    save_json(args.output.with_name(args.output.stem + "-manifest.json"), manifests)
    result = {
        "protocol": PROTOCOL, "seed": SEED, "host": platform.platform(),
        "threads": args.threads, "versions": VERSIONS, "manifest_sha256": json_hash(manifests),
        "prompt_template": "a photo of a {class}", "models": {}, "datasets": {},
        "limits": ["English class labels; no translation, caption matching, OCR, or personal-gallery annotations.",
                   "CIFAR images are 32x32; Imagenette is a 10-class ImageNet subset, not ImageNet-1k.",
                   "No fitting or prompt selection on evaluation data; FP32 text is an offline control.",
                   "Class-query P@10/mAP is not image-caption recall; bootstrap CI is conditional on this dataset."],
    }
    tokenizer = open_clip.get_tokenizer("MobileCLIP2-S0")
    for spec in specs:
        model_id = spec["id"]
        result["models"][model_id] = {
            **spec, "image_sha256": digest(Path(spec["image"])),
            "text_sha256": digest(Path(spec["text"])),
            "total_bytes": sum(Path(spec[k]).stat().st_size for k in ("image", "text")),
        }
        for name, (ds, indices, labels, manifest) in bundles.items():
            signature = {"manifest": json_hash(manifest), "preprocess": spec["preprocess"]}
            image_features = features(
                Path(spec["image"]), (preprocess(ds[i][0], spec["preprocess"]) for i in indices),
                len(indices), signature, args.cache, args.threads, f"{model_id}/{name}/image")
            prompts = manifest["prompts"]
            tokens = tokenizer(prompts).numpy()
            text_features = features(
                Path(spec["text"]), (row[None] for row in tokens), len(prompts),
                {"token_ids": tokens.tolist()}, args.cache, args.threads, f"{model_id}/{name}/text")
            scores = image_features @ text_features.T
            dataset_result = result["datasets"].setdefault(name, {"metrics": {}})
            dataset_result["metrics"][model_id] = metrics(scores, labels, manifest["classes"])
            print(model_id, name, {k: v for k, v in dataset_result["metrics"][model_id].items()
                                  if k not in ("per_class", "predicted_class")}, flush=True)
            save_json(args.output, result)
    for name, (_, _, labels, _) in bundles.items():
        result["datasets"][name]["comparisons"] = comparisons(result["datasets"][name]["metrics"], labels)
    save_json(args.output, result)


if __name__ == "__main__":
    main()
