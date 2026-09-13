#!/usr/bin/env python3
"""Score complete phone-exported embeddings using the existing accuracy protocol.

No model inference or device commands run here. Use the repository's evaluation
Python environment and run from the repository root.
"""
from __future__ import annotations

import argparse
from collections import Counter
import hashlib
import json
import math
from pathlib import Path

import numpy as np
import open_clip

from evaluate_accuracy import PROTOCOL, SEED, comparisons, json_hash, metrics
from render_image_int8_report import (
    FP32, INT8, RUNTIME, close, digest, link, read, require, valid_sha,
    validate_bilingual_tables, write_bilingual,
)


MODELS = (FP32, INT8)
DATASETS = {"cifar100": (2000, 100), "imagenette": (3925, 10)}
LABELS = {FP32: "FP32 图像 + 动态 INT8 文本", INT8: "混合 INT8 图像 + 同一动态 INT8 文本"}


def local_file(root, name):
    require(isinstance(name, str) and Path(name).name == name and name not in (".", ".."), "Expected a plain artifact filename")
    path = root / name
    require(path.resolve().is_relative_to(root.resolve()) and path.is_file(), f"Missing/invalid artifact: {path}")
    return path


def model_map(rows):
    result = {row["id"]: row for row in rows}
    require(len(rows) == len(result) == 2 and set(result) == set(MODELS), "Expected exactly the FP32 and mixed INT8 image models")
    return result


def validate_inputs(args, manifest, evaluation, host):
    require(manifest["schema_version"] == 1, "Unknown device input schema")
    require(manifest["evaluation_manifest_sha256"] == digest(args.evaluation_manifest), "Evaluation manifest file hash mismatch")
    require(manifest["evaluation_samples_sha256"] == json_hash(evaluation) == host["manifest_sha256"], "Host/device evaluation samples differ")
    require(manifest["host_accuracy_sha256"] == digest(args.host), "Host accuracy file differs from preparation")
    require(host["protocol"] == PROTOCOL and host["seed"] == SEED and host["threads"] == 4, "Host accuracy protocol differs")
    require(host["versions"]["onnxruntime"] == manifest["versions"]["onnxruntime"] == RUNTIME, "Expected ONNX Runtime 1.29.0")
    require(set(evaluation) == set(host["datasets"]) == set(DATASETS), "Both complete datasets are required")
    require(host["prompt_template"] == "a photo of a {class}", "Host prompt protocol differs")
    models = model_map(manifest["models"])
    datasets = {row["id"]: row for row in manifest["datasets"]}
    require(len(manifest["datasets"]) == len(datasets) == 2 and set(datasets) == set(DATASETS), "Duplicate or incomplete input datasets")
    checked_models = {}
    for model, row in models.items():
        spec = host["models"][model]
        require(spec["preprocess"] == manifest["preprocess"], "Host/device image preprocessing differs")
        for tower in ("image", "text"):
            path = Path(spec[tower])
            require(path.suffix == ".ort" and path.name == row[f"{tower}_file"], "Model format/filename differs")
            if str(path) not in checked_models:
                checked_models[str(path)] = digest(path)
            require(checked_models[str(path)] == row[f"{tower}_sha256"] == spec[f"{tower}_sha256"], "Host/device model hash differs")
    require(models[FP32]["text_sha256"] == models[INT8]["text_sha256"], "Image quantization comparison must share the text model")
    require(models[FP32]["image_sha256"] != models[INT8]["image_sha256"], "Image model files must differ")

    source_hashes = {}
    if args.source_input_dir:
        require(digest(args.source_input_dir / "manifest.json") == digest(args.input / "manifest.json"), "Source and pulled input manifests differ")
    tokenizer = open_clip.get_tokenizer("MobileCLIP2-S0")
    for name, (count, classes_count) in DATASETS.items():
        bundle, row = evaluation[name], datasets[name]
        samples, classes = bundle["samples"], bundle["classes"]
        require(len(samples) == count and len(set(s["id"] for s in samples)) == count, "Incomplete/duplicate evaluation samples")
        require(len(classes) == len(set(classes)) == classes_count, "Invalid evaluation classes")
        require(bundle["prompts"] == [f"a photo of a {c}" for c in classes], "Evaluation class prompts differ")
        require(all(valid_sha(s["sha256"]) for s in samples), "Invalid evaluation image hash")
        labels = np.asarray([s["label"] for s in samples])
        require(set(labels.tolist()) == set(range(classes_count)), "Invalid evaluation labels")
        if name == "cifar100":
            require(set(Counter(labels.tolist()).values()) == {20} and all(s["id"].startswith("test/") for s in samples), "CIFAR must use 20 test images per class")
        else:
            require(all("/val/" in s["id"].replace("\\", "/") for s in samples), "Imagenette must use the validation split")
        require(row["image_count"] == count and row["token_count"] == classes_count, "Prepared input counts differ")
        require(valid_sha(row["image_sha256"]) and valid_sha(row["tokens_sha256"]), "Invalid prepared input SHA256")
        tokens = tokenizer(bundle["prompts"]).numpy().astype("<i4")
        require(tokens.shape == (classes_count, 77), "Unexpected tokenizer shape")
        require(hashlib.sha256(tokens.tobytes()).hexdigest() == row["tokens_sha256"], "Token input hash does not match evaluation prompts/order")
        require(bool(host["datasets"][name].get("comparisons")), "Host accuracy evaluation is incomplete")
        for model in MODELS:
            result = host["datasets"][name]["metrics"][model]
            predicted = np.asarray(result["predicted_class"])
            require(predicted.shape == labels.shape and np.isin(predicted, np.arange(classes_count)).all(), "Invalid host predictions")
            correct = predicted == labels
            require(result["n"] == count and result["top1_correct"] == int(correct.sum()) and close(result["top1"], float(correct.mean())), "Host Top1 does not match predictions")
            require(all(math.isfinite(result[k]) and 0 <= result[k] <= 1 for k in ("top1", "top5", "class_query_p_at_10", "class_query_map")), "Invalid host accuracy metric")
        if args.source_input_dir:
            for tower, length in (("image", count * 3 * manifest["preprocess"]["size"] ** 2 * 4),
                                  ("tokens", classes_count * 77 * 4)):
                path = local_file(args.source_input_dir, row[f"{tower}_file"])
                require(path.stat().st_size == length, "Source tensor byte length differs")
                actual_sha = digest(path)
                require(actual_sha == row[f"{tower}_sha256"], "Source tensor SHA256 differs")
                source_hashes[path.name] = actual_sha
    return models, datasets, {"models_rehashed": checked_models, "source_tensors_rehashed": source_hashes,
                              "token_hashes_reproduced_from_prompts": True,
                              "source_tensor_bytes_rehashed": bool(args.source_input_dir)}


def load_features(root, record, expected_name, count):
    require(record["status"] == "passed", "Incomplete feature export")
    require(record["file"] == expected_name and record["shape"] == [count, 512] and record["count"] == count, "Feature filename/shape/count differs")
    require(record["completed_count"] == count, "Feature export did not complete every input")
    require(record["bytes"] == count * 512 * 4, "Feature byte count differs")
    path = local_file(root, record["file"])
    require(path.stat().st_size == record["bytes"] and digest(path) == record["sha256"], "Pulled feature file hash/size differs")
    values = np.fromfile(path, dtype="<f4").reshape(count, 512)
    require(np.isfinite(values).all(), "Nonfinite exported embedding")
    norms = np.linalg.norm(values, axis=1, keepdims=True)
    require(np.isfinite(norms).all() and np.all(norms >= 1e-8), "Zero/invalid exported embedding norm")
    require(math.isfinite(record["elapsed_ms"]) and record["elapsed_ms"] >= 0, "Invalid export duration")
    return values / norms, {"file": path.name, "sha256": record["sha256"], "shape": [count, 512],
                            "raw_norm_min": float(norms.min()), "raw_norm_max": float(norms.max()),
                            "normalization": "Per-row float32 L2 normalization before cosine scoring"}


def validate_report(root, model, expected_model, datasets, manifest_sha, evaluation_sha):
    path = local_file(root, f"{model}-report.json")
    report = read(path)
    require(report["status"] == "passed" and report["model_id"] == model, "Device export failed or has wrong model ID")
    require(report["manifest_sha256"] == manifest_sha and report["evaluation_manifest_sha256"] == evaluation_sha, "Device input/evaluation manifest differs")
    require(report["runtime_version"] == RUNTIME and report["cpu_threads"] == 4 and report["inter_op_threads"] == 1, "Device runtime/thread configuration differs")
    require(report["model_hashes_verified"] is True and report["input_hashes_verified"] is True, "Device model/input hashes were not verified")
    require(report["output_format"] == "Raw little-endian float32 [count,512], manifest order; no additional normalization.", "Unknown feature byte order/layout")
    require(report["sdk"] >= 29 and "arm64-v8a" in report["abis"] and report["fingerprint"], "Expected supported Android ARM64 device identity")
    config = report["session_configuration"]
    require(config["execution_provider"] == "CPUExecutionProvider" and config["intra_op_threads"] == 4 and config["inter_op_threads"] == 1, "Device session provider/threads differ")
    require(config["execution_mode"] == "ORT_SEQUENTIAL (runtime default)" and config["graph_optimization"] == "runtime default", "Device execution/optimization mode differs")
    require(all(report["model"][k] == value for k, value in expected_model.items()), "Device selected model differs from manifest")
    for tower in ("image", "text"):
        require(report[f"actual_{tower}_sha256"] == expected_model[f"{tower}_sha256"], "Device model SHA256 differs")
        session = report[f"{tower}_session"]
        contract = session["tensor_contract"]
        require(session["runtime_version"] == RUNTIME, "Tower runtime differs")
        require(contract["input_shape"] == ([1, 3, 256, 256] if tower == "image" else [1, 77]), "Tower input shape differs")
        require(contract["input_type"] in (("FLOAT",) if tower == "image" else ("INT32", "INT64")), "Tower input type differs")
        require(contract["output_shape"] == [1, 512] and contract["output_type"] == "FLOAT32", "Tower output contract differs")
    rows = {row["id"]: row for row in report["datasets"]}
    require(len(report["datasets"]) == len(rows) == 2 and set(rows) == set(DATASETS), "Incomplete/duplicate device datasets")
    features, provenance = {}, {}
    for name, expected in datasets.items():
        row = rows[name]
        if "status" in row:
            require(row["status"] == "passed", "Incomplete dataset export")
        require(all(row[k] == expected[k] for k in ("image_file", "image_sha256", "image_count", "tokens_file", "tokens_sha256", "token_count")), "Device source tensor hashes/counts differ")
        features[name], provenance[name] = {}, {}
        for tower, count in (("image", expected["image_count"]), ("text", expected["token_count"])):
            features[name][tower], provenance[name][tower] = load_features(root, row[tower], f"{model}-{name}-{tower}.f32", count)
    return report, features, {"report": path.name, "sha256": digest(path), "outputs": provenance}


def host_comparison(host_row, phone_row, labels, samples):
    pair = comparisons({"host": host_row, "device": phone_row}, labels)[0]
    first, second = np.asarray(host_row["predicted_class"]), np.asarray(phone_row["predicted_class"])
    flipped = np.flatnonzero(first != second)
    return {"device_minus_host_pp": {key: (phone_row[key] - host_row[key]) * 100 for key in ("top1", "top5", "class_query_p_at_10", "class_query_map")},
            "paired_top1": pair, "prediction_flip_count": len(flipped), "prediction_flip_rate": len(flipped) / len(labels),
            "prediction_flips": [{"sample_id": samples[i]["id"], "position": int(i), "label": int(labels[i]),
                                   "host_class": int(first[i]), "device_class": int(second[i])} for i in flipped]}


def parity_context(path, models):
    if path is None:
        return None
    data = read(path)
    require(data["status"] in ("passed", "failed"), "Incomplete first-input parity report")
    for name, sha in data["input_report_sha256"].items():
        require(digest(local_file(path.parent, name)) == sha, "Parity source report hash differs")
    rows = []
    for row in data["results"]:
        if row["model_id"] not in MODELS:
            continue
        for tower in ("image", "text"):
            result = row["towers"][tower]
            require(result["model_sha256"] == models[row["model_id"]][f"{tower}_sha256"], "First-input parity used a different model")
            cosine, threshold = result["cosine"], result["cosine_minimum"]
            require(math.isfinite(cosine) and -1.000001 <= cosine <= 1.000001 and -1 <= threshold <= 1, "Invalid parity cosine/threshold")
            require(result["status"] == ("passed" if cosine >= threshold else "failed"), "Parity result disagrees with its threshold")
        rows.append({"model_id": row["model_id"], "round_id": row["round_id"], "towers": row["towers"]})
    require({r["model_id"] for r in rows} == set(MODELS), "Parity report lacks the selected models")
    return {"file": str(path), "sha256": digest(path), "status": data["status"], "rows": rows,
            "scope": data["scope"], "interpretation": "A strict first-vector parity failure is not an accuracy acceptance decision; platform-specific numerical behavior is measured separately by full-dataset predictions."}


def validate_run(root, manifest_sha):
    metadata = read(root / "run-metadata.json")
    require(metadata["status"] == "complete", "Device accuracy run is incomplete")
    require(metadata["manifest_sha256"] == manifest_sha, "Run metadata refers to a different input manifest")
    runs = {row["model_id"]: row for row in metadata["runs"]}
    require(len(metadata["runs"]) == len(runs) == 2 and set(runs) == set(MODELS), "Run metadata must contain exactly two model runs")
    for model, run in runs.items():
        require(run["status"] == "passed" and run["instrumentation_returncode"] == 0, "Device instrumentation did not complete successfully")
        require(run["report_file"] == f"{model}-report.json", "Unexpected report filename in run metadata")
        require(digest(local_file(root, run["report_file"])) == run["report_sha256"], "Device report differs from the completed run")
    require(metadata["device"]["fingerprint"], "Missing run device fingerprint")
    return metadata, runs


def validate_shared_text_outputs(export_reports):
    """Identical model, class tokens and device configuration must share text outputs."""
    for name in DATASETS:
        first, second = (export_reports[model]["outputs"][name]["text"] for model in MODELS)
        require(first["sha256"] == second["sha256"] and first["shape"] == second["shape"],
                f"Shared text output bytes differ for {name}")
    return True


def evaluate(args):
    manifest_path = args.input / "manifest.json"
    manifest, evaluation, host = read(manifest_path), read(args.evaluation_manifest), read(args.host)
    run_metadata, runs = validate_run(args.input, digest(manifest_path))
    models, datasets, input_checks = validate_inputs(args, manifest, evaluation, host)
    result = {"status": "passed", "status_scope": "Complete artifact integrity and accuracy computation, not a model-quality acceptance threshold.",
              "protocol": PROTOCOL, "seed": SEED, "onnxruntime_version": RUNTIME, "cpu_threads": 4,
              "numpy_version": np.__version__, "metric_implementation_sha256": digest(Path(__file__).with_name("evaluate_accuracy.py")),
              "evaluator_sha256": digest(Path(__file__)),
              "source_sha256": {"manifest": digest(manifest_path), "evaluation_manifest": digest(args.evaluation_manifest), "host_accuracy": digest(args.host),
                                "run_metadata": digest(args.input / "run-metadata.json")},
              "run_metadata": run_metadata,
              "models": models, "input_checks": input_checks, "export_reports": {}, "datasets": {},
              "scope": "Both image and text embeddings computed on the phone for every evaluation input; cosine scoring and metrics computed on the host with the existing protocol. Export duration is not benchmark latency."}
    device_identity = None
    for model in MODELS:
        report, features, provenance = validate_report(args.input, model, models[model], datasets,
                                                       result["source_sha256"]["manifest"], result["source_sha256"]["evaluation_manifest"])
        identity = {k: report[k] for k in ("device", "manufacturer", "sdk", "abis", "fingerprint")}
        require(device_identity is None or identity == device_identity, "Models were exported on different devices")
        require(identity["fingerprint"] == run_metadata["device"]["fingerprint"]
                and identity["sdk"] == int(run_metadata["device"]["sdk"])
                and identity["device"] == run_metadata["device"]["model"], "Export device differs from run metadata")
        receipts = {row["file"]: row for row in runs[model]["outputs"]}
        expected_files = {item["file"] for row in report["datasets"] for item in (row["image"], row["text"])}
        require(len(runs[model]["outputs"]) == len(receipts) == 4 and set(receipts) == expected_files, "Run has incomplete/duplicate feature retrieval receipts")
        for row in report["datasets"]:
            for tower in ("image", "text"):
                record = row[tower]
                require(all(receipts[record["file"]][k] == record[k] for k in ("sha256", "bytes", "shape", "count")), "Run retrieval receipt differs from device output")
        device_identity = identity
        result["export_reports"][model] = provenance
        for name, matrices in features.items():
            labels = np.asarray([s["label"] for s in evaluation[name]["samples"]])
            scores = matrices["image"] @ matrices["text"].T
            row = metrics(scores, labels, evaluation[name]["classes"])
            bundle = result["datasets"].setdefault(name, {"metrics": {}, "host_device_comparisons": {}})
            bundle["metrics"][model] = row
            bundle["host_device_comparisons"][model] = host_comparison(host["datasets"][name]["metrics"][model], row, labels, evaluation[name]["samples"])
    result["device"] = device_identity
    result["shared_text_outputs_byte_identical"] = validate_shared_text_outputs(result["export_reports"])
    for name, bundle in result["datasets"].items():
        labels = np.asarray([s["label"] for s in evaluation[name]["samples"]])
        bundle["comparisons"] = comparisons(bundle["metrics"], labels)
        first, second = [np.asarray(bundle["metrics"][m]["predicted_class"]) for m in MODELS]
        bundle["fp32_int8_prediction_flip_count"] = int((first != second).sum())
    result["first_input_parity"] = parity_context(args.parity, models)
    return result, host


def render(result, host, args, output, language="en"):
    require(language in ("en", "zh"), "Unsupported report language")
    def t(english, chinese):
        return english if language == "en" else chinese

    labels = LABELS if language == "zh" else {
        FP32: "FP32 image + dynamic INT8 text", INT8: "Mixed INT8 image + same dynamic INT8 text",
    }
    device = result["device"]
    counterpart = output.with_name(output.stem + "_zh.md") if language == "en" else output
    lines = [t(f"# {device['device']} full-dataset accuracy", f"# {device['device']} 完整数据集识别精度"), "",
             link(t("简体中文", "English"), counterpart, output), "",
             t(f"Both image configurations computed all image and class-text embeddings on {device['device']} (Android API {device['sdk']}, ARM64, ONNX Runtime {RUNTIME}, CPU 4 threads).",
               f"两个图像配置均在 {device['device']}（Android API {device['sdk']}、ARM64、ONNX Runtime {RUNTIME}、CPU 4 线程）上完成全部图像及类别文本的特征计算。"),
             t("Each model covers 2,000 CIFAR-100 test images and all 3,925 Imagenette validation images, 5,925 images total, with 100 and 10 class texts respectively. The phone embeddings were transferred to the host for L2 normalization, cosine ranking and metrics using the existing protocol. Host embeddings do not substitute for phone outputs.", "每模型覆盖 CIFAR-100 test 2,000 张和 Imagenette val 3,925 张，共 5,925 张；类别文本分别为 100 条和 10 条。特征拉回主机后按原协议 L2 归一化、计算余弦排序和指标。没有用主机特征替代手机结果。"), "",
             t("## Phone accuracy", "## 手机准确率"), "",
             t("| Dataset | Image configuration | Top1 | Top5 | Class-query P@10 | Class-query mAP |", "| 数据集 | 图像配置 | Top1 | Top5 | 类别查询 P@10 | 类别查询 mAP |"), "|---|---|---:|---:|---:|---:|"]
    for name, bundle in result["datasets"].items():
        for model in MODELS:
            row = bundle["metrics"][model]
            cells = [f"{row[k]*100:.2f}%" for k in ("top1", "top5", "class_query_p_at_10", "class_query_map")]
            lines.append(f"| {name} | {labels[model]} | " + " | ".join(cells) + " |")
    lines += ["", t("Both configurations share the same dynamic INT8 text model and preprocessed inputs. Their exported text feature bytes are identical for both datasets. The mixed INT8 image configuration retains sensitive Conv operations in FP32. P@10/mAP query all images with class text and average over classes; they are not free-caption–image Recall@K.", "两配置共用同一动态 INT8 文本模型和预处理输入，两个数据集的导出文本特征均逐字节一致。混合 INT8 图像配置保留敏感 Conv 的 FP32 计算。P@10/mAP 是类别文本对完整图片集合的检索指标，按类别平均，不是图片与自由描述配对的 Recall@K。"), "",
              t("## Phone mixed INT8 versus FP32", "## 手机 FP32 与混合 INT8 的差值"), "",
              t("Differences are mixed INT8 − FP32, in percentage points (pp). Paired bootstrap resamples the same images 2,000 times (seed 20260913); 95% intervals describe the current dataset distributions only.", "差值为混合 INT8 − FP32，单位百分点（pp）。对相同图片做 2,000 次成对 bootstrap（seed 20260913）；95% CI 仅描述当前数据分布。"), "",
              t("| Dataset | Top1 delta pp | 95% CI pp | FP32-only / INT8-only correct | Prediction flips |", "| 数据集 | Top1 差值 pp | 95% CI pp | FP32 独对 / INT8 独对 | 预测翻转数 |"), "|---|---:|---:|---:|---:|"]
    for name, bundle in result["datasets"].items():
        pair = bundle["comparisons"][0]
        low, high = pair["paired_bootstrap_95ci_pp"]
        lines.append(f"| {name} | {pair['b_minus_a_top1_pp']:+.2f} | [{low:+.2f}, {high:+.2f}] | {pair['a_only_correct']} / {pair['b_only_correct']} | {bundle['fp32_int8_prediction_flip_count']} |")
    lines += ["", t("## Same model: host versus phone", "## 同一模型：主机与手机"), "",
              t(f"Host: {host['host']}. Both platforms use ONNX Runtime {RUNTIME}, 4 threads and the same models/images/class texts. Differences below are phone − host. Per-image flip records contain sample IDs, ground truth and both predictions.", f"主机环境：{host['host']}；同用 ONNX Runtime {RUNTIME}、4 线程及相同模型/图片/类别文本。下表差值为手机 − 主机，逐图翻转记录包含样本 ID、真值和两端预测。"), "",
              t("| Dataset | Configuration | Host / phone Top1 | Top1 delta pp | Paired 95% CI pp | Prediction flips / rate |", "| 数据集 | 配置 | 主机 / 手机 Top1 | Top1 差值 pp | 配对 95% CI pp | 预测翻转数 / 比例 |"), "|---|---|---:|---:|---:|---:|"]
    for name, bundle in result["datasets"].items():
        for model, comparison in bundle["host_device_comparisons"].items():
            a, b = host["datasets"][name]["metrics"][model]["top1"], bundle["metrics"][model]["top1"]
            low, high = comparison["paired_top1"]["paired_bootstrap_95ci_pp"]
            lines.append(f"| {name} | {labels[model]} | {a*100:.2f}% / {b*100:.2f}% | {comparison['device_minus_host_pp']['top1']:+.2f} | [{low:+.2f}, {high:+.2f}] | {comparison['prediction_flip_count']} / {comparison['prediction_flip_rate']*100:.2f}% |")
    lines += ["", t("JSON also includes cross-platform Top5/P@10/mAP deltas and per-image flips. Flips may correct a prediction, make it wrong, or change between wrong classes. Vector cosine cannot replace accuracy measured against ground truth.", "Top5、P@10、mAP 的跨平台差值及逐图翻转明细同时保存在 JSON 中。预测翻转可能纠错，也可能由对变错或两端都错；向量余弦相似度不能代替有真值的准确率。")]
    parity = result["first_input_parity"]
    if parity:
        failed = [r["towers"]["image"] for r in parity["rows"] if r["model_id"] == INT8 and r["towers"]["image"]["status"] == "failed"]
        if failed:
            lines += ["", t(f"The earlier first-image host/phone cosine for this mixed model reached a minimum of {min(r['cosine'] for r in failed):.8f}, below that check's {failed[0]['cosine_minimum']:.4f} threshold.", f"此前同一混合模型的主机/手机首图 cosine 最低为 {min(r['cosine'] for r in failed):.8f}，未达到该检查的 {failed[0]['cosine_minimum']:.4f} 阈值。"),
                      t("This establishes cross-platform numerical differences, but a first-vector check alone cannot establish recognition failure or identify the responsible kernel. Full phone-dataset accuracy measures the actual effect here.", "这确认了跨平台数值差异，尚不能单凭首向量断定识别质量失败或定位具体内核原因；本报告使用手机完整数据集结果直接度量其实际影响。")]
        lines += ["", link(t("Earlier first-input platform parity", "此前首输入跨平台数值检查"), Path(parity["file"]), output) + t(" uses a small set of benchmark inputs, separate from the full evaluation datasets.", "。该检查使用少量 benchmark 输入，与本次完整评测样本范围不同。")]
    lines += ["", t("## Validation and scope", "## 验证与范围"), "",
              t("SHA256 links model files, export manifests, evaluation samples and host results. Device model/input hashes match the manifest. All eight retrieved feature files are checked for byte length, shape and SHA256; every vector is finite and nonzero. Text input hashes are reproduced from the same class prompts, and shared-text output hashes match byte for byte.", "模型文件、导出 manifest、评测样本清单与主机结果通过 SHA256 关联；手机记录的实际模型/输入哈希与清单一致，拉取的八个特征文件重新校验字节数、形状和 SHA256，全部特征检查有限且非零。文本输入哈希由相同类别提示词重新生成，共享文本输出哈希逐字节一致。"),
              (t("Original host image/token input files were also rehashed sequentially.", "原始主机图像与 token 输入文件也已顺序重算 SHA256。") if result["input_checks"]["source_tensor_bytes_rehashed"] else t("The large original inputs were not reread at this step; input validation uses verified device reports and the preparation manifest.", "原始大输入文件未在此步骤再次读取；输入验证依据已通过校验的手机报告与准备清单。")),
              t("The fixed English prompt is `a photo of a {class}`. No evaluation samples were used for calibration, tuning or prompt selection. CIFAR has 32×32 source images and Imagenette is a 10-class subset. Results do not cover personal photo libraries, Chinese queries, OCR or free-caption retrieval.", "固定英文单模板 `a photo of a {class}`，未在评测集校准、调参或选择提示词。CIFAR 原图为 32×32，Imagenette 为 10 类子集；结果不覆盖真实相册、中文查询、OCR 或自由描述检索。"),
              t("This is one complete feature export for accuracy evaluation. Export I/O, hashing and total execution duration are not inference-speed measurements. Warmed short-benchmark P50/P95 are reported separately and must not be pooled with export durations.", "这是单次完整特征导出的准确率验证。导出过程的读写、校验与执行总时长不作为推理速度数据；短时 benchmark 的预热后 P50/P95 仍单独报告，不能混合计算。"), "",
              t("This run's local JSON retains measurements, per-image predictions, pairing statistics and hashes. Keep generated reports, feature arrays and model/input files under ignored build/ directories. Git retains curated aggregate summaries; reproducing complete cosine rankings requires regenerating the phone feature files.", "本次本地 JSON 保留测量值、逐图预测、配对统计和哈希。生成的报告、特征数组及模型/输入文件应放在 Git 忽略的 build/ 目录。仓库仅保留整理后的聚合摘要；复核完整余弦排序需要重新生成手机特征文件。"), "",
              link(t("Full metrics, predictions and validation", "手机完整指标、逐图预测、配对统计与文件校验"), output.with_name("device-accuracy.json"), output) + " · " + link(t("Run record", "完整执行记录"), args.input / "run-metadata.json", output) + " · " + link(t("Phone input manifest", "手机输入清单"), args.input / "manifest.json", output) + " · " + link(t("Evaluation IDs, labels and hashes", "评测样本 ID/标签/哈希"), args.evaluation_manifest, output) + " · " + link(t("Host accuracy", "主机准确率"), args.host, output), ""]
    return "\n".join(lines)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", type=Path, default=Path("build/mobileclip2-image-int8-results/pixel8a-accuracy"))
    parser.add_argument("--host", type=Path, default=Path("build/mobileclip2-image-int8-results/accuracy.json"))
    parser.add_argument("--evaluation-manifest", type=Path, default=Path("build/mobileclip2-image-int8-results/accuracy-manifest.json"))
    parser.add_argument("--source-input-dir", type=Path, help="Rehash original preprocessed images/tokens without pulling them from the phone")
    parser.add_argument("--parity", type=Path, help="Prior first-input host/device parity report; failed numerical parity does not fail this evaluation")
    args = parser.parse_args()
    if args.parity is None:
        candidate = args.input.parent / "pixel8a" / "host-device-parity.json"
        args.parity = candidate if candidate.is_file() else None
    result, host = evaluate(args)
    output = args.input / "README.md"
    english = render(result, host, args, output, "en")
    chinese = render(result, host, args, output, "zh")
    validate_bilingual_tables(english, chinese)
    result["bilingual_tables_verified"] = True
    (args.input / "device-accuracy.json").write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    write_bilingual(output, english, chinese)
    print(f"Verified complete phone embeddings for 2 x 5,925 images; wrote {output}")


if __name__ == "__main__":
    main()
