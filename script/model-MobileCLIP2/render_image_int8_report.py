#!/usr/bin/env python3
"""Validate and report image INT8 accuracy and optional Android measurements.

Run from the repository root. This reads completed artifacts; it does not execute
models, select quantization parameters, or modify earlier evaluation reports.
"""
from __future__ import annotations

import argparse
from collections import Counter
import hashlib
import json
import math
import os
from pathlib import Path
import re

import numpy as np

from summarize_device_benchmark import stats, verify_stats


FP32 = "v2_s0_fp32_image_ort"
INT8 = "v2_s0_int8_image_ort"
LEGACY = "legacy_clip_int8"
MODELS = (FP32, INT8, LEGACY)
LABELS = {FP32: "MobileCLIP2 S0 · FP32 图像", INT8: "MobileCLIP2 S0 · 混合 INT8 图像",
          LEGACY: "历史 CLIP · 双塔 INT8"}
SEED = 20260913
RUNTIME = "1.29.0"


def require(condition, message):
    if not condition:
        raise ValueError(message)


def read(path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def digest(path):
    value = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            value.update(chunk)
    return value.hexdigest()


def json_hash(value):
    return hashlib.sha256(json.dumps(value, sort_keys=True).encode()).hexdigest()


def valid_sha(value):
    return isinstance(value, str) and len(value) == 64 and all(c in "0123456789abcdef" for c in value)


def close(a, b):
    return math.isfinite(a) and math.isfinite(b) and math.isclose(a, b, rel_tol=1e-9, abs_tol=1e-7)


def referenced_file(value, owner):
    path = Path(value)
    candidates = (path,) if path.is_absolute() else (owner.parent / path, path)
    for candidate in candidates:
        if candidate.is_file():
            return candidate
    raise ValueError(f"Missing referenced file: {value}")


def sample_identity(manifest):
    return {name: {"classes": data["classes"], "prompts": data["prompts"],
                   "samples": sorted((s["id"].replace("\\", "/"), s["label"], s["sha256"])
                                     for s in data["samples"])}
            for name, data in manifest.items()}


def validate_accuracy(data, manifest):
    require(data["protocol"] == "classification-class-retrieval-v1", "Unexpected accuracy protocol")
    require(data["seed"] == SEED and data["threads"] == 4, "Accuracy seed/thread mismatch")
    require(data["versions"]["onnxruntime"] == RUNTIME, "Accuracy must use ONNX Runtime 1.29.0")
    require(data["manifest_sha256"] == json_hash(manifest), "Accuracy manifest hash mismatch")
    require(set(data["models"]) == set(MODELS), "Expected exactly the three image-quantization models")
    require(set(data["datasets"]) == set(manifest) == {"cifar100", "imagenette"}, "Both evaluation splits are required")
    require(data["prompt_template"] == "a photo of a {class}", "Unexpected prompt template")
    sizes, checked = {}, {}
    for model in MODELS:
        spec = data["models"][model]
        sizes[model] = {}
        for tower in ("image", "text"):
            path = Path(spec[tower])
            require(path.suffix == ".ort" and path.is_file(), f"Missing ORT model: {path}")
            key = path.resolve()
            if key not in checked:
                checked[key] = (digest(path), path.stat().st_size)
            sha, size = checked[key]
            require(sha == spec[f"{tower}_sha256"], f"Accuracy model hash mismatch: {model}/{tower}")
            sizes[model][tower] = size
        require(sum(sizes[model].values()) == spec["total_bytes"], f"Model size mismatch: {model}")
    first, second = (data["models"][m] for m in (FP32, INT8))
    require(first["text_sha256"] == second["text_sha256"], "The principal comparison must share the same text model")
    require(first["preprocess"] == second["preprocess"], "Image preprocessing differs between FP32 and INT8")
    require(first["image_sha256"] != second["image_sha256"], "FP32 and INT8 image model files are identical")

    datasets = {}
    for name, count, class_count in (("cifar100", 2000, 100), ("imagenette", 3925, 10)):
        source, bundle = manifest[name], data["datasets"][name]
        samples, classes = source["samples"], source["classes"]
        require(len(samples) == count and len(set(s["id"] for s in samples)) == count, f"Incomplete/duplicate {name} samples")
        require(len(classes) == len(set(classes)) == class_count, f"Invalid {name} classes")
        require(source["prompts"] == [f"a photo of a {c}" for c in classes], "Class prompts differ")
        require(all(valid_sha(s["sha256"]) for s in samples), "Invalid evaluation sample hash")
        labels = np.array([s["label"] for s in samples])
        require(set(labels.tolist()) == set(range(class_count)), f"Invalid {name} labels")
        if name == "cifar100":
            require(set(Counter(labels.tolist()).values()) == {20}, "CIFAR must have 20 images per class")
            require(all(s["id"].startswith("test/") for s in samples), "CIFAR evaluation must use test images")
        else:
            require(all("/val/" in s["id"].replace("\\", "/") for s in samples), "Imagenette evaluation must use val images")
        rows = bundle["metrics"]
        require(set(rows) == set(MODELS), f"Incomplete {name} model rows")
        predictions = {}
        for model, row in rows.items():
            predicted = np.array(row["predicted_class"])
            require(predicted.shape == labels.shape and np.isin(predicted, np.arange(class_count)).all(), "Invalid predictions")
            correct = predicted == labels
            predictions[model] = predicted
            require(row["n"] == count and row["top1_correct"] == int(correct.sum()), "Top1 count mismatch")
            require(close(row["top1"], float(correct.mean())), "Top1 recomputation differs")
            require(row["top1"] <= row["top5"] <= 1 and close(row["top5"] * count, round(row["top5"] * count)), "Invalid Top5 count")
            require(len(row["per_class"]) == class_count, "Incomplete per-class metrics")
            for c, item in enumerate(row["per_class"]):
                require(item["class"] == classes[c] and item["support"] == int((labels == c).sum()), "Class mapping/support mismatch")
                require(close(item["top1"], float(correct[labels == c].mean())), "Per-class Top1 mismatch")
                require(0 <= item["p_at_10"] <= 1 and close(item["p_at_10"] * 10, round(item["p_at_10"] * 10)), "Invalid class P@10")
                require(0 <= item["ap"] <= 1, "Invalid class AP")
            for total, field in (("macro_top1", "top1"), ("class_query_p_at_10", "p_at_10"), ("class_query_map", "ap")):
                require(close(row[total], sum(v[field] for v in row["per_class"]) / class_count), f"Invalid aggregate: {total}")
        seen = set()
        for pair in bundle["comparisons"]:
            a, b = pair["a"], pair["b"]
            key = frozenset((a, b))
            require(a in rows and b in rows and a != b and key not in seen, "Duplicate or invalid paired comparison")
            seen.add(key)
            ca, cb = predictions[a] == labels, predictions[b] == labels
            delta = cb.astype(np.int8) - ca.astype(np.int8)
            counts = {"a_only_correct": int((ca & ~cb).sum()), "b_only_correct": int((cb & ~ca).sum()),
                      "both_correct": int((ca & cb).sum()), "both_wrong": int((~ca & ~cb).sum())}
            require(all(pair[k] == value for k, value in counts.items()), "Paired correctness counts differ")
            require(close(pair["b_minus_a_top1_pp"], float(delta.mean() * 100)), "Paired Top1 delta differs")
            rng = np.random.default_rng(SEED)
            boot = [rng.choice(delta, count, replace=True).mean() for _ in range(2000)]
            ci = np.quantile(boot, [.025, .975]) * 100
            require(np.allclose(pair["paired_bootstrap_95ci_pp"], ci, rtol=1e-9, atol=1e-7), "Paired bootstrap CI differs")
        require(len(seen) == 3, "All three model pairs must be present; the accuracy run is incomplete")
        ca, cb = predictions[FP32] == labels, predictions[INT8] == labels
        pair = next(p for p in bundle["comparisons"] if {p["a"], p["b"]} == {FP32, INT8})
        sign = 1 if pair["a"] == FP32 else -1
        ci = pair["paired_bootstrap_95ci_pp"]
        datasets[name] = {
            "n": count, "metrics": {m: {k: rows[m][k] for k in ("top1", "top5", "macro_top1", "class_query_p_at_10", "class_query_map")} for m in MODELS},
            "int8_minus_fp32_top1_pp": sign * pair["b_minus_a_top1_pp"],
            "paired_bootstrap_95ci_pp": ci if sign == 1 else [-ci[1], -ci[0]],
            "prediction_flips": int((predictions[FP32] != predictions[INT8]).sum()),
            "fp32_only_correct": int((ca & ~cb).sum()), "int8_only_correct": int((cb & ~ca).sum()),
            "both_correct": int((ca & cb).sum()), "both_wrong": int((~ca & ~cb).sum()),
            "both_wrong_prediction_flips": int((~ca & ~cb & (predictions[FP32] != predictions[INT8])).sum()),
        }
    return {"sizes_bytes": sizes, "datasets": datasets,
            "verification_scope": "Top1, class mappings, paired counts and all bootstrap CIs recomputed; Top5/P@10/AP checked for bounds/counts and per-class aggregates. Rankings are not exported and were not independently recomputed."}


def match_model(record, spec, tower, sizes):
    require(Path(record["file"]).name == Path(spec[tower]).name, "Quantization model filename mismatch")
    require(record["sha256"] == spec[f"{tower}_sha256"], "Quantization model hash mismatch")
    if "bytes" in record:
        require(record["bytes"] == sizes[tower], "Quantization model size mismatch")


def validate_quantization(metadata, path, accuracy, manifest, sizes):
    require(metadata["status"] == "passed" and metadata["onnxruntime_version"] == RUNTIME, "Quantization is not verified under ORT 1.29.0")
    require(metadata["format_parity"]["status"] == "passed", "INT8 ONNX-to-ORT export validation failed")
    match_model(metadata["source_fp32_ort"], accuracy["models"][FP32], "image", sizes[FP32])
    match_model(metadata["image"]["ort"], accuracy["models"][INT8], "image", sizes[INT8])
    match_model(metadata["shared_text"]["ort"], accuracy["models"][FP32], "text", sizes[FP32])
    for record in (metadata["source"], metadata["image"]["onnx"]):
        model_path = referenced_file(record["file"], path)
        require(digest(model_path) == record["sha256"] and model_path.stat().st_size == record["bytes"], "Source/quantized ONNX provenance differs")
    quant = metadata["quantization"]
    require(quant["format"] == "QDQ" and quant["activation_type"] in ("QInt8", "QUInt8") and quant["weight_type"] == "QInt8", "Expected static 8-bit QDQ image quantization")
    require(quant["calibration_method"] == "MinMax", "Unexpected calibration method")
    coverage = metadata["coverage"]
    heavy = coverage["quantized"]["heavy_ops"]
    totals = Counter(row["op_type"] for row in heavy)
    quantized = Counter(row["op_type"] for row in heavy if row["both_inputs_quantized"])
    require(sum(quantized.values()) > 0 and coverage["quantized"]["initializer_elements"].get("INT8", 0) > 0, "The candidate has no recorded INT8 weights/heavy operators")
    require(all(coverage["quantized_heavy_ops"].get(kind, 0) == quantized[kind] for kind in ("Conv", "MatMul", "Gemm")), "INT8 operator coverage totals differ")
    coverage_summary = {kind: {"total": totals[kind], "int8": quantized[kind], "float_or_partial": totals[kind] - quantized[kind]}
                        for kind in ("Conv", "MatMul", "Gemm")}
    remaining = [{k: row[k] for k in ("name", "op_type", "int8_dequantized_inputs")}
                 for row in heavy if not row["both_inputs_quantized"]]
    calibration = metadata["calibration"]
    require(calibration["seed"] == SEED and calibration["samples_total"] == 400, "Unexpected calibration selection")
    calibration_path = referenced_file(calibration["manifest_file"], path)
    require(digest(calibration_path) == calibration["manifest_sha256"], "Calibration manifest hash mismatch")
    source = read(calibration_path)
    require(source["seed"] == SEED, "Calibration manifest seed differs")
    require(source["preprocess"]["spec"] == accuracy["models"][FP32]["preprocess"], "Calibration and evaluation preprocessing differ")
    preprocessing_path = referenced_file(source["preprocess"]["source_file"], calibration_path)
    require(digest(preprocessing_path) == source["preprocess"]["source_file_sha256"], "Calibration preprocessing implementation changed")
    samples = source["samples"]
    require(len(samples) == 400 and len({(s["dataset"], s["id"]) for s in samples}) == 400, "Incomplete/duplicate calibration samples")
    require(all(s["dataset"] in manifest and s["split"] == "train" for s in samples), "Calibration must use train splits only")
    require(all(valid_sha(s[k]) for s in samples for k in ("sha256", "rgb_sha256", "preprocessed_sha256")), "Invalid calibration sample hash")
    declared = {d["dataset"]: d for d in calibration["datasets"]}
    require(set(declared) == set(manifest), "Calibration dataset declarations differ")
    for name, classes, per_class in (("cifar100", 100, 2), ("imagenette", 10, 20)):
        chosen = [s for s in samples if s["dataset"] == name]
        require(len(chosen) == 200 and Counter(s["label"] for s in chosen) == {c: per_class for c in range(classes)}, "Calibration class balance differs")
        require(declared[name]["split"] == "train" and declared[name]["samples"] == 200 and declared[name]["per_class"] == per_class, "Calibration declaration mismatch")
        evaluated = manifest[name]["samples"]
        require(not ({s["sha256"] for s in chosen} & {s["sha256"] for s in evaluated}), "Calibration/evaluation image hash overlap")
        require(not ({s["id"].replace("\\", "/") for s in chosen} & {s["id"].replace("\\", "/") for s in evaluated}), "Calibration/evaluation sample ID overlap")
        if name == "cifar100":
            require(not ({s["rgb_sha256"] for s in chosen} & {s["sha256"] for s in evaluated}), "CIFAR calibration/evaluation RGB overlap")
    leakage = calibration["leakage_check"]
    require(leakage["status"] == "passed" and leakage["native_sha256_overlap"] == leakage["rgb_sha256_overlap"] == 0, "Calibration leakage verification failed")
    require(leakage["evaluation_counts"] == {"cifar100": 2000, "imagenette": 3925}, "Leakage check used different evaluation counts")
    evaluation_path = referenced_file(leakage["evaluation_manifest_file"], path)
    require(digest(evaluation_path) == leakage["evaluation_manifest_sha256"], "Leakage evaluation manifest hash mismatch")
    require(sample_identity(read(evaluation_path)) == sample_identity(manifest), "Leakage check used different evaluation images or classes")
    return {"quantization": quant, "coverage_summary": coverage_summary, "float_or_partial_nodes": remaining,
            "coverage": coverage, "calibration_samples": len(samples),
            "calibration_manifest": str(calibration_path), "calibration_manifest_sha256": digest(calibration_path),
            "calibration_overlap": {"native_sha256": 0, "sample_ids": 0, "rgb_sha256_producer_verified": 0},
            "leakage_scope": "Sample IDs/native hashes and CIFAR RGB hashes independently compared; all-image RGB overlap checked by quantization exporter against the same evaluation sample identities.",
            "format_parity": metadata["format_parity"], "quantization_cosines": metadata.get("quantization_cosines")}


def validate_device(root, accuracy, sizes):
    metadata, fixture = read(root / "run-metadata.json"), read(root / "fixture-manifest.json")
    require(metadata["status"] == "complete" and metadata["rounds"] == 2, "Device benchmark needs two completed rounds")
    require(metadata["sample_count"] == 100 and metadata["warmup_count"] == 10, "Device sample/warmup counts differ")
    require(str(metadata["android"]) == "17", "Device benchmark must use Android 17")
    fixtures = {r["id"]: r for r in fixture["models"]}
    require(len(fixture["models"]) == 3 and set(fixtures) == set(MODELS), "Device fixture model matrix differs")
    require(fixture["token_count"] == len(fixture["prompts"]) == 13 and valid_sha(fixture["tokens_sha256"]), "Invalid device text fixtures")
    require(len(fixture["fixture_sha256"]) == 5 and all(valid_sha(v) for v in fixture["fixture_sha256"].values()), "Invalid source photo fixtures")
    for model, item in fixtures.items():
        require(item["backend"] == "onnx" and item["format"] == "ort", "Device must use ORT through ONNX Runtime")
        require(item["preprocess"] == accuracy["models"][model]["preprocess"] and item["input_size"] == item["preprocess"]["size"], "Device preprocessing differs")
        require(item["image_count"] == 5 and valid_sha(item["image_input_sha256"]), "Invalid device image fixtures")
        for tower in ("image", "text"):
            require(item[f"{tower}_sha256"] == accuracy["models"][model][f"{tower}_sha256"], "Device/accuracy model hash mismatch")
            require(item[f"{tower}_file_bytes"] == sizes[model][tower], "Device fixture model size mismatch")
    require(fixtures[FP32]["image_input_sha256"] == fixtures[INT8]["image_input_sha256"], "FP32 and INT8 device inputs differ")
    keys = [(r["model"], r["round"]) for r in metadata["runs"]]
    require(len(keys) == len(set(keys)) == 6 and set(keys) == {(m, r) for m in MODELS for r in (1, 2)}, "Incomplete or duplicate device rounds")
    require([r for _, r in keys] == [1, 1, 1, 2, 2, 2] and [m for m, _ in keys[3:]] == [m for m, _ in keys[:3]][::-1], "Device rounds must run in forward/reverse order")
    grouped, artifacts = {m: [] for m in MODELS}, {}
    for run in metadata["runs"]:
        path = root / run["report"]
        require(path.resolve().is_relative_to(root.resolve()), "Device report path escapes its directory")
        row = read(path)
        model, item = run["model"], fixtures[run["model"]]
        require(row["status"] == "passed" and row["model_id"] == model and row["round_id"] == f"r{run['round']}", "Device model/round failed or mismatched")
        require(row["fingerprint"] == metadata["fingerprint"] and row["sdk"] == 37 and "arm64-v8a" in row["abis"], "Device identity/ABI differs")
        require(row["runtime_version"] == RUNTIME and row["cpu_threads"] == 4 and row["backend"] == "onnx", "Device runtime/thread/backend differs")
        require(row["sample_count"] == 100 and row["warmup_count"] == 10, "Per-run sample counts differ")
        require(row["model_hashes_verified"] is True and row["input_hashes_verified"] is True, "Device input/model hashes were not verified")
        require(row["tokens_sha256"] == fixture["tokens_sha256"] and row["image_input_sha256"] == item["image_input_sha256"], "Device fixture hashes differ")
        require(row["preloaded_image_count"] == 5 and row["preloaded_text_count"] == 13 and row["input_size"] == item["input_size"], "Device input counts/shape differ")
        for tower in ("image", "text"):
            require(row[f"actual_{tower}_sha256"] == item[f"{tower}_sha256"] and row[f"{tower}_file_bytes"] == sizes[model][tower], "Device model file differs")
            result = row[tower]
            verify_stats(result)
            require(len(result["raw_samples_ms"]) == result["summary"]["count"] == 100 and result["validated_output_count"] == 111, "Incomplete device measurements")
            require(all(math.isfinite(result[k]) and result[k] > 0 for k in ("load_ms", "first_invoke_ms")), "Invalid device startup timing")
            output = result["first_output"]
            require(len(output) == 512 and all(math.isfinite(v) for v in output), "Invalid device first embedding")
            norm = math.sqrt(sum(v * v for v in output))
            require(norm > 1e-8, "Zero device first embedding")
            if model != LEGACY:
                require(abs(norm - 1) <= 1e-3, "MobileCLIP first embedding is not normalized")
        sustained = row["sustained_image"]
        duration = metadata["sustain_seconds_first_round"] if run["round"] == 1 else 0
        require(row["sustain_seconds"] == duration, "Per-run sustained duration differs")
        if duration:
            require(sustained["actual_elapsed_s"] >= duration and sum(s["count"] for s in sustained["segments"]) == sustained["count"], "Incomplete sustained device run")
        else:
            require(sustained["skipped"] is True, "Unexpected sustained measurement")
        require(bool(row["health_samples"]), "Missing device health samples")
        grouped[model].append(row)
        artifacts[run["report"]] = digest(path)
    models = {}
    for model, rows in grouped.items():
        rows.sort(key=lambda r: r["round_id"])
        health = [h for row in rows for h in row["health_samples"]]
        temperatures = [h["battery_temperature_c"] for h in health if h["battery_temperature_c"] is not None]
        models[model] = {"thermal_statuses": sorted({h["thermal_status"] for h in health}),
                         "temperature_c_range": [min(temperatures), max(temperatures)] if temperatures else None,
                         "max_observed_process_pss_mib": max(h["sampled_total_pss_kb"] for h in health) / 1024}
        for tower in ("image", "text"):
            models[model][tower] = {**stats([v for row in rows for v in row[tower]["raw_samples_ms"]]),
                                   "round_p50_ms": [row[tower]["summary"]["p50_ms"] for row in rows],
                                   "load_ms": [row[tower]["load_ms"] for row in rows],
                                   "first_invoke_ms": [row[tower]["first_invoke_ms"] for row in rows],
                                   "first_output_norm": [math.sqrt(sum(v * v for v in row[tower]["first_output"])) for row in rows]}
    return {"metadata": metadata, "models": models, "raw_sample_count": 1200,
            "percentile_method": "nearest rank", "run_sha256": artifacts,
            "manifest_sha256": digest(root / "fixture-manifest.json"), "metadata_sha256": digest(root / "run-metadata.json")}


def link(label, path, output):
    relative = os.path.relpath(path.resolve(), output.parent.resolve()).replace("\\", "/")
    return f"[{label}](<{relative}>)"


def render(verification, accuracy, args, manifest_path, language="en"):
    require(language in ("en", "zh"), "Unsupported report language")
    def t(english, chinese):
        return english if language == "en" else chinese

    labels = LABELS if language == "zh" else {
        FP32: "MobileCLIP2 S0 · FP32 image", INT8: "MobileCLIP2 S0 · mixed INT8 image",
        LEGACY: "Legacy CLIP · INT8 image/text",
    }
    sizes = verification["accuracy"]["sizes_bytes"]
    archive = Path(__file__).parent / "results" / "image-int8"
    counterpart = args.output.with_name(args.output.stem + "_zh.md") if language == "en" else args.output
    lines = [t("# MobileCLIP2 image INT8 measurements", "# MobileCLIP2 图像 INT8 实测"), "",
             link(t("简体中文", "English"), counterpart, args.output), "",
             t("The principal comparison changes only the MobileCLIP2 S0 image quantization configuration, sharing the same dynamic INT8 text tower and using ORT files. This is mixed precision: the retained FP32 nodes and actual INT8 coverage are listed below. Legacy CLIP with both towers quantized is a separate reference.",
               "主比较固定同一个动态 INT8 文本塔，仅替换 MobileCLIP2 S0 图像塔的量化配置；两组均使用 ORT 文件。本候选是混合精度，INT8 算子覆盖及保留的 FP32 节点在下文列明。历史 CLIP 双 INT8 作为辅助参考。"), "",
             t("## Host accuracy", "## 主机识别精度"), "",
             t(f"These accuracy measurements run on the host CPU with ONNX Runtime {RUNTIME} and 4 threads. Full phone accuracy is reported separately; host results are not Android results.",
               f"本节准确率在主机 CPU 上评测，ONNX Runtime {RUNTIME}、4 线程；不能直接视作 Android 准确率。手机完整评测单独记录。"),
             t(f"Host: {accuracy['host']}.", f"主机环境：{accuracy['host']}。"),
             t("CIFAR-100 uses 20 fixed test images per class (2,000 total); Imagenette uses the complete validation split (3,925). All classes use the single English prompt `a photo of a {class}`. Evaluation data is not used to choose prompts or quantization parameters.",
               "CIFAR-100 使用 test 每类固定抽 20 张，共 2,000 张；Imagenette 使用完整 val，共 3,925 张。固定英文单模板 `a photo of a {class}`，不在评测集选择提示词或量化参数。"), "",
             t("| Dataset | Model | Top1 | Top5 | Class-query P@10 | Class-query mAP |", "| 数据集 | 模型 | Top1 | Top5 | 类别查询 P@10 | 类别查询 mAP |"), "|---|---|---:|---:|---:|---:|"]
    for dataset, item in verification["accuracy"]["datasets"].items():
        for model in MODELS:
            row = item["metrics"][model]
            cells = [f"{row[k]*100:.2f}%" for k in ("top1", "top5", "class_query_p_at_10", "class_query_map")]
            lines.append(f"| {dataset} | {labels[model]} | " + " | ".join(cells) + " |")
    lines += ["", t("P@10 and mAP rank all images within each dataset for each class-text query and average over classes. They are not caption–image Recall@K. CIFAR source images are only 32×32, and Imagenette has 10 classes; neither replaces ImageNet-1k or real photo-library evaluation.", "P@10 和 mAP 使用类别文本查询检索同一数据集的全部图片，按类别平均；它们不是自然语言描述与图片配对的 Recall@K。CIFAR 原图仅 32×32；Imagenette 只有 10 类，不能代替 ImageNet-1k 或真实相册测试。"), "",
              t("## Image quantization differences", "## 图像量化差值"), "",
              t("Differences are mixed INT8 − FP32, in percentage points (pp). Paired 95% bootstrap intervals resample the same images 2,000 times with seed 20260913; they describe sampling uncertainty within these datasets only.", "差值为混合 INT8 − FP32，单位百分点（pp）。95% 区间使用相同图片的 2,000 次成对 bootstrap，固定 seed 20260913；仅反映当前数据分布的抽样不确定性。"), "",
              t("| Dataset | Top1 delta pp | Paired 95% CI pp | FP32-only / INT8-only correct | Prediction flips | Both-wrong flips |", "| 数据集 | Top1 差值 pp | 配对 95% CI pp | FP32 独对 / INT8 独对 | 预测翻转 | 其中两者均错的翻转 |"), "|---|---:|---:|---:|---:|---:|"]
    for dataset, row in verification["accuracy"]["datasets"].items():
        low, high = row["paired_bootstrap_95ci_pp"]
        lines.append(f"| {dataset} | {row['int8_minus_fp32_top1_pp']:+.2f} | [{low:+.2f}, {high:+.2f}] | {row['fp32_only_correct']} / {row['int8_only_correct']} | {row['prediction_flips']} / {row['n']} ({row['prediction_flips']/row['n']*100:.2f}%) | {row['both_wrong_prediction_flips']} |")
    lines += ["", t("Prediction flips include corrections, correct-to-wrong changes and changes between two wrong classes. Neither flip rate nor vector cosine replaces accuracy. Per-class results are in the accuracy JSON.", "预测翻转包括纠错、由对变错、以及两次均错但类别不同；翻转率和向量 cosine 都不能替代识别准确率。逐类结果见原始准确率 JSON。"), "",
              t("## Calibration and model size", "## 校准与模型大小"), "",
              t("Static quantization uses fixed MinMax calibration: 2 CIFAR-100 training images per class and 20 Imagenette training images per class, 200 from each dataset, 400 total, seed 20260913. Evaluation images are excluded, and parameters were not selected using evaluation results.", "静态量化使用预先固定的 MinMax 校准：CIFAR-100 train 每类 2 张、Imagenette train 每类 20 张，各 200 张，总计 400 张，seed 20260913。评测图片不参与校准，也未根据评测结果挑选参数。"),
              t("Validation checks model and shared-text SHA256, identical preprocessing, calibration records and evaluation identities. Sample IDs/native hashes are independently checked for overlap; the exporter records the complete RGB overlap check against the same evaluation samples.", "报告核对模型与共享文本 SHA256、相同预处理、训练样本记录与评测清单；独立重查样本 ID/原生哈希不相交，全部 RGB 哈希去重检查由量化导出器记录并关联相同评测样本。"), "",
              t("| Model | Image MiB | Text MiB | Total MiB |", "| 模型 | 图像 MiB | 文本 MiB | 合计 MiB |"), "|---|---:|---:|---:|"]
    for model in MODELS:
        row = sizes[model]
        lines.append(f"| {labels[model]} | {row['image']/2**20:.2f} | {row['text']/2**20:.2f} | {(row['image']+row['text'])/2**20:.2f} |")
    quant = verification["quantization"]["quantization"]
    lines += ["", f"{quant['format']}: activation={quant['activation_type']}, weight={quant['weight_type']}, per_channel={quant['per_channel']}.",
              t(f"The image file is {sizes[INT8]['image']/sizes[FP32]['image']*100:.1f}% of the FP32 size. See the metadata for operator coverage, retained FP32 nodes and vector diagnostics.", f"图像文件大小为 FP32 的 {sizes[INT8]['image']/sizes[FP32]['image']*100:.1f}%；具体算子覆盖、保留 FP32 的部分和向量诊断见量化 metadata。"),
              t("A metadata status of passed verifies format, tensor contracts or conversion parity, not acceptable recognition quality; use the measured accuracy above to assess quality.", "metadata 中的 passed 表示模型格式、输出契约或转换一致性检查通过，不表示 INT8 识别质量达标；质量变化应以上面的实测准确率为依据。"), "",
              t("Heavy operators in the quantized ONNX graph (fused ORT operators are listed separately in the metadata):", "量化 ONNX 图中的重算子覆盖（融合后的 ORT 算子清单另见 metadata）："), "",
              t("| Operator | Total | Both inputs quantized to INT8 | Float or partially quantized |", "| 算子 | 总数 | 两输入均经 INT8 量化 | 保留浮点或部分量化 |"), "|---|---:|---:|---:|"]
    for kind, row in verification["quantization"]["coverage_summary"].items():
        lines.append(f"| {kind} | {row['total']} | {row['int8']} | {row['float_or_partial']} |")
    remaining = verification["quantization"]["float_or_partial_nodes"]
    if remaining:
        full_float = sum(r["int8_dequantized_inputs"] == 0 for r in remaining)
        lines += ["", t(f"This mixed-precision candidate retains {full_float} heavy operators in FP32 and {len(remaining)-full_float} with partially quantized inputs.", f"本候选采用混合精度：{full_float} 个重算子保留 FP32，{len(remaining)-full_float} 个重算子仅部分输入量化。"), "",
                  t(f"<details><summary>Float or partially quantized heavy operators ({len(remaining)})</summary>", f"<details><summary>保留浮点或部分量化的重算子（{len(remaining)} 个）</summary>"), "", "```text"]
        lines += [f"{r['op_type']} | INT8 inputs: {r['int8_dequantized_inputs']} | {r['name']}" for r in remaining]
        lines += ["```", "", "</details>"]
    device = verification.get("device")
    if device:
        metadata = device["metadata"]
        lines += ["", t("## Android device latency", "## Android 真机耗时"), "",
                  f"{metadata['model']} / {metadata['soc']} / Android {metadata['android']} / ONNX Runtime {RUNTIME} / CPU 4 threads.",
                  t("Each model has two rounds with 10 warmups and 100 timed samples per tower; first invocation is recorded separately. There are 1,200 timed samples total, with reversed model order in the second round.", "每模型两轮，每塔首次执行单列、预热 10 次、正式记录 100 次，总计 1,200 条时延；第二轮反向顺序。"),
                  t(f"Run window (UTC): {metadata['started_utc']} to {metadata['completed_utc']}.", f"运行区间 UTC：{metadata['started_utc']} 至 {metadata['completed_utc']}。"), "",
                  t("| Model | Image P50 / P95 ms | Text P50 / P95 ms | Image P50 by round | Text P50 by round |", "| 模型 | 图片 P50 / P95 ms | 文本 P50 / P95 ms | 两轮图片 P50 | 两轮文本 P50 |"), "|---|---:|---:|---:|---:|"]
        for model, row in device["models"].items():
            a, b = row["image"], row["text"]
            pairs = [" / ".join(f"{v:.2f}" for v in row[t]["round_p50_ms"]) for t in ("image", "text")]
            lines.append(f"| {labels[model]} | {a['p50_ms']:.2f} / {a['p95_ms']:.2f} | {b['p50_ms']:.2f} / {b['p95_ms']:.2f} | {pairs[0]} | {pairs[1]} |")
        ratio = device["models"][FP32]["image"]["p50_ms"] / device["models"][INT8]["image"]["p50_ms"]
        lines += ["", t(f"FP32 / mixed INT8 image P50 ratio: {ratio:.3f}; above 1 means INT8 is faster. Percentiles use nearest rank after pooling both rounds.", f"FP32 / 混合 INT8 图片 P50 比值为 {ratio:.3f}；大于 1 表示 INT8 更快。两轮原始时延合并后按 nearest rank 计算分位数。"), "",
                  t("Timing includes copying preloaded inputs, runtime execution and copying outputs. It excludes image decoding/resizing, tokenization and similarity search. Inputs cycle through five public photos and 13 texts; this is separate from full-dataset accuracy evaluation.", "计时包含预加载输入拷贝、运行库执行和输出拷贝，不含读图、缩放、分词或相似度搜索。输入循环使用五张公开图片与 13 条文本；它与完整数据集的准确率评估范围不同。"), "",
                  t("| Model | Image load ms by round | Image first call ms | Text load ms | Text first call ms | Thermal status | Observed process PSS MiB |", "| 模型 | 图片加载 ms（各轮） | 图片首次执行 ms | 文本加载 ms | 文本首次执行 ms | thermal status | 观测进程 PSS MiB |"), "|---|---:|---:|---:|---:|---|---:|"]
        for model, row in device["models"].items():
            cells = [" / ".join(f"{v:.2f}" for v in row[t][k]) for t in ("image", "text") for k in ("load_ms", "first_invoke_ms")]
            lines.append(f"| {labels[model]} | " + " | ".join(cells) + f" | {row['thermal_statuses']} | {row['max_observed_process_pss_mib']:.1f} |")
        lines += ["", t("Model hashes are verified before loading, so the filesystem cache may be warm: load time is not cold-disk startup. PSS is sampled during single-tower execution, not peak memory or concurrent app tower residency. First outputs must be finite, nonzero and 512-dimensional; legacy CLIP retains raw norms. FP32 and quantized vectors need not be equal.", "每塔加载前已校验模型 SHA256，文件缓存可能已热，不能称为冷磁盘启动。PSS 是单塔运行时的阶段采样，不是峰值或 App 双塔同时驻留内存。首输出检查有限、非零和 512 维；历史 CLIP 输出保留原始范数。此处不要求 FP32 与量化模型向量相等。"),
                  t(f"Extra sustained testing in the first round was set to {metadata['sustain_seconds_first_round']} seconds. Two short rounds do not establish thermal steady state for long library indexing. Frequencies and core affinity were not fixed; temperature, USB charging and scheduling can affect timing.", f"第一轮额外持续测试设为 {metadata['sustain_seconds_first_round']} 秒；短时双轮结果不能说明长时间相册索引的热稳态。测试未锁频或绑核，温度、USB 充电、系统调度仍可能影响耗时。")]
    else:
        lines += ["", t("Android latency is not included in this report; smaller files do not by themselves imply faster phone inference.", "本报告尚未纳入 Android 真机时延；模型文件缩小不能直接推断手机推理更快。")]
    lines += ["", t("## Evidence and reproduction", "## 可复核文件"), "",
              link(t("Archived experiment history (Chinese)", "归档实验过程与初始失败方案"), archive / "experiment.md", args.output) + " · " + link(t("Archived quantization metadata", "归档量化 metadata"), archive / "quantization-metadata.json", args.output) + " · " + link(t("Archived full phone accuracy", "归档手机完整准确率"), archive / "pixel8a-accuracy" / t("README.md", "README_zh.md"), args.output), "",
              link(t("Host accuracy, predictions and versions", "主机准确率、逐图预测及依赖版本"), args.accuracy, args.output) + " · " + link(t("Evaluation samples", "评测样本清单"), manifest_path, args.output) + " · " + link(t("Machine validation", "机器验证结果"), args.output.with_name(args.output.stem + "-validation.json"), args.output), "",
              t("The validator recomputes Top1, paired counts, flips and bootstrap intervals, and checks exported Top5/retrieval values and per-class aggregates. Full rankings are not archived, so this report does not independently recompute Top5/retrieval rankings. Legacy CLIP uses INT8 for both towers, stretch224 and the original app normalization; that row cannot isolate image-quantization effects.", "验证器重算 Top1、配对计数、翻转和 bootstrap CI；Top5 与检索指标验证导出值及逐类聚合。准确率文件没有导出完整排序，本报告未独立重算 Top5/检索排序。历史 CLIP 使用双 INT8、stretch224 与原 App 归一化，辅助行不能用于单独归因图像量化的收益。"), "",
              t("JSON measurements, per-image predictions, manifests and hashes are archived. Large model/input files and raw feature arrays are generated locally and excluded from Git. A fresh checkout can audit archived counts and report values; full inference or ranking recomputation requires regenerating those artifacts first.", "JSON 测量值、逐图预测、清单和哈希均已归档。大型模型、输入文件和原始特征数组在本地生成，不纳入 Git。新克隆可审计归档计数与报告数值；完整推理或排序重算须先重新生成这些产物。")]
    if args.device_dir:
        lines += ["", link(t("Device run metadata", "真机执行元数据"), args.device_dir / "run-metadata.json", args.output) + " · " + link(t("Device models and inputs", "真机模型与输入清单"), args.device_dir / "fixture-manifest.json", args.output) + t(". Per-model JSON files in that directory retain raw timings and first outputs.", "。同目录各模型 JSON 保留原始时延和首输出。")]
    return "\n".join(lines) + "\n"


def validate_bilingual_tables(english, chinese):
    """Catch numerical drift while leaving translated labels and prose unrestricted."""
    def values(report):
        rows = []
        for line in report.splitlines():
            if line.startswith("|"):
                # Ignore numbers embedded in identifiers such as FP32 and Top5.
                rows.append(re.findall(r"(?<![\w])[-+]?\d+(?:\.\d+)?(?:%|x)?", line))
        return rows
    require(values(english) == values(chinese), "English/Chinese report table numbers differ")


def write_bilingual(output, english, chinese):
    validate_bilingual_tables(english, chinese)
    output.write_text(english, encoding="utf-8")
    output.with_name(output.stem + "_zh.md").write_text(chinese, encoding="utf-8")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--accuracy", type=Path, required=True)
    parser.add_argument("--quantization", type=Path, required=True)
    parser.add_argument("--device-dir", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    manifest_path = args.accuracy.with_name(args.accuracy.stem + "-manifest.json")
    accuracy, manifest = read(args.accuracy), read(manifest_path)
    verification = {"status": "passed", "status_scope": "Artifact consistency and report validation only; not a model-quality acceptance decision.", "onnxruntime_version": RUNTIME,
                    "renderer_sha256": digest(Path(__file__)), "numpy_version": np.__version__,
                    "sources_sha256": {"accuracy": digest(args.accuracy), "evaluation_manifest": digest(manifest_path), "quantization_metadata": digest(args.quantization)}}
    verification["accuracy"] = validate_accuracy(accuracy, manifest)
    verification["quantization"] = validate_quantization(read(args.quantization), args.quantization, accuracy, manifest, verification["accuracy"]["sizes_bytes"])
    if args.device_dir:
        verification["device"] = validate_device(args.device_dir, accuracy, verification["accuracy"]["sizes_bytes"])
    english = render(verification, accuracy, args, manifest_path, "en")
    chinese = render(verification, accuracy, args, manifest_path, "zh")
    validate_bilingual_tables(english, chinese)
    verification["bilingual_tables_verified"] = True
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.with_name(args.output.stem + "-validation.json").write_text(json.dumps(verification, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    write_bilingual(args.output, english, chinese)
    print(f"Verified image INT8 comparison; wrote {args.output}")


if __name__ == "__main__":
    main()
