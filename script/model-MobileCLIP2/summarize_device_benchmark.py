#!/usr/bin/env python3
"""Validate Android measurements and render the physical-device speed comparison."""
import argparse
import hashlib
import json
import math
import re
from pathlib import Path


LABELS = {
    "v1_s0_onnx_int8": "MobileCLIP v1 S0 · ONNX",
    "v1_s2_onnx_int8": "MobileCLIP v1 S2 · ONNX",
    "v2_s0_onnx_int8": "MobileCLIP2 S0 · ONNX",
    "v2_s0_tflite_int8": "MobileCLIP2 S0 · TFLite",
    "legacy_clip_int8": "历史 CLIP · ORT（双 INT8）",
}


def read(path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def stats(values):
    assert values and all(math.isfinite(v) and v > 0 for v in values)
    ordered = sorted(values)
    return {
        "count": len(values), "mean_ms": sum(values) / len(values),
        "min_ms": ordered[0], "max_ms": ordered[-1],
        **{f"p{p}_ms": ordered[math.ceil(len(values) * p / 100) - 1] for p in (50, 95, 99)},
    }


def verify_stats(row):
    expected = stats(row["raw_samples_ms"])
    assert all(math.isclose(v, row["summary"][k], rel_tol=1e-9, abs_tol=1e-7) for k, v in expected.items())


def validate_manifest(manifest, reference_path=None):
    """Validate format provenance before accepting a new, independently scoped run."""
    rows = manifest["models"]
    fixtures = {m["id"]: m for m in rows}
    assert rows and len(fixtures) == len(rows), "Duplicate or missing model IDs"
    assert len(manifest["prompts"]) == manifest["token_count"] > 0
    assert re.fullmatch(r"[0-9a-f]{64}", manifest["tokens_sha256"])
    reference = read(reference_path) if reference_path else None
    converted = []
    for row in rows:
        model_format = row["format"]
        assert model_format in ("onnx", "ort", "tflite")
        assert row["backend"] == ("tflite" if model_format == "tflite" else "onnx")
        assert row["image_count"] > 0 and row["input_size"] == row["preprocess"]["size"]
        assert re.fullmatch(r"[0-9a-f]{64}", row["image_input_sha256"])
        for tower in ("image", "text"):
            assert row[f"{tower}_file"].endswith("." + model_format)
            assert re.fullmatch(r"[0-9a-f]{64}", row[f"{tower}_sha256"])
            assert row[f"{tower}_file_bytes"] > 0
        source_id = row.get("source_model_id", row["id"])
        assert source_id in fixtures, "Missing format-comparison source model"
        if source_id == row["id"]:
            continue
        source = fixtures[source_id]
        converted.append(row["id"])
        assert model_format == "ort" and source["format"] == "onnx"
        assert row["preprocess"] == source["preprocess"]
        assert row["image_count"] == source["image_count"]
        assert row["image_input_sha256"] == source["image_input_sha256"], "Format inputs differ"
        export = row["export_metadata"]
        assert export["status"] == "passed" and export["source_model_id"] == source_id
        assert export.get("onnxruntime_version"), "Missing export runtime version"
        assert re.fullmatch(r"[0-9a-f]{64}", row["export_metadata_sha256"])
        if reference is not None:
            assert export == reference, "Reference metadata differs from the prepared manifest"
            assert row["export_metadata_sha256"] == hashlib.sha256(reference_path.read_bytes()).hexdigest()
        for tower in ("image", "text"):
            record = export[tower]
            assert record["file"] == row[f"{tower}_file"]
            assert record["sha256"] == row[f"{tower}_sha256"]
            assert record["source_file"] == source[f"{tower}_file"]
            assert record["source_sha256"] == source[f"{tower}_sha256"] == row[f"source_{tower}_sha256"]
    assert converted, "The format manifest must include an ORT model and its ONNX source"
    return fixtures


def compare_format_outputs(grouped, fixtures):
    comparisons = {}
    for model, fixture in fixtures.items():
        source_id = fixture.get("source_model_id", model)
        if source_id == model:
            continue
        sources = {row["round_id"]: row for row in grouped[source_id]}
        rounds = []
        for row in grouped[model]:
            source = sources[row["round_id"]]
            assert row["runtime_version"] == source["runtime_version"] and row["runtime_version"], "Format runtimes differ"
            assert row["cpu_threads"] == source["cpu_threads"] == 4, "Format thread counts differ"
            assert row["image_input_sha256"] == source["image_input_sha256"]
            assert row["tokens_sha256"] == source["tokens_sha256"]
            comparison = {"round_id": row["round_id"]}
            for tower in ("image", "text"):
                a, b = row[tower]["first_output"], source[tower]["first_output"]
                assert len(a) == len(b) == 512 and all(math.isfinite(v) for v in a + b)
                norm = math.sqrt(sum(v * v for v in a) * sum(v * v for v in b))
                assert norm > 0, "Zero format-comparison embedding"
                cosine = sum(x * y for x, y in zip(a, b)) / norm
                max_error = max(abs(x - y) for x, y in zip(a, b))
                assert cosine >= 0.99999 and max_error <= 1e-4, f"ORT conversion parity failed: {model}/{tower}"
                comparison[tower] = {"cosine": cosine, "max_absolute_error": max_error}
            rounds.append(comparison)
        comparisons[model] = {
            "source_model_id": source_id, "rounds": rounds,
            "scope": "First image and first text only, same fixture and runtime in each round; not dataset accuracy.",
            "minimum_cosine": 0.99999, "maximum_absolute_error": 1e-4,
        }
    return comparisons


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", type=Path, default=Path("script/model-MobileCLIP2/results/pixel8a-speed"))
    parser.add_argument("--fixtures", type=Path, default=Path("build/mobileclip-device-benchmark/manifest.json"))
    parser.add_argument("--accuracy", type=Path, default=Path("script/model-MobileCLIP2/results/accuracy-results.json"))
    parser.add_argument("--manifest", type=Path, help="Use a format-comparison manifest instead of the historical five-model accuracy protocol")
    parser.add_argument("--reference", type=Path, help="Verify original ORT export metadata against the manifest (requires --manifest)")
    args = parser.parse_args()
    if args.reference and not args.manifest:
        parser.error("--reference requires --manifest")
    root = args.input
    metadata = read(root / "run-metadata.json")
    assert metadata.get("status") == "complete", "The benchmark is incomplete"
    fixture_manifest = read(args.manifest or args.fixtures)
    fixtures = (validate_manifest(fixture_manifest, args.reference) if args.manifest
                else {m["id"]: m for m in fixture_manifest["models"]})
    accuracy = None if args.manifest else read(args.accuracy)["models"]
    labels = {model: row["label"] for model, row in fixtures.items()} if args.manifest else LABELS
    assert metadata["rounds"] > 0 and metadata["sample_count"] > 0 and metadata["warmup_count"] >= 0
    expected_keys = {(model, r) for model in labels for r in range(1, metadata["rounds"] + 1)}
    actual_keys = [(r["model"], r["round"]) for r in metadata["runs"]]
    assert len(actual_keys) == len(set(actual_keys)) and set(actual_keys) == expected_keys
    grouped = {model: [] for model in labels}
    for run in metadata["runs"]:
        report_path = root / run["report"]
        assert report_path.resolve().is_relative_to(root.resolve())
        row = read(report_path)
        model = run["model"]
        fixture = fixtures[model]
        assert row["status"] == "passed" and row["model_id"] == model and row["round_id"] == f"r{run['round']}"
        assert row["cpu_threads"] == 4 and row["fingerprint"] == metadata["fingerprint"]
        assert row["backend"] == fixture["backend"] and row["input_size"] == fixture["input_size"]
        assert row["model_hashes_verified"] and row["input_hashes_verified"]
        assert row["tokens_sha256"] == fixture_manifest["tokens_sha256"]
        assert row["image_input_sha256"] == fixture["image_input_sha256"]
        if args.manifest:
            assert row.get("runtime_version"), "Missing device runtime version"
            assert row["sample_count"] == metadata["sample_count"] and row["warmup_count"] == metadata["warmup_count"]
            assert row["preloaded_image_count"] == fixture["image_count"]
            assert row["preloaded_text_count"] == fixture_manifest["token_count"]
        for tower in ("image", "text"):
            assert row[f"actual_{tower}_sha256"] == fixture[f"{tower}_sha256"]
            if accuracy is not None:
                assert row[f"actual_{tower}_sha256"] == accuracy[model][f"{tower}_sha256"]
            else:
                assert row[f"{tower}_file_bytes"] == fixture[f"{tower}_file_bytes"]
                assert all(math.isfinite(row[tower][k]) and row[tower][k] > 0 for k in ("load_ms", "first_invoke_ms"))
            verify_stats(row[tower])
            assert row[tower]["summary"]["count"] == metadata["sample_count"]
            assert row[tower]["validated_output_count"] == 1 + metadata["warmup_count"] + metadata["sample_count"]
        sustained = row["sustained_image"]
        if run["round"] == 1 and metadata["sustain_seconds_first_round"] > 0:
            assert sustained["actual_elapsed_s"] >= metadata["sustain_seconds_first_round"]
            assert sum(s["count"] for s in sustained["segments"]) == sustained["count"]
            assert math.isclose(sustained["images_per_second"], sustained["count"] / sustained["actual_elapsed_s"])
        else:
            assert sustained["skipped"]
        grouped[model].append(row)

    summary = {"metadata": metadata, "percentile_method": "nearest rank", "models": {}}
    if args.manifest:
        runtime_versions = {}
        for model, rows in grouped.items():
            runtime_versions.setdefault(fixtures[model]["backend"], set()).update(r["runtime_version"] for r in rows)
        assert all(len(versions) == 1 for versions in runtime_versions.values()), "Device runtime versions differ"
        summary["format_comparisons"] = compare_format_outputs(grouped, fixtures)
    for model, rows in grouped.items():
        health = [h for row in rows for h in row["health_samples"]]
        temperatures = [h["battery_temperature_c"] for h in health if h["battery_temperature_c"] is not None]
        item = {
            "label": labels[model], "runtime_versions": sorted({row["runtime_version"] for row in rows}),
            "image_sha256": rows[0]["actual_image_sha256"], "text_sha256": rows[0]["actual_text_sha256"],
            "temperature_c_range": [min(temperatures), max(temperatures)],
            "thermal_statuses": sorted({h["thermal_status"] for h in health}),
            "battery_plugged_values": sorted({h["battery_plugged"] for h in health}),
            "max_observed_single_tower_process_pss_mib": max(h["sampled_total_pss_kb"] for h in health) / 1024,
            "sustained_image": next((r["sustained_image"] for r in rows if not r["sustained_image"].get("skipped")), None),
        }
        if args.manifest:
            item.update({"format": fixtures[model]["format"],
                         "source_model_id": fixtures[model].get("source_model_id", model),
                         "image_bytes": rows[0]["image_file_bytes"], "text_bytes": rows[0]["text_file_bytes"]})
        for tower in ("image", "text"):
            item[tower] = stats([v for row in rows for v in row[tower]["raw_samples_ms"]])
            item[tower]["round_p50_ms"] = [r[tower]["summary"]["p50_ms"] for r in rows]
            item[tower]["load_ms"] = [r[tower]["load_ms"] for r in rows]
            item[tower]["first_invoke_ms"] = [r[tower]["first_invoke_ms"] for r in rows]
        summary["models"][model] = item
    (root / "summary.json").write_text(json.dumps(summary, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    # Preserve the exact input manifest alongside the report, even if build/ is later cleared.
    (root / "fixture-manifest.json").write_text(json.dumps(fixture_manifest, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    lines = [
        f"# {metadata['model']} 真机速度对比", "",
        ("同代 ONNX/ORT 格式比较和跨代部署模型比较分别解释；所有模型使用本次记录中的运行库。" if args.manifest else
         "更正：下表保留原 App 配置的真实测量值。后续[线程诊断](../pixel8a-tflite-diagnostics/诊断报告.md)确认，这个 LiteRT 1.4.1 Java 路径的 XNNPACK 实际近似单线程；CPU 4 线程仅是请求值，不能据此得出同等四线程下 TFLite 慢四倍。修正线程传递的实验结果请看诊断报告。"), "",
        f"设备：{metadata['model']} / {metadata['soc']} / Android {metadata['android']}，ARM64；请求 CPU 4 线程，batch=1，无 GPU/NPU delegate。",
        f"运行区间（UTC）：{metadata['started_utc']} 至 {metadata['completed_utc']}。",
        f"每种模型运行 {metadata['rounds']} 轮，每塔每轮单列首次执行、预热 {metadata['warmup_count']} 次、记录 {metadata['sample_count']} 次；第二轮反向排序。",
        f"第一轮每种模型额外连续跑图片 {metadata['sustain_seconds_first_round']} 秒，两次模型测试之间静置 {metadata['cooldown_seconds']} 秒。", "",
        "## 预热后耗时", "",
        "合并各轮的原始样本，以 nearest rank 计算 P50 / P95；单位毫秒。计时包含预加载输入拷贝、完整模型图执行和 512 维输出拷贝，不含读图、缩放、分词和数据库检索。", "",
        "| 模型 | 图片 P50 / P95 | 文本 P50 / P95 | 各轮图片 P50 | 各轮文本 P50 |",
        "|---|---:|---:|---:|---:|",
    ]
    for item in summary["models"].values():
        image, text = item["image"], item["text"]
        rounds = lambda v: " / ".join(f"{x:.2f}" for x in v)
        lines.append(f"| {item['label']} | {image['p50_ms']:.2f} / {image['p95_ms']:.2f} | {text['p50_ms']:.2f} / {text['p95_ms']:.2f} | {rounds(image['round_p50_ms'])} | {rounds(text['round_p50_ms'])} |")
    models = summary["models"]
    if args.manifest:
        lines += ["", "## 同源 ONNX / ORT", "", "| 格式对 | ONNX / ORT 图片 P50 比 | ONNX / ORT 文本 P50 比 | 首输入最低 cosine（图 / 文） |", "|---|---:|---:|---:|"]
        for model, comparison in summary["format_comparisons"].items():
            source, converted = models[comparison["source_model_id"]], models[model]
            cosines = [min(r[t]["cosine"] for r in comparison["rounds"]) for t in ("image", "text")]
            lines.append(f"| {source['label']} → ORT | {source['image']['p50_ms']/converted['image']['p50_ms']:.3f} | {source['text']['p50_ms']/converted['text']['p50_ms']:.3f} | {cosines[0]:.8f} / {cosines[1]:.8f} |")
        lines += ["", "比值大于 1 表示 ORT 更快。数值校验仅覆盖每轮首张图片和首条文本，不是数据集识别准确率；完整导出校验见清单中的 export_metadata。"]
    else:
        onnx, tflite, v1, s2 = [models[m] for m in ("v2_s0_onnx_int8", "v2_s0_tflite_int8", "v1_s0_onnx_int8", "v1_s2_onnx_int8")]
        lines += ["", f"同为 v2 S0，ONNX/TFLite 图片耗时比 {onnx['image']['p50_ms']/tflite['image']['p50_ms']:.2f}，文本耗时比 {onnx['text']['p50_ms']/tflite['text']['p50_ms']:.2f}。",
                  f"同用 ONNX，v2 S0/v1 S0 图片耗时比 {onnx['image']['p50_ms']/v1['image']['p50_ms']:.2f}，文本耗时比 {onnx['text']['p50_ms']/v1['text']['p50_ms']:.2f}；v1 S2/v1 S0 图片耗时比 {s2['image']['p50_ms']/v1['image']['p50_ms']:.2f}。"]
    lines += ["", "## 持续运行和温度", "",
              "| 模型 | 连续图片吞吐（张/秒） | 首/末 15 秒图片 P50 ms | 电池温度范围 °C | 系统 thermal status | 最大观测进程 PSS MiB |",
              "|---|---:|---:|---:|---:|---:|"]
    for item in models.values():
        sustained = item["sustained_image"]
        segments = sustained["segments"] if sustained else []
        # A final subsecond remainder is not representative of the last 15-second window.
        full = [s for s in segments if s["elapsed_s"] >= 10]
        segment_label = f"{full[0]['p50_ms']:.2f} / {full[-1]['p50_ms']:.2f}" if full else "—"
        throughput = f"{sustained['images_per_second']:.2f}" if sustained else "—"
        low, high = item["temperature_c_range"]
        lines.append(f"| {item['label']} | {throughput} | {segment_label} | {low:.1f}–{high:.1f} | {item['thermal_statuses']} | {item['max_observed_single_tower_process_pss_mib']:.1f} |")
    lines += ["", "连续吞吐按整段墙钟时间计算，包含每次输出检查和每约 10 秒的状态采样开销；它不同于推理耗时的倒数。持续负载只在第一轮各跑一次，没有反序复测，不能据此断言热稳态排名。电池温度不是 CPU 核心温度，thermal status 也不能排除 CPU 动态降频。60 秒不足以代表长时间相册索引的热稳态。",
              "进程 PSS 仅在阶段边界和连续测试中定期采样，不是峰值；每个塔关闭后才打开下一个塔，也不是 App 同时驻留图像和文本模型时的内存。", "",
              "## 加载和首次执行", "", "每行展示各轮值（ms）。加载前已读取模型校验 SHA256，文件系统缓存可能已热，因此这些数字不能称为冷磁盘启动耗时。", "",
              "| 模型 | 图片加载 | 图片首次执行 | 文本加载 | 文本首次执行 |", "|---|---:|---:|---:|---:|"]
    for item in models.values():
        cells = [" / ".join(f"{x:.2f}" for x in item[t][k]) for t in ("image", "text") for k in ("load_ms", "first_invoke_ms")]
        lines.append(f"| {item['label']} | " + " | ".join(cells) + " |")
    if args.manifest:
        lines += ["", "| 模型 | 图像模型 MiB | 文本模型 MiB | 合计 MiB |", "|---|---:|---:|---:|"]
        for item in models.values():
            lines.append(f"| {item['label']} | {item['image_bytes']/2**20:.2f} | {item['text_bytes']/2**20:.2f} | {(item['image_bytes']+item['text_bytes'])/2**20:.2f} |")
    lines += ["", "## 解释范围和验证", "",
              "- MobileCLIP 四组沿用 FP32 图像 + INT8 文本的部署配置；历史 CLIP 为图像和文本双 INT8、224 输入，保留旧 App 预处理。因此这是实际部署组合对比，并非同架构同精度的算子赛跑。",
              "- 五张公开图片及 13 条文本循环输入，均与主机比较使用的预处理和 tokenizer 一致；未读取手机私人相册。",
              ("- 模型及输入文件 SHA256 均在手机上核对并与本次清单匹配；转换模型与源 ONNX 的哈希关系经过导出 metadata 校验。全部预热与正式输出均检查为 512 个有限且非全零的数值，本次未重跑识别准确率数据集。" if args.manifest else
               "- 五组模型及输入文件 SHA256 均在手机上核对；模型哈希与先前识别精度评估一致。全部预热与正式输出均检查为 512 个有限且非全零的数值。本次没有在手机重跑识别准确率数据集。"),
              "- 一个测试 APK 内直接调用 App 已有的 ONNX Runtime / LiteRT Android 库；每种模型新建 App 进程，两个模型塔依次释放；无并发模型推理。",
              "- USB 连接、充电和屏幕状态未人为调整；未经锁频或绑核。顺序反转及各轮单列有助于观察漂移，但不能完全消除温度、调度和后台任务影响。",
              "- 这是已预热的编码开销，不能直接当作首次搜索延迟、整张照片索引时间或完整 App 的端到端响应时间。首次加载/执行见上表。",
              "- v1 S0/S2 来自官方重新导出的模型；历史 App 的 MobileCLIP v1 二进制缺失，不能声称复现其完全相同的旧运行文件。",
              f"- 系统为 Android {metadata['android']}；本机实际 page size 为 {metadata['page_size']} 字节，完整系统指纹见运行元数据。所有正式测试均成功执行，未为本次测量更换运行库。", "",
              "运行库：" + "；".join(f"{item['label']}: {', '.join(item['runtime_versions'])}" for item in models.values()) + "。", "",
              "[汇总及校验结果](summary.json) · [设备和执行顺序](run-metadata.json) · [模型和输入清单](fixture-manifest.json)"
              + (" · [构建验证](build-verification.json)" if (root / "build-verification.json").exists() else "")
              + "。同目录逐模型 `*-r*.json` 保存原始样本、热状态和加载信息，`*-instrumentation.log` 保存测试通过证据。",
              ("本次为导出格式与跨代速度验证；有标签精度数据集未重跑。" if args.manifest else
               "此前的[识别精度对比](../识别精度对比.md)与[主机速度对比](../speed-comparison.md)供交叉参考；它们与本次手机计时的测试范围不同。"), ""]
    (root / "真机速度对比.md").write_text("\n".join(lines), encoding="utf-8")
    print(f"Verified {len(metadata['runs'])} device runs, raw percentiles and all model/input hashes; wrote {root / '真机速度对比.md'}")


if __name__ == "__main__":
    main()
