"""Audit archived measurements and bilingual summaries without running models."""
import math
from pathlib import Path
import statistics
import sys
import unittest
from unittest.mock import patch

import numpy as np

from fixture_support import ARCHIVE, BASE, digest, read

sys.path.insert(0, str(BASE))
from render_image_int8_report import FP32, INT8, LEGACY, MODELS, validate_bilingual_tables
from summarize_device_benchmark import stats, verify_stats

RESULTS = BASE / "results"
HISTORICAL = ("v1_s0_onnx_int8", "v1_s2_onnx_int8", "v2_s0_onnx_int8", "v2_s0_tflite_int8", LEGACY)
FORMAT_MODELS = (*HISTORICAL[:3], "v2_s0_ort_int8", LEGACY)
METRICS = ("top1", "top5", "class_query_p_at_10", "class_query_map")


def table(path, marker):
    text = path.read_text(encoding="utf-8-sig")
    section = text.split(f"<!-- archive-table: {marker} -->\n", 1)[1].split("\n\n", 1)[0]
    return [[cell.strip() for cell in line.split("|")[1:-1]] for line in section.splitlines()[2:]]


def accuracy_table(path):
    rows = []
    for line in path.read_text(encoding="utf-8-sig").splitlines():
        cells = [cell.strip() for cell in line.split("|")[1:-1]]
        if len(cells) == 6 and all(cell.endswith("%") for cell in cells[2:]):
            rows.append((cells[0], cells[2:]))
    return rows


class TestReportArchive(unittest.TestCase):
    def test_archived_accuracy_counts_and_aggregates(self):
        for source, manifest in (
            (RESULTS / "accuracy-results.json", RESULTS / "accuracy-results-manifest.json"),
            (ARCHIVE / "accuracy.json", ARCHIVE / "accuracy-manifest.json"),
            (ARCHIVE / "pixel8a-accuracy/device-accuracy.json", ARCHIVE / "accuracy-manifest.json"),
        ):
            data, samples = read(source), read(manifest)
            for name, bundle in data["datasets"].items():
                labels = np.asarray([row["label"] for row in samples[name]["samples"]])
                for model, row in bundle["metrics"].items():
                    with self.subTest(source=source.name, dataset=name, model=model):
                        correct = np.asarray(row["predicted_class"]) == labels
                        self.assertEqual(row["n"], len(labels))
                        self.assertEqual(row["top1_correct"], int(correct.sum()))
                        self.assertAlmostEqual(row["top1"], float(correct.mean()), places=12)
                        self.assertGreaterEqual(row["top5"], row["top1"])
                        self.assertAlmostEqual(row["top5"] * len(labels), round(row["top5"] * len(labels)), places=8)
                        for aggregate, field in (("macro_top1", "top1"), ("class_query_p_at_10", "p_at_10"), ("class_query_map", "ap")):
                            self.assertAlmostEqual(row[aggregate], statistics.mean(c[field] for c in row["per_class"]), places=12)
                        for index, item in enumerate(row["per_class"]):
                            self.assertEqual(item["class"], samples[name]["classes"][index])
                            self.assertEqual(item["support"], int((labels == index).sum()))
                            self.assertAlmostEqual(item["top1"], float(correct[labels == index].mean()), places=12)

    def test_paired_phone_counts_and_intervals(self):
        from evaluate_accuracy import comparisons

        data = read(ARCHIVE / "pixel8a-accuracy/device-accuracy.json")
        host, manifest = read(ARCHIVE / "accuracy.json"), read(ARCHIVE / "accuracy-manifest.json")
        for name, bundle in data["datasets"].items():
            labels = np.asarray([row["label"] for row in manifest[name]["samples"]])
            self.assertEqual(bundle["comparisons"], comparisons(bundle["metrics"], labels))
            for model, compared in bundle["host_device_comparisons"].items():
                first, second = host["datasets"][name]["metrics"][model], bundle["metrics"][model]
                pair = comparisons({"host": first, "device": second}, labels)[0]
                self.assertEqual(compared["paired_top1"], pair)
                flips = np.flatnonzero(np.asarray(first["predicted_class"]) != np.asarray(second["predicted_class"]))
                self.assertEqual(compared["prediction_flip_count"], len(flips))
                self.assertEqual([v["position"] for v in compared["prediction_flips"]], flips.tolist())

    def test_phone_archive_hash_chain_and_shared_text(self):
        root = ARCHIVE / "pixel8a-accuracy"
        manifest, run, data = (read(root / name) for name in ("manifest.json", "run-metadata.json", "device-accuracy.json"))
        self.assertEqual(manifest["host_accuracy_sha256"], digest(ARCHIVE / "accuracy.json"))
        self.assertEqual(manifest["evaluation_manifest_sha256"], digest(ARCHIVE / "accuracy-manifest.json"))
        self.assertEqual(run["status"], "complete")
        self.assertEqual(run["manifest_sha256"], digest(root / "manifest.json"))
        self.assertEqual(data["source_sha256"]["run_metadata"], digest(root / "run-metadata.json"))
        self.assertEqual({r["model_id"] for r in run["runs"]}, {FP32, INT8})
        self.assertEqual(len(run["runs"]), 2)
        for receipt in run["runs"]:
            self.assertEqual(receipt["status"], "passed")
            report_path = root / receipt["report_file"]
            self.assertEqual(receipt["report_sha256"], digest(report_path))
            report = read(report_path)
            self.assertEqual(report["runtime_version"], "1.29.0")
            self.assertEqual(report["cpu_threads"], 4)
            self.assertEqual(report["manifest_sha256"], digest(root / "manifest.json"))
            self.assertEqual(report["fingerprint"], run["device"]["fingerprint"])
            for dataset in report["datasets"]:
                for tower in ("image", "text"):
                    output = dataset[tower]
                    archived = data["export_reports"][receipt["model_id"]]["outputs"][dataset["id"]][tower]
                    self.assertEqual(output["sha256"], archived["sha256"])
                    self.assertEqual(output["shape"], archived["shape"])
                    self.assertEqual(output["completed_count"], output["count"])
        self.assertTrue(data["shared_text_outputs_byte_identical"])
        for dataset in ("cifar100", "imagenette"):
            outputs = [data["export_reports"][model]["outputs"][dataset]["text"] for model in (FP32, INT8)]
            self.assertEqual(outputs[0]["sha256"], outputs[1]["sha256"])
            self.assertEqual(outputs[0]["shape"], outputs[1]["shape"])

    def test_phone_raw_arrays_when_available(self):
        root = ARCHIVE / "pixel8a-accuracy"
        reports = [read(root / f"{model}-report.json") for model in (FP32, INT8)]
        records = [row[tower] for report in reports for row in report["datasets"] for tower in ("image", "text")]
        missing = [row["file"] for row in records if not (root / row["file"]).is_file()]
        if missing:
            self.skipTest("Raw .f32 arrays are not in Git; regenerate phone exports to rehash and inspect vectors")
        for row in records:
            path = root / row["file"]
            self.assertEqual(path.stat().st_size, row["bytes"])
            self.assertEqual(digest(path), row["sha256"])
            values = np.fromfile(path, dtype="<f4").reshape(row["shape"])
            self.assertTrue(np.isfinite(values).all())
            self.assertTrue((np.linalg.norm(values, axis=1) >= 1e-8).all())
        for dataset in ("cifar100", "imagenette"):
            self.assertEqual((root / f"{FP32}-{dataset}-text.f32").read_bytes(),
                             (root / f"{INT8}-{dataset}-text.f32").read_bytes())

    def test_bilingual_tables_match(self):
        for directory in (RESULTS, ARCHIVE, ARCHIVE / "pixel8a-accuracy", RESULTS / "ort-comparison"):
            with self.subTest(directory=directory.name):
                validate_bilingual_tables((directory / "README.md").read_text(encoding="utf-8-sig"),
                                          (directory / "README_zh.md").read_text(encoding="utf-8-sig"))

    def test_missing_raw_arrays_explicitly_skip(self):
        with patch.object(Path, "is_file", return_value=False):
            with self.assertRaisesRegex(unittest.SkipTest, "Raw .f32 arrays are not in Git"):
                self.test_phone_raw_arrays_when_available()

    def test_generated_accuracy_tables_match_json(self):
        for directory, source, models in ((ARCHIVE, "accuracy.json", MODELS),
                                          (ARCHIVE / "pixel8a-accuracy", "device-accuracy.json", (FP32, INT8))):
            data = read(directory / source)
            expected = [(name, [f"{bundle['metrics'][model][key]*100:.2f}%" for key in METRICS])
                        for name, bundle in data["datasets"].items() for model in models]
            for filename in ("README.md", "README_zh.md"):
                self.assertEqual(accuracy_table(directory / filename), expected)

    def test_index_accuracy_tables_match_json(self):
        host, phone = read(ARCHIVE / "accuracy.json"), read(ARCHIVE / "pixel8a-accuracy/device-accuracy.json")
        expected = []
        for data in (host, phone):
            for model in (FP32, INT8):
                expected.append([f"{row['top1']*100:.2f}% ({row['top1_correct']} / {row['n']})"
                                 for dataset in ("cifar100", "imagenette")
                                 for row in [data["datasets"][dataset]["metrics"][model]]])
        old = read(RESULTS / "accuracy-results.json")
        historical = [[f"{old['datasets'][dataset]['metrics'][model]['top1']*100:.2f}%"
                       for dataset in ("cifar100", "imagenette")] for model in HISTORICAL]
        for filename in ("README.md", "README_zh.md"):
            self.assertEqual([row[2:] for row in table(RESULTS / filename, "current-accuracy")], expected)
            self.assertEqual([row[1:] for row in table(RESULTS / filename, "historical-accuracy")], historical)

    def test_device_speed_tables_match_raw_timings(self):
        for root, count in ((RESULTS / "ort-comparison/pixel8a", 2000), (ARCHIVE / "pixel8a", 1200)):
            metadata = read(root / "run-metadata.json")
            collected = {}
            for run in metadata["runs"]:
                row = read(root / run["report"])
                self.assertEqual(row["status"], "passed")
                self.assertEqual(row["runtime_version"], "1.29.0")
                self.assertEqual(row["cpu_threads"], 4)
                for tower in ("image", "text"):
                    verify_stats(row[tower])
                    self.assertEqual(len(row[tower]["raw_samples_ms"]), 100)
                    collected.setdefault(run["model"], {}).setdefault(tower, []).extend(row[tower]["raw_samples_ms"])
            self.assertEqual(sum(len(v) for r in collected.values() for v in r.values()), count)
            if count == 2000:
                summary = read(root / "summary.json")["models"]
                expected = []
                for model in FORMAT_MODELS:
                    row = summary[model]
                    for tower in ("image", "text"):
                        raw = stats(collected[model][tower])
                        self.assertAlmostEqual(raw["p50_ms"], row[tower]["p50_ms"], places=9)
                        self.assertAlmostEqual(raw["p95_ms"], row[tower]["p95_ms"], places=9)
                    expected.append([f"{row[t]['p50_ms']:.2f} / {row[t]['p95_ms']:.2f}" for t in ("image", "text")]
                                    + [f"{(row['image_bytes']+row['text_bytes'])/2**20:.2f}"])
                for filename in ("README.md", "README_zh.md"):
                    self.assertEqual([r[1:] for r in table(root.parent / filename, "device-speed")], expected)
                    expected_load = [[f"{statistics.median(summary[m][t][k]):.2f}"
                                      for k in ("load_ms", "first_invoke_ms") for t in ("image", "text")]
                                     for m in ("v2_s0_onnx_int8", "v2_s0_ort_int8")]
                    self.assertEqual([r[1:] for r in table(root.parent / filename, "device-loading")], expected_load)
            else:
                verification = read(ARCHIVE / "README-validation.json")
                expected = [[f"{stats(collected[m][t])['p50_ms']:.2f}" for t in ("image", "text")]
                            + [f"{verification['accuracy']['sizes_bytes'][m]['image']/2**20:.2f}"] for m in MODELS]
                for filename in ("README.md", "README_zh.md"):
                    self.assertEqual([r[1:] for r in table(RESULTS / filename, "current-speed")], expected)

    def test_ort_host_summary_matches_raw_timings(self):
        expected = []
        for filename in ("host.json", "host-amd64.json"):
            data = read(RESULTS / "ort-comparison" / filename)
            row = []
            for model in ("v2_s0_onnx_int8", "v2_s0_ort_int8"):
                values = []
                for tower in ("image", "text"):
                    rounds = data["models"][model][tower + "_rounds"]
                    samples = [sample for item in rounds for sample in item["warm_samples_ms"]]
                    self.assertEqual(len(rounds), 3)
                    self.assertEqual(len(samples), 300)
                    self.assertTrue(all(math.isfinite(v) and v > 0 for v in samples))
                    values.append(f"{statistics.median(samples):.2f}")
                row.append(" / ".join(values))
            expected.append(row)
        for filename in ("README.md", "README_zh.md"):
            self.assertEqual([r[1:] for r in table(RESULTS / "ort-comparison" / filename, "host-speed")], expected)


if __name__ == "__main__":
    unittest.main()
