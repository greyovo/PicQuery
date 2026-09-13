#!/usr/bin/env python3
"""Re-render the historical LiteRT 1.4.1 CPU threading experiment.

This checks archived measurements, not the current App runtime. Existing build
files cannot establish the identity of binaries used by an earlier experiment.
"""
import argparse
import json
import math
from pathlib import Path


CASES = ["default4", "xnn1", "xnn4", "xnn8", "native1", "native2", "native4", "native8", "onnx1", "onnx4", "builtin4"]


def cosine(a, b):
    return sum(x * y for x, y in zip(a, b)) / math.sqrt(sum(x * x for x in a) * sum(y * y for y in b))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", type=Path, default=Path("script/model-MobileCLIP2/results/pixel8a-tflite-diagnostics"))
    parser.add_argument("--fixtures", type=Path,
                        default=Path(__file__).resolve().parent / "results/pixel8a-speed/fixture-manifest.json",
                        help="Manifest recorded for the historical experiment, not current build outputs.")
    args = parser.parse_args()
    root = args.input
    rows = {case: json.loads((root / f"{case}.json").read_text(encoding="utf-8-sig")) for case in CASES}
    manifest = json.loads(args.fixtures.read_text(encoding="utf-8-sig"))
    models = {row["id"]: row for row in manifest["models"]}
    baseline = rows["default4"]
    summary = {}
    for case, row in rows.items():
        assert row["status"] == "passed" and row["round_id"] == case
        assert row["fingerprint"] == baseline["fingerprint"] and row["model_hashes_verified"] and row["input_hashes_verified"]
        assert row["tokens_sha256"] == manifest["tokens_sha256"]
        assert row["image_input_sha256"] == models[row["model_id"]]["image_input_sha256"]
        item = {"requested_threads": row["cpu_threads"], "xnnpack": row["requested_xnnpack"], "backend": row["backend"]}
        for tower in ("image", "text"):
            value = row[tower]
            assert row[f"actual_{tower}_sha256"] == models[row["model_id"]][f"{tower}_sha256"]
            samples = value["raw_samples_ms"]
            assert len(samples) == row["sample_count"] and all(math.isfinite(x) and x > 0 for x in samples)
            for p in (50, 95, 99):
                assert math.isclose(sorted(samples)[math.ceil(len(samples) * p / 100) - 1], value["summary"][f"p{p}_ms"])
            assert math.isclose(value["process_cpu_ms"] / value["sample_loop_wall_ms"], value["cpu_parallelism"])
            vector = value["first_output"]
            assert len(vector) == 512 and all(math.isfinite(x) for x in vector)
            item[tower] = {k: value[k] for k in ("summary", "process_cpu_ms", "sample_loop_wall_ms", "cpu_parallelism")}
            if row["backend"] == "tflite":
                reference = baseline[tower]["first_output"]
                item[tower]["first_output_cosine_to_default"] = cosine(vector, reference)
                item[tower]["first_output_max_abs_to_default"] = max(abs(a - b) for a, b in zip(vector, reference))
                if case != "builtin4":
                    assert vector == reference, f"Unexpected output difference: {case}/{tower}"
            if case.startswith("native"):
                item[tower]["native_thread_pool"] = value["native_xnnpack_has_thread_pool"]
                assert value["native_xnnpack_has_thread_pool"] == (row["cpu_threads"] > 1)
        summary[case] = item
    receipt_path = root / "summary.json"
    previous = json.loads(receipt_path.read_text(encoding="utf-8-sig")) if receipt_path.exists() else {}
    same_experiment = (previous.get("cases") == summary
                       and previous.get("device") == baseline["device"]
                       and previous.get("fingerprint") == baseline["fingerprint"])
    provenance = previous.get("artifact_sha256", {}) if same_experiment else {}
    receipt_path.write_text(json.dumps({"device": baseline["device"], "fingerprint": baseline["fingerprint"],
        "cases": summary, "artifact_sha256": provenance, "production_app_changed": False,
        "artifact_provenance_note": "Recorded hashes retained only when the existing receipt matches these measurements. "
                                    "An empty map means unavailable; current build files are never substituted."},
        indent=2) + "\n", encoding="utf-8")
    lines = ["# Pixel 8a：TFLite 线程问题诊断", "",
        "已定位主要原因：LiteRT 1.4.1 Java Interpreter 的 lazy XNNPACK 路径没有把请求线程数传入 XNNPACK 自己的线程池。此前报告中的 CPU 4 线程只是请求配置，不能视为两个后端均实际使用四线程。原始时延记录是真实的，但不能把它解读为 TFLite 正常四线程性能。", "",
        "## 真机对照", "", "同一 Pixel 8a / Tensor G3 / Android 17、同一对模型及输入文件。原生探针只改变 XNNPACK options.num_threads，保留其他默认选项；没有改变模型、量化、精度或 GPU/NPU 配置。",
        "每组预热 10 次；Java/ONNX 对照各 30 个正式样本，原生探针各 50 个。配置间休息 10 秒，未锁频；这是一轮定位实验，不是长期负载排名。", "",
        "| 配置 | 图片 P50 ms | 图片 CPU/墙钟 | 文本 P50 ms | 文本 CPU/墙钟 |",
        "|---|---:|---:|---:|---:|",
    ]
    labels = {"default4": "原 Java 默认 XNNPACK，请求 4", "xnn1": "Java 显式 XNNPACK，请求 1", "xnn4": "Java 显式 XNNPACK，请求 4", "xnn8": "Java 显式 XNNPACK，请求 8",
              "native1": "原生 XNNPACK，实际配置 1", "native2": "原生 XNNPACK，实际配置 2", "native4": "原生 XNNPACK，实际配置 4", "native8": "原生 XNNPACK，实际配置 8", "onnx1": "ONNX，请求 1", "onnx4": "ONNX，请求 4"}
    for case, label in labels.items():
        image, text = summary[case]["image"], summary[case]["text"]
        lines.append(f"| {label} | {image['summary']['p50_ms']:.2f} | {image['cpu_parallelism']:.2f} | {text['summary']['p50_ms']:.2f} | {text['cpu_parallelism']:.2f} |")
    original, fixed, onnx = [summary[c]["image"]["summary"]["p50_ms"] for c in ("default4", "native4", "onnx4")]
    lines += ["", f"原生显式配置四线程后，TFLite 图片耗时由 {original:.2f} ms 降至 {fixed:.2f} ms，约为原速度的 {original/fixed:.2f} 倍；与本轮 ONNX 四线程 {onnx:.2f} ms 相比，耗时高约 {(fixed/onnx-1)*100:.1f}%。",
        "CPU/墙钟 = 正式采样循环的进程累计 CPU 时间 / 墙钟时间。它描述全进程平均 CPU 使用量，不是精确的工作线程计数；约 1 表示平均一个核的工作量，接近 4 表示接近四核并行工作量。原生探针同时直接验证了 XNNPACK 线程池：1 线程没有池，2/4/8 线程都有池。", "",
        "## 排除的原因和边界", "",
        "- 默认与显式启用 XNNPACK 的结果相同，单独添加 setUseXNNPACK(true) 无法修复线程传递。关闭 XNNPACK 也不是这次采用的修复。",
        "- 默认、Java 1/4/8 与原生 1/2/4/8 的模型及输入 SHA256 完全一致，首张图片、首条文本的 512 维输出逐元素完全相同。只抽查了首组向量，没有重新跑整套识别精度数据集。",
        "- 真机日志显示图像 242/242 个算子全部交给 XNNPACK，只有 1 个分区；文本 695/705 个算子被接管，有 4 个分区。原生修正前后覆盖保持一致。",
        "- 图结构检查确认已重参数化；没有 Flex 算子、DEQUANTIZE 或动态生成卷积权重。存在少量额外 layout 转换，但没有证据把四倍差距归因于它。主机图结构摘要保存在 host-graph-summary.json；完整临时分析在 build/mobileclip-tflite-diagnostics/。",
        "- 增加到 8 线程反而更慢；图像和文本应分别调优，不能假设线程越多越快。这里只测一个设备和当前 runtime。",
        f"- 禁用 XNNPACK 的 builtin4 对照文本输出异常：与默认路径首条文本向量余弦仅 {summary['builtin4']['text']['first_output_cosine_to_default']:.5f}。尽管有限值检查通过，这条路径不具备输出等价性，已从有效速度比较表排除。该路径未用于 App。", "",
        "## 代码及复现", "",
        "当次 11 组诊断实验的变更仅在 Android instrumentation、测试用 JNI 探针和诊断脚本，生产 App 的运行配置、运行库版本和模型资产当时未改动。summary.json 中 production_app_changed=false 记录的是这次历史实验；原始 JSON 和日志保留当时的数据。探针当时仅验证 ARM64、LiteRT 1.4.1，不能将其结果直接视为生产 App 的验证结果。", "",
        "随后采用 LiteRT 1.4.1 的历史 App 验证接入原生 XNNPACK，配置图像 4 线程、文本 2 线程；ONNX 两塔均为 4 线程。两个 Debug APK 在 Pixel 8a 上分别索引 5 张 demo 图并返回狗和宇航员照片，TFLite 两塔线程池均确认存在。这些是历史构建的结果，不代表当前构建验证；见 [当次 App 报告](../pixel8a-app/实际运行报告.md) 和 [当前双语指南](../../README_zh.md)。当前依赖以版本目录为准，不能用本机现有 APK 的哈希替代历史执行来源。", "",
        "构建探针需要已安装的 Android NDK；本机使用 29.0.14206865。四份官方头文件下载后严格校验 SHA256，XNNPACK options 结构来自 LiteRT v1.4.1 官方头文件，所需基础类型来自 TensorFlow v2.20.0。没有新增运行时依赖。", "",
        "```powershell",
        '# Historical commands require a checkout using LiteRT 1.4.1; the probe rejects other versions.',
        '& script/model-MobileCLIP2/build_xnnpack_probe.ps1 -NdkDirectory "$env:ANDROID_SDK_ROOT/ndk/29.0.14206865"',
        ".\\gradlew.bat :app:assembleOnnxDebugAndroidTest",
        "adb -s DEVICE_SERIAL install -r app/build/outputs/apk/androidTest/onnx/debug/app-onnx-debug-androidTest.apk",
        "python script/model-MobileCLIP2/upload_device_benchmark.py --serial DEVICE_SERIAL --manifest build/mobileclip-tflite-diagnostics/probe-upload.json",
        "python script/model-MobileCLIP2/diagnose_device_tflite.py --serial DEVICE_SERIAL",
        "python script/model-MobileCLIP2/diagnose_device_tflite.py --serial DEVICE_SERIAL --cases native1 native2 native4 native8 --samples 50",
        "python script/model-MobileCLIP2/summarize_tflite_diagnostics.py",
        "```", "",
        "前提是已按原速度测试准备并上传模型/输入到 debug App 的私有目录。每个配置的 JSON 保留全部耗时、CPU 时间、首组输出、模型哈希和温度信息，delegate.log 与 instrumentation.log 保存执行证据。汇总脚本复核所有样本分位数、CPU比值、哈希及向量等价性；探针以 -Wall -Wextra -Werror 编译，Android test APK 构建和 Lint 均通过。", "",
        "## 官方证据", "",
        "- [LiteRT v1.4.1 lazy XNNPACK 创建代码](https://github.com/google-ai-edge/LiteRT/blob/v1.4.1/tflite/java/src/main/native/op_resolver_lazy_delegate_proxy.cc)：使用默认 options 创建 delegate，没有赋值 num_threads。",
        "- [官方仓库同类问题 #1328](https://github.com/google-ai-edge/LiteRT/issues/1328)：报告 Java setNumThreads 未传入 XNNPACK 线程池。",
        "- [官方 XNNPACK options](https://github.com/google-ai-edge/LiteRT/blob/v1.4.1/tflite/delegates/xnnpack/xnnpack_delegate.h)：num_threads 为 0 或负数时不使用线程池。", "",
        "[汇总与验证](summary.json) · [原始五模型速度报告](../pixel8a-speed/真机速度对比.md)", ""]
    (root / "诊断报告.md").write_text("\n".join(lines), encoding="utf-8")
    print(f"Verified {len(rows)} cases and output equivalence; wrote threading diagnosis.")


if __name__ == "__main__":
    main()
