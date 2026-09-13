"""Validate retained bilingual summaries against the compact measured summary."""
from __future__ import annotations

from pathlib import Path
import statistics
import unittest

from fixture_support import BASE, FP32, INT8, LEGACY, RUNTIME, read


RESULTS = BASE / "results"
SUMMARY_PATH = RESULTS / "measured-summary.json"
IMAGE_INT8 = RESULTS / "image-int8"
PHONE = IMAGE_INT8 / "pixel8a-accuracy"
ORT = RESULTS / "ort-comparison"
CURRENT_MODEL_ORDER = (FP32, INT8)
HISTORICAL_MODEL_ORDER = ("v1_s0_onnx_int8", "v1_s2_onnx_int8", "v2_s0_onnx_int8", "v2_s0_tflite_int8", LEGACY)
ORT_DEVICE_ORDER = ("v1_s0_onnx_int8", "v1_s2_onnx_int8", "v2_s0_onnx_int8", "v2_s0_ort_int8", LEGACY)


def table(path: Path, marker: str):
    text = path.read_text(encoding="utf-8-sig")
    section = text.split(f"<!-- archive-table: {marker} -->\n", 1)[1].split("\n\n", 1)[0]
    return [[cell.strip() for cell in line.split("|")[1:-1]] for line in section.splitlines()[2:]]


def accuracy_table(path: Path):
    rows = []
    for line in path.read_text(encoding="utf-8-sig").splitlines():
        cells = [cell.strip() for cell in line.split("|")[1:-1]]
        if len(cells) == 6 and all(cell.endswith("%") for cell in cells[2:]):
            rows.append((cells[0], cells[2:]))
    return rows


def markdown_tables(path: Path):
    groups, current = [], []
    for line in path.read_text(encoding="utf-8-sig").splitlines():
        if line.startswith("|"):
            current.append([cell.strip() for cell in line.split("|")[1:-1]])
        elif current:
            groups.append(current)
            current = []
    if current:
        groups.append(current)
    return groups


def find_table(path: Path, header):
    for group in markdown_tables(path):
        if group and group[0][:len(header)] == list(header):
            return group[2:]
    raise AssertionError(f"Missing table with header {header} in {path}")


def current_accuracy_cells(summary, platform, model):
    metrics = summary["current_accuracy"][platform]["datasets"]
    return [f"{metrics[name]['metrics'][model]['top1'] * 100:.2f}% ({metrics[name]['metrics'][model]['top1_correct']} / {metrics[name]['metrics'][model]['n']})" for name in ("cifar100", "imagenette")]


def image_accuracy_cells(summary, platform, model):
    metrics = summary["current_accuracy"][platform]["datasets"]
    return [f"{metrics[name]['metrics'][model][key] * 100:.2f}%" for name in ("cifar100", "imagenette") for key in ("top1", "top5", "class_query_p_at_10", "class_query_map")]


class TestReportArchive(unittest.TestCase):
    def test_summary_schema_sane(self):
        summary = read(SUMMARY_PATH)
        self.assertEqual(summary["schema_version"], 1)
        self.assertEqual(summary["measurement_date"], "2026-09-13")
        self.assertEqual(summary["protocol"]["seed"], 20260913)
        self.assertEqual(summary["protocol"]["prompt_template"], "a photo of a {class}")
        self.assertEqual(summary["protocol"]["datasets"]["cifar100"]["samples"], 2000)
        self.assertEqual(summary["protocol"]["datasets"]["imagenette"]["samples"], 3925)
        self.assertEqual(summary["current_accuracy"]["host"]["versions"]["onnxruntime"], RUNTIME)
        self.assertEqual(summary["current_accuracy"]["device"]["onnxruntime_version"], RUNTIME)
        self.assertEqual(summary["current_speed"]["cpu_threads"], 4)
        self.assertEqual(summary["ort_comparison"]["phone"]["cpu_threads"], 4)
        self.assertEqual(summary["historical_accuracy"]["versions"]["onnxruntime"], "1.25.0")
        self.assertTrue(summary["mixed_quantization"]["quantized_ops"]["Conv"] > 0)
        self.assertEqual(summary["current_accuracy"]["host"]["models"][FP32]["text_sha256"], summary["current_accuracy"]["host"]["models"][INT8]["text_sha256"])
        self.assertEqual(summary["current_accuracy"]["device"]["models"][FP32]["text_sha256"], summary["current_accuracy"]["device"]["models"][INT8]["text_sha256"])

    def test_bilingual_tables_match(self):
        from render_image_int8_report import validate_bilingual_tables

        for directory in (RESULTS, IMAGE_INT8, PHONE, ORT):
            with self.subTest(directory=directory.name):
                validate_bilingual_tables((directory / "README.md").read_text(encoding="utf-8-sig"), (directory / "README_zh.md").read_text(encoding="utf-8-sig"))

    def test_root_tables_match_summary(self):
        summary = read(SUMMARY_PATH)
        expected_current = [
            current_accuracy_cells(summary, "host", FP32),
            current_accuracy_cells(summary, "host", INT8),
            current_accuracy_cells(summary, "device", FP32),
            current_accuracy_cells(summary, "device", INT8),
        ]
        expected_historical = [
            [f"{summary['historical_accuracy']['datasets'][dataset]['metrics'][model]['top1'] * 100:.2f}%" for dataset in ("cifar100", "imagenette")]
            for model in HISTORICAL_MODEL_ORDER
        ]
        expected_speed = [
            [f"{summary['current_speed']['models'][model]['image']['p50_ms']:.2f}", f"{summary['current_speed']['models'][model]['text']['p50_ms']:.2f}", f"{summary['current_speed']['models'][model]['image_file_bytes'] / 2 ** 20:.2f}"]
            for model in (FP32, INT8, LEGACY)
        ]
        self.assertEqual([row[2:] for row in table(RESULTS / "README.md", "current-accuracy")], expected_current)
        self.assertEqual([row[1:] for row in table(RESULTS / "README.md", "historical-accuracy")], expected_historical)
        self.assertEqual([row[1:] for row in table(RESULTS / "README.md", "current-speed")], expected_speed)

    def test_image_int8_tables_match_summary(self):
        summary = read(SUMMARY_PATH)
        expected_host = [(name, [f"{summary['current_accuracy']['host']['datasets'][name]['metrics'][model][key] * 100:.2f}%" for key in ("top1", "top5", "class_query_p_at_10", "class_query_map")]) for name in ("cifar100", "imagenette") for model in (FP32, INT8, LEGACY)]
        expected_phone = [(name, [f"{summary['current_accuracy']['device']['datasets'][name]['metrics'][model][key] * 100:.2f}%" for key in ("top1", "top5", "class_query_p_at_10", "class_query_map")]) for name in ("cifar100", "imagenette") for model in (FP32, INT8)]
        self.assertEqual(accuracy_table(IMAGE_INT8 / "README.md"), expected_host)
        self.assertEqual(accuracy_table(PHONE / "README.md"), expected_phone)

    def test_phone_comparison_tables_match_summary(self):
        summary = read(SUMMARY_PATH)
        expected_int8_vs_fp32 = []
        expected_host_vs_phone = []
        device = summary["current_accuracy"]["device"]["datasets"]
        for dataset in ("cifar100", "imagenette"):
            pair = device[dataset]["comparisons"][0]
            expected_int8_vs_fp32.append([
                dataset,
                f"{pair['b_minus_a_top1_pp']:.2f}",
                f"[{pair['paired_bootstrap_95ci_pp'][0]:+.2f}, {pair['paired_bootstrap_95ci_pp'][1]:+.2f}]",
                f"{pair['a_only_correct']} / {pair['b_only_correct']}",
                f"{device[dataset]['fp32_int8_prediction_flip_count']}",
            ])
            for model, label in ((FP32, "FP32 image + dynamic INT8 text"), (INT8, "Mixed INT8 image + same dynamic INT8 text")):
                row = device[dataset]["host_device_comparisons"][model]
                metric = device[dataset]["metrics"][model]
                host_metric = summary["current_accuracy"]["host"]["datasets"][dataset]["metrics"][model]
                pair = row["paired_top1"]
                expected_host_vs_phone.append([
                    dataset,
                    label,
                    f"{host_metric['top1'] * 100:.2f}% / {metric['top1'] * 100:.2f}%",
                    f"{pair['b_minus_a_top1_pp']:+.2f}",
                    f"[{pair['paired_bootstrap_95ci_pp'][0]:+.2f}, {pair['paired_bootstrap_95ci_pp'][1]:+.2f}]",
                    f"{row['prediction_flip_count']} / {row['prediction_flip_rate'] * 100:.2f}%",
                ])
        self.assertEqual(find_table(PHONE / "README.md", ("Dataset", "Top1 delta pp", "95% CI pp")), expected_int8_vs_fp32)
        self.assertEqual(find_table(PHONE / "README.md", ("Dataset", "Configuration", "Host / phone Top1")), expected_host_vs_phone)

    def test_ort_tables_match_summary(self):
        summary = read(SUMMARY_PATH)
        phone = summary["ort_comparison"]["phone"]["models"]
        expected_device_speed = [
            [f"{phone[model]['image']['p50_ms']:.2f} / {phone[model]['image']['p95_ms']:.2f}", f"{phone[model]['text']['p50_ms']:.2f} / {phone[model]['text']['p95_ms']:.2f}", f"{(phone[model]['image_bytes'] + phone[model]['text_bytes']) / 2 ** 20:.2f}"]
            for model in ORT_DEVICE_ORDER
        ]
        expected_device_loading = [
            [f"{statistics.median(phone[model]['image']['load_ms']):.2f}", f"{statistics.median(phone[model]['text']['load_ms']):.2f}", f"{statistics.median(phone[model]['image']['first_invoke_ms']):.2f}", f"{statistics.median(phone[model]['text']['first_invoke_ms']):.2f}"]
            for model in ("v2_s0_onnx_int8", "v2_s0_ort_int8")
        ]
        host_rows = []
        for key in ("host_arm_artifacts", "host_amd64_artifacts"):
            item = summary["ort_comparison"][key]["models"]
            host_rows.append([
                f"{item['v2_s0_onnx_int8']['image_summary']['median_ms']:.2f} / {item['v2_s0_onnx_int8']['text_summary']['median_ms']:.2f}",
                f"{item['v2_s0_ort_int8']['image_summary']['median_ms']:.2f} / {item['v2_s0_ort_int8']['text_summary']['median_ms']:.2f}",
            ])
        self.assertEqual([row[1:] for row in table(ORT / "README.md", "device-speed")], expected_device_speed)
        self.assertEqual([row[1:] for row in table(ORT / "README.md", "device-loading")], expected_device_loading)
        self.assertEqual([row[1:] for row in table(ORT / "README.md", "host-speed")], host_rows)


if __name__ == "__main__":
    unittest.main()
