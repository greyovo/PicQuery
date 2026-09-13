"""Deterministic synthetic fixtures for offline MobileCLIP2 regression tests.

These helpers intentionally do not preserve real benchmark payloads. They keep
the same schema, hash, path and statistics contracts while clearly marking every
generated file as synthetic and non-measured.
"""
from __future__ import annotations

from collections import Counter
import hashlib
import json
import math
from pathlib import Path

import numpy as np
import open_clip


BASE = Path(__file__).resolve().parents[1]
REPO = BASE.parents[1]

SEED = 20260913
RUNTIME = "1.29.0"
FP32 = "v2_s0_fp32_image_ort"
INT8 = "v2_s0_int8_image_ort"
LEGACY = "legacy_clip_int8"
MODELS = (FP32, INT8, LEGACY)
DEVICE_MODELS = (FP32, INT8)

PREPROCESS = {
    "size": 256,
    "resize": "center_crop",
    "interpolation": "bilinear",
    "mean": [0.0, 0.0, 0.0],
    "std": [1.0, 1.0, 1.0],
}
LEGACY_PREPROCESS = {
    "size": 224,
    "resize": "stretch",
    "interpolation": "bilinear",
    "mean": [0.48145466, 0.4578275, 0.40821073],
    "std": [0.26862954, 0.26130258, 0.27577711],
}
DEVICE_IDENTITY = {
    "device": "SYNTHETIC OFFLINE TEST - NOT DEVICE MEASUREMENTS",
    "manufacturer": "OfflineFixture",
    "sdk": 37,
    "abis": ["arm64-v8a"],
    "fingerprint": "synthetic/pixel8a/offline:17/UP1A.000000.000/fixture:userdebug/test-keys",
}
IMAGE_CONTRACT = {
    "input_shape": [1, 3, 256, 256],
    "input_type": "FLOAT",
    "output_shape": [1, 512],
    "output_type": "FLOAT32",
}
TEXT_CONTRACT = {
    "input_shape": [1, 77],
    "input_type": "INT32",
    "output_shape": [1, 512],
    "output_type": "FLOAT32",
}

DATASET_SPECS = {
    "cifar100": {"count": 2000, "classes": 100, "supports": [20] * 100},
    "imagenette": {"count": 3925, "classes": 10, "supports": [393 if i < 5 else 392 for i in range(10)]},
}
TARGET_CORRECT = {
    "cifar100": {FP32: 1600, INT8: 1565, LEGACY: 1210},
    "imagenette": {FP32: 3810, INT8: 3802, LEGACY: 3240},
}


def read(path: Path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def write(path: Path, value):
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def stable_sha(text: str) -> str:
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


def json_hash(value) -> str:
    payload = json.dumps(value, sort_keys=True).encode("utf-8")
    return hashlib.sha256(payload).hexdigest()


def stats(values):
    ordered = sorted(values)
    assert ordered and all(math.isfinite(v) and v > 0 for v in ordered)
    return {
        "count": len(values),
        "mean_ms": sum(values) / len(values),
        "min_ms": ordered[0],
        "max_ms": ordered[-1],
        **{f"p{p}_ms": ordered[math.ceil(len(values) * p / 100) - 1] for p in (50, 95, 99)},
    }


def dataset_classes(name: str):
    if name == "cifar100":
        return [f"cifar_class_{index:03d}" for index in range(DATASET_SPECS[name]["classes"])]
    return [f"imagenette_class_{index:02d}" for index in range(DATASET_SPECS[name]["classes"])]


def evaluation_manifest():
    result = {}
    for name, spec in DATASET_SPECS.items():
        samples = []
        classes = dataset_classes(name)
        index = 0
        for label, support in enumerate(spec["supports"]):
            for offset in range(support):
                if name == "cifar100":
                    sample_id = f"test/{classes[label]}/img_{offset:04d}.png"
                else:
                    sample_id = f"imagenette/{classes[label]}/val/img_{offset:04d}.jpg"
                samples.append({
                    "id": sample_id,
                    "index": index,
                    "label": label,
                    "sha256": stable_sha(f"evaluation:{name}:{sample_id}"),
                })
                index += 1
        assert index == spec["count"]
        result[name] = {
            "dataset": name,
            "source": "synthetic offline fixture - not benchmark evidence",
            "selection": f"Deterministic synthetic fixture, seed {SEED}",
            "classes": classes,
            "prompts": [f"a photo of a {label}" for label in classes],
            "samples": samples,
            "sample_hash_format": "synthetic sha256 fixture ids",
        }
    return result


def predicted_classes(labels: np.ndarray, class_count: int, correct: int, seed: int, shift: int):
    predicted = labels.copy()
    wrong = len(labels) - correct
    rng = np.random.default_rng(seed)
    wrong_indices = rng.choice(len(labels), wrong, replace=False)
    offsets = 1 + (wrong_indices + shift) % (class_count - 1)
    predicted[wrong_indices] = (labels[wrong_indices] + offsets) % class_count
    return predicted


def retrieval_rows(labels: np.ndarray, predicted: np.ndarray, classes, bias: int):
    correct = predicted == labels
    rows = []
    for index, name in enumerate(classes):
        support = int((labels == index).sum())
        top1 = float(correct[labels == index].mean())
        bucket = ((index + bias) % 5) + 5
        p_at_10 = bucket / 10
        ap = min(0.99, max(0.05, top1 * 0.82 + 0.1 + ((index + bias) % 4) * 0.015))
        rows.append({
            "class": name,
            "support": support,
            "top1": top1,
            "p_at_10": p_at_10,
            "ap": ap,
        })
    return rows


def metric_row(labels: np.ndarray, predicted: np.ndarray, classes, bias: int):
    correct = predicted == labels
    count = len(labels)
    top1_correct = int(correct.sum())
    top5_correct = min(count, top1_correct + max(1, count // 40) + bias)
    per_class = retrieval_rows(labels, predicted, classes, bias)
    return {
        "n": count,
        "top1_correct": top1_correct,
        "top1": float(correct.mean()),
        "top5": top5_correct / count,
        "macro_top1": float(np.mean([row["top1"] for row in per_class])),
        "class_query_p_at_10": float(np.mean([row["p_at_10"] for row in per_class])),
        "class_query_map": float(np.mean([row["ap"] for row in per_class])),
        "per_class": per_class,
        "predicted_class": predicted.tolist(),
    }


def pairwise_comparisons(rows, labels: np.ndarray):
    result = []
    keys = list(rows)
    for index, first in enumerate(keys):
        for second in keys[index + 1:]:
            a = np.asarray(rows[first]["predicted_class"]) == labels
            b = np.asarray(rows[second]["predicted_class"]) == labels
            delta = b.astype(np.int8) - a.astype(np.int8)
            rng = np.random.default_rng(SEED)
            samples = np.asarray([rng.choice(delta, len(delta), replace=True).mean() for _ in range(2000)])
            result.append({
                "a": first,
                "b": second,
                "b_minus_a_top1_pp": float(delta.mean() * 100),
                "paired_bootstrap_95ci_pp": (np.quantile(samples, [0.025, 0.975]) * 100).tolist(),
                "a_only_correct": int(np.sum(a & ~b)),
                "b_only_correct": int(np.sum(b & ~a)),
                "both_correct": int(np.sum(a & b)),
                "both_wrong": int(np.sum(~a & ~b)),
            })
    return result


def make_model_files(root: Path):
    root.mkdir(parents=True, exist_ok=True)
    files = {
        "shared_text": root / "mobileclip2_shared_text_int8.ort",
        FP32: root / "mobileclip2_fp32_image.ort",
        INT8: root / "mobileclip2_mixed_int8_image.ort",
        f"{LEGACY}:image": root / "legacy_clip_image_int8.ort",
        f"{LEGACY}:text": root / "legacy_clip_text_int8.ort",
        "source": root / "mobileclip2_fp32_image.onnx",
        "quantized": root / "mobileclip2_mixed_int8_image.onnx",
    }
    for key, path in files.items():
        path.write_bytes(f"SYNTHETIC FIXTURE - {key} - NOT A REAL MODEL".encode("utf-8"))
    shared = files["shared_text"]
    legacy_image = files[f"{LEGACY}:image"]
    legacy_text = files[f"{LEGACY}:text"]
    specs = {
        FP32: {"id": FP32, "label": "Synthetic MobileCLIP2 FP32 image", "preprocess": json.loads(json.dumps(PREPROCESS)),
               "image": str(files[FP32]), "text": str(shared)},
        INT8: {"id": INT8, "label": "Synthetic MobileCLIP2 mixed INT8 image", "preprocess": json.loads(json.dumps(PREPROCESS)),
               "image": str(files[INT8]), "text": str(shared)},
        LEGACY: {"id": LEGACY, "label": "Synthetic legacy CLIP", "preprocess": json.loads(json.dumps(LEGACY_PREPROCESS)),
                 "image": str(legacy_image), "text": str(legacy_text)},
    }
    for spec in specs.values():
        for tower in ("image", "text"):
            path = Path(spec[tower])
            spec[f"{tower}_sha256"] = digest(path)
        spec["total_bytes"] = sum(Path(spec[tower]).stat().st_size for tower in ("image", "text"))
    return specs, files


def make_accuracy_payload(root: Path):
    manifest = evaluation_manifest()
    models, files = make_model_files(root)
    data = {
        "protocol": "classification-class-retrieval-v1",
        "seed": SEED,
        "host": "SYNTHETIC OFFLINE TEST - NOT MEASUREMENTS",
        "threads": 4,
        "versions": {"onnxruntime": RUNTIME},
        "manifest_sha256": json_hash(manifest),
        "prompt_template": "a photo of a {class}",
        "limits": ["Synthetic offline regression fixture; not benchmark evidence."],
        "models": models,
        "datasets": {},
    }
    for name, bundle in manifest.items():
        labels = np.asarray([sample["label"] for sample in bundle["samples"]])
        class_count = len(bundle["classes"])
        rows = {}
        for bias, model in enumerate(MODELS, start=1):
            predicted = predicted_classes(labels, class_count, TARGET_CORRECT[name][model], SEED + bias * 17 + class_count, bias)
            rows[model] = metric_row(labels, predicted, bundle["classes"], bias)
        data["datasets"][name] = {"metrics": rows, "comparisons": pairwise_comparisons(rows, labels)}
    host_path = root / "host-accuracy.json"
    evaluation_path = root / "evaluation-manifest.json"
    write(host_path, data)
    write(evaluation_path, manifest)
    return data, manifest, models, files, host_path, evaluation_path


def calibration_samples():
    samples = []
    for label in range(100):
        for offset in range(2):
            sample_id = f"train/cifar_class_{label:03d}/cal_{offset:02d}.png"
            samples.append({
                "dataset": "cifar100",
                "split": "train",
                "id": sample_id,
                "label": label,
                "sha256": stable_sha(f"calibration:{sample_id}:native"),
                "rgb_sha256": stable_sha(f"calibration:{sample_id}:rgb"),
                "preprocessed_sha256": stable_sha(f"calibration:{sample_id}:preprocessed"),
            })
    for label in range(10):
        for offset in range(20):
            sample_id = f"imagenette/imagenette_class_{label:02d}/train/cal_{offset:02d}.jpg"
            samples.append({
                "dataset": "imagenette",
                "split": "train",
                "id": sample_id,
                "label": label,
                "sha256": stable_sha(f"calibration:{sample_id}:native"),
                "rgb_sha256": stable_sha(f"calibration:{sample_id}:rgb"),
                "preprocessed_sha256": stable_sha(f"calibration:{sample_id}:preprocessed"),
            })
    assert Counter(sample["dataset"] for sample in samples) == {"cifar100": 200, "imagenette": 200}
    return samples


def make_quantization_payload(root: Path, accuracy, manifest, files):
    calibration_manifest = {
        "seed": SEED,
        "preprocess": {
            "spec": PREPROCESS,
            "source_file": str(BASE / "evaluate_accuracy.py"),
            "source_file_sha256": digest(BASE / "evaluate_accuracy.py"),
        },
        "samples": calibration_samples(),
    }
    calibration_path = root / "calibration_manifest.json"
    write(calibration_path, calibration_manifest)
    evaluation_manifest_path = root / "evaluation-manifest.json"
    metadata = {
        "status": "passed",
        "onnxruntime_version": RUNTIME,
        "format_parity": {"status": "passed", "scope": "Synthetic format parity fixture"},
        "source_fp32_ort": {
            "file": accuracy["models"][FP32]["image"],
            "sha256": accuracy["models"][FP32]["image_sha256"],
            "bytes": Path(accuracy["models"][FP32]["image"]).stat().st_size,
        },
        "source": {"file": str(files["source"]), "sha256": digest(files["source"]), "bytes": files["source"].stat().st_size},
        "shared_text": {
            "ort": {
                "file": accuracy["models"][FP32]["text"],
                "sha256": accuracy["models"][FP32]["text_sha256"],
                "bytes": Path(accuracy["models"][FP32]["text"]).stat().st_size,
            }
        },
        "image": {
            "ort": {
                "file": accuracy["models"][INT8]["image"],
                "sha256": accuracy["models"][INT8]["image_sha256"],
                "bytes": Path(accuracy["models"][INT8]["image"]).stat().st_size,
            },
            "onnx": {"file": str(files["quantized"]), "sha256": digest(files["quantized"]), "bytes": files["quantized"].stat().st_size},
        },
        "quantization": {
            "format": "QDQ",
            "activation_type": "QInt8",
            "weight_type": "QInt8",
            "per_channel": True,
            "calibration_method": "MinMax",
        },
        "coverage": {
            "quantized": {
                "initializer_elements": {"INT8": 8192},
                "heavy_ops": [
                    {"name": "/visual/block0/Conv", "op_type": "Conv", "both_inputs_quantized": True, "int8_dequantized_inputs": 2},
                    {"name": "/visual/block1/Conv", "op_type": "Conv", "both_inputs_quantized": True, "int8_dequantized_inputs": 2},
                    {"name": "/visual/sensitive/Conv", "op_type": "Conv", "both_inputs_quantized": False, "int8_dequantized_inputs": 0},
                    {"name": "/visual/downsample/Conv", "op_type": "Conv", "both_inputs_quantized": False, "int8_dequantized_inputs": 0},
                    {"name": "/visual/proj/MatMul", "op_type": "MatMul", "both_inputs_quantized": True, "int8_dequantized_inputs": 2},
                    {"name": "/visual/head/Gemm", "op_type": "Gemm", "both_inputs_quantized": True, "int8_dequantized_inputs": 2},
                ],
            },
            "quantized_heavy_ops": {"Conv": 2, "MatMul": 1, "Gemm": 1},
        },
        "calibration": {
            "seed": SEED,
            "samples_total": 400,
            "manifest_file": str(calibration_path),
            "manifest_sha256": digest(calibration_path),
            "datasets": [
                {"dataset": "cifar100", "split": "train", "samples": 200, "per_class": 2},
                {"dataset": "imagenette", "split": "train", "samples": 200, "per_class": 20},
            ],
            "leakage_check": {
                "status": "passed",
                "native_sha256_overlap": 0,
                "rgb_sha256_overlap": 0,
                "evaluation_counts": {"cifar100": 2000, "imagenette": 3925},
                "evaluation_manifest_file": str(evaluation_manifest_path),
                "evaluation_manifest_sha256": digest(evaluation_manifest_path),
            },
        },
        "quantization_cosines": {"scope": "Synthetic placeholder for validator coverage"},
    }
    path = root / "quantization_metadata.json"
    write(path, metadata)
    return path


def token_hash(prompts):
    tokenizer = open_clip.get_tokenizer("MobileCLIP2-S0")
    tokens = tokenizer(prompts).numpy().astype("<i4")
    return hashlib.sha256(tokens.tobytes()).hexdigest()


def make_parity_payload(root: Path, models):
    host_reference = root / "parity-host-reference.json"
    device_reference = root / "parity-device-reference.json"
    write(host_reference, {"fixture": "synthetic host parity input"})
    write(device_reference, {"fixture": "synthetic device parity input"})
    data = {
        "status": "failed",
        "scope": "Synthetic first-input parity fixture; not full accuracy evidence.",
        "input_report_sha256": {
            host_reference.name: digest(host_reference),
            device_reference.name: digest(device_reference),
        },
        "results": [],
    }
    for model in DEVICE_MODELS:
        image_cosine = 1.0 if model == FP32 else 0.9985
        data["results"].append({
            "model_id": model,
            "round_id": "r1",
            "towers": {
                "image": {
                    "status": "passed" if model == FP32 else "failed",
                    "cosine": image_cosine,
                    "cosine_minimum": 0.9999,
                    "model_sha256": models[model]["image_sha256"],
                },
                "text": {
                    "status": "passed",
                    "cosine": 1.0,
                    "cosine_minimum": 0.9999,
                    "model_sha256": models[model]["text_sha256"],
                },
            },
        })
    path = root / "parity.json"
    write(path, data)
    return path


def make_artifacts(root: Path):
    accuracy, manifest, models, files, host_path, evaluation_path = make_accuracy_payload(root)
    quantization_path = make_quantization_payload(root, accuracy, manifest, files)
    parity_path = make_parity_payload(root, models)
    return {
        "host": host_path,
        "evaluation": evaluation_path,
        "quantization": quantization_path,
        "parity": parity_path,
        "accuracy": accuracy,
        "evaluation_manifest": manifest,
        "models": models,
    }
