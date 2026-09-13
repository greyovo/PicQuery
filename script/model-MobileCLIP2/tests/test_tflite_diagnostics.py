"""Offline regression checks for diagnostic provenance without archived logs."""
from __future__ import annotations

import json
import math
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

from fixture_support import stable_sha


SCRIPTS = Path(__file__).resolve().parents[1]
CASES = ["default4", "xnn1", "xnn4", "xnn8", "native1", "native2", "native4", "native8", "onnx1", "onnx4", "builtin4"]


def timing_row(samples, vector, native_pool=None):
    ordered = sorted(samples)
    row = {
        "raw_samples_ms": samples,
        "summary": {
            "p50_ms": ordered[math.ceil(len(samples) * 50 / 100) - 1],
            "p95_ms": ordered[math.ceil(len(samples) * 95 / 100) - 1],
            "p99_ms": ordered[math.ceil(len(samples) * 99 / 100) - 1],
        },
        "process_cpu_ms": 20.0,
        "sample_loop_wall_ms": 10.0,
        "cpu_parallelism": 2.0,
        "first_output": vector,
    }
    if native_pool is not None:
        row["native_xnnpack_has_thread_pool"] = native_pool
    return row


def build_cases(root: Path):
    fixture_manifest = {
        "models": [
            {
                "id": "v2_s0_tflite_int8",
                "image_input_sha256": stable_sha("diag:tflite:image-input"),
                "image_sha256": stable_sha("diag:tflite:image-model"),
                "text_sha256": stable_sha("diag:tflite:text-model"),
            },
            {
                "id": "v2_s0_onnx_int8",
                "image_input_sha256": stable_sha("diag:onnx:image-input"),
                "image_sha256": stable_sha("diag:onnx:image-model"),
                "text_sha256": stable_sha("diag:onnx:text-model"),
            },
        ],
        "prompts": [f"synthetic prompt {index}" for index in range(13)],
        "token_count": 13,
        "tokens_sha256": stable_sha("diag:tokens"),
    }
    manifest_path = root / "fixtures.json"
    manifest_path.write_text(json.dumps(fixture_manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    baseline_image = [1.0] + [0.0] * 511
    baseline_text = [0.0, 1.0] + [0.0] * 510
    builtin_image = [0.9] + [0.1] * 511
    builtin_text = [0.1, 0.9] + [0.0] * 510
    cases = {}
    for index, case in enumerate(CASES):
        if case.startswith("onnx"):
            model_id, backend = "v2_s0_onnx_int8", "onnx"
        else:
            model_id, backend = "v2_s0_tflite_int8", "tflite"
        threads = int(case[-1]) if case[-1].isdigit() else 4
        requested_xnnpack = not case.startswith("builtin")
        image_vector, text_vector = (
            (baseline_image, baseline_text)
            if backend != "tflite" or case == "builtin4" or case == "default4" or case.startswith("xnn") or case.startswith("native")
            else (baseline_image, baseline_text)
        )
        if case == "builtin4":
            image_vector, text_vector = builtin_image, builtin_text
        samples = [8.0 + index + offset / 10 for offset in range(10)]
        row = {
            "status": "passed",
            "round_id": case,
            "device": "Synthetic diagnostic device",
            "fingerprint": "synthetic/pixel8a/offline:17/fixture",
            "model_hashes_verified": True,
            "input_hashes_verified": True,
            "tokens_sha256": fixture_manifest["tokens_sha256"],
            "image_input_sha256": next(model["image_input_sha256"] for model in fixture_manifest["models"] if model["id"] == model_id),
            "cpu_threads": threads,
            "requested_xnnpack": requested_xnnpack,
            "backend": backend,
            "model_id": model_id,
            "sample_count": len(samples),
            "actual_image_sha256": next(model["image_sha256"] for model in fixture_manifest["models"] if model["id"] == model_id),
            "actual_text_sha256": next(model["text_sha256"] for model in fixture_manifest["models"] if model["id"] == model_id),
            "image": timing_row(samples, image_vector, native_pool=(threads > 1) if case.startswith("native") else None),
            "text": timing_row([sample / 2 for sample in samples], text_vector, native_pool=(threads > 1) if case.startswith("native") else None),
        }
        (root / f"{case}.json").write_text(json.dumps(row, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        cases[case] = row
    return manifest_path, cases


def cosine(a, b):
    numerator = sum(x * y for x, y in zip(a, b))
    denominator = math.sqrt(sum(x * x for x in a) * sum(y * y for y in b))
    return numerator / denominator


def expected_summary(cases):
    baseline = cases["default4"]
    summary = {}
    for case, row in cases.items():
        item = {"requested_threads": row["cpu_threads"], "xnnpack": row["requested_xnnpack"], "backend": row["backend"]}
        for tower in ("image", "text"):
            value = row[tower]
            item[tower] = {key: value[key] for key in ("summary", "process_cpu_ms", "sample_loop_wall_ms", "cpu_parallelism")}
            if row["backend"] == "tflite":
                reference = baseline[tower]["first_output"]
                item[tower]["first_output_cosine_to_default"] = cosine(value["first_output"], reference)
                item[tower]["first_output_max_abs_to_default"] = max(abs(a - b) for a, b in zip(value["first_output"], reference))
            if case.startswith("native"):
                item[tower]["native_thread_pool"] = value["native_xnnpack_has_thread_pool"]
        summary[case] = item
    return summary


class DiagnosticProvenanceTest(unittest.TestCase):
    def render(self, previous):
        with tempfile.TemporaryDirectory(prefix="mobileclip-diag-") as temporary:
            root = Path(temporary)
            inputs = root / "diagnostics"
            inputs.mkdir()
            manifest_path, cases = build_cases(inputs)
            if previous is not None:
                (inputs / "summary.json").write_text(json.dumps(previous, ensure_ascii=False, indent=2), encoding="utf-8")
            result = subprocess.run(
                [sys.executable, str(SCRIPTS / "summarize_tflite_diagnostics.py"), "--input", str(inputs), "--fixtures", str(manifest_path)],
                cwd=root,
                capture_output=True,
                text=True,
                encoding="utf-8",
            )
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
            actual = json.loads((inputs / "summary.json").read_text(encoding="utf-8"))
            return actual, cases

    def test_preserves_recorded_hashes_and_measured_statistics(self):
        with tempfile.TemporaryDirectory(prefix="mobileclip-diag-source-") as temporary:
            _, cases = build_cases(Path(temporary))
            summary = expected_summary(cases)
        previous = {
            "device": "Synthetic diagnostic device",
            "fingerprint": "synthetic/pixel8a/offline:17/fixture",
            "cases": summary,
            "artifact_sha256": {"android-test.apk": "a" * 64},
        }
        actual, _ = self.render(previous)
        self.assertEqual(actual["artifact_sha256"], previous["artifact_sha256"])
        self.assertEqual(actual["cases"], summary)

    def test_does_not_invent_provenance_for_missing_receipt(self):
        actual, _ = self.render(None)
        self.assertEqual(set(actual["cases"]), set(CASES))
        self.assertEqual(actual["artifact_sha256"], {})

    def test_does_not_reuse_hashes_from_different_measurements(self):
        previous = {
            "device": "Synthetic diagnostic device",
            "fingerprint": "synthetic/pixel8a/offline:17/fixture",
            "cases": {},
            "artifact_sha256": {"android-test.apk": "a" * 64},
        }
        actual, _ = self.render(previous)
        self.assertEqual(actual["artifact_sha256"], {})


if __name__ == "__main__":
    unittest.main()
