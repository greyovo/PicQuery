"""Portable synthetic report regressions; no models, device or downloads required."""
from __future__ import annotations

import contextlib
import copy
import importlib.util
import io
import json
import os
import pathlib
import sys
import tempfile
import unittest
from unittest.mock import patch

from fixture_support import DEVICE_IDENTITY, BASE, FP32, IMAGE_CONTRACT, INT8, LEGACY, MODELS, REPO, TEXT_CONTRACT, digest, make_artifacts, stable_sha


def load_module(name: str, path: pathlib.Path):
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def synthetic_device_fixture(root: pathlib.Path, module, accuracy, sizes):
    prompts = [f"synthetic query {index}" for index in range(13)]
    common_input = stable_sha("device:shared-image-input")
    fixture = {
        "models": [],
        "prompts": prompts,
        "token_count": len(prompts),
        "tokens_sha256": stable_sha("|".join(prompts)),
        "fixture_sha256": {f"fixture_{index}.jpg": stable_sha(f"fixture:{index}") for index in range(5)},
    }
    for model in MODELS:
        fixture["models"].append({
            "id": model,
            "label": module.LABELS[model],
            "backend": "onnx",
            "format": "ort",
            "preprocess": accuracy["models"][model]["preprocess"],
            "input_size": accuracy["models"][model]["preprocess"]["size"],
            "image_count": 5,
            "image_input_sha256": common_input if model != LEGACY else stable_sha("device:legacy-image-input"),
            "image_sha256": accuracy["models"][model]["image_sha256"],
            "text_sha256": accuracy["models"][model]["text_sha256"],
            "image_file_bytes": sizes[model]["image"],
            "text_file_bytes": sizes[model]["text"],
        })
    fixture_path = root / "fixture-manifest.json"
    fixture_path.write_text(json.dumps(fixture, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    metadata = {
        "status": "complete",
        "model": "Pixel 8a",
        "soc": "Synthetic Tensor",
        "android": "17",
        "fingerprint": DEVICE_IDENTITY["fingerprint"],
        "rounds": 2,
        "sample_count": 100,
        "warmup_count": 10,
        "sustain_seconds_first_round": 0,
        "started_utc": "2026-09-13T08:21:11Z",
        "completed_utc": "2026-09-13T08:24:51Z",
        "runs": [],
    }
    reports = {}
    order = list(MODELS)
    for round_number, models in ((1, order), (2, list(reversed(order)))):
        for index, model in enumerate(models):
            report = {
                **DEVICE_IDENTITY,
                "status": "passed",
                "model_id": model,
                "round_id": f"r{round_number}",
                "runtime_version": module.RUNTIME,
                "cpu_threads": 4,
                "backend": "onnx",
                "sample_count": 100,
                "warmup_count": 10,
                "model_hashes_verified": True,
                "input_hashes_verified": True,
                "tokens_sha256": fixture["tokens_sha256"],
                "image_input_sha256": fixture["models"][0 if model != LEGACY else 2]["image_input_sha256"],
                "preloaded_image_count": 5,
                "preloaded_text_count": len(prompts),
                "input_size": accuracy["models"][model]["preprocess"]["size"],
                "actual_image_sha256": accuracy["models"][model]["image_sha256"],
                "actual_text_sha256": accuracy["models"][model]["text_sha256"],
                "image_file_bytes": sizes[model]["image"],
                "text_file_bytes": sizes[model]["text"],
                "sustain_seconds": 0,
                "sustained_image": {"skipped": True},
                "health_samples": [{
                    "battery_temperature_c": 32.0 + index * 0.2 + round_number * 0.1,
                    "thermal_status": 0,
                    "sampled_total_pss_kb": 184320 + index * 4096,
                }],
            }
            for tower, base in (("image", 30.0 + index * 7), ("text", 14.0 + index * 3)):
                samples = [base + round_number * 0.1 + value / 200 for value in range(100)]
                report[tower] = {
                    "raw_samples_ms": samples,
                    "summary": module.stats(samples),
                    "load_ms": base + 80,
                    "first_invoke_ms": base + 40,
                    "validated_output_count": 111,
                    "first_output": ([1.0] + [0.0] * 511) if model != LEGACY else ([2.0] + [0.0] * 511),
                }
            filename = f"{model}-r{round_number}.json"
            reports[filename] = report
            metadata["runs"].append({"model": model, "round": round_number, "report": filename})
    (root / "run-metadata.json").write_text(json.dumps(metadata, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    for filename, report in reports.items():
        (root / filename).write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return metadata, reports


class TestImageInt8Report(unittest.TestCase):
    def test_synthetic_artifact_contracts(self):
        previous_cwd = pathlib.Path.cwd()
        os.chdir(REPO)
        self.addCleanup(os.chdir, previous_cwd)
        temporary = tempfile.TemporaryDirectory(prefix="mobileclip-report-")
        self.addCleanup(temporary.cleanup)
        root = pathlib.Path(temporary.name)
        artifacts = make_artifacts(root)
        module = load_module("image_report", BASE / "render_image_int8_report.py")

        data = copy.deepcopy(artifacts["accuracy"])
        manifest = copy.deepcopy(artifacts["evaluation_manifest"])
        checks = []

        def rejects(name, func):
            try:
                func()
            except (AssertionError, KeyError, ValueError):
                checks.append(name)
            else:
                raise AssertionError(f"Accepted invalid input: {name}")

        def bad_accuracy(name, change):
            value = copy.deepcopy(data)
            change(value)
            rejects(name, lambda: module.validate_accuracy(value, manifest))

        valid = module.validate_accuracy(data, manifest)
        checks.append("Complete synthetic accuracy metrics and paired CI reconstruction")
        bad_accuracy("Old runtime", lambda value: value["versions"].update(onnxruntime="1.25.0"))
        bad_accuracy("Manifest hash", lambda value: value.update(manifest_sha256="0" * 64))
        bad_accuracy("Wrong threads", lambda value: value.update(threads=1))
        bad_accuracy("Wrong image hash", lambda value: value["models"][INT8].update(image_sha256="0" * 64))
        bad_accuracy("Different preprocess", lambda value: value["models"][INT8]["preprocess"].update(interpolation="bicubic"))

        def change_text(value):
            other = value["models"][LEGACY]
            value["models"][INT8].update(text=other["text"], text_sha256=other["text_sha256"])
            value["models"][INT8]["total_bytes"] = sum(pathlib.Path(value["models"][INT8][tower]).stat().st_size for tower in ("image", "text"))

        bad_accuracy("Different shared text", change_text)
        bad_accuracy("Partial model metrics", lambda value: value["datasets"]["cifar100"]["metrics"].pop(INT8))
        bad_accuracy("Top1 corruption", lambda value: value["datasets"]["cifar100"]["metrics"][INT8].update(top1_correct=1))
        bad_accuracy("Top5 corruption", lambda value: value["datasets"]["cifar100"]["metrics"][INT8].update(top5=1.01))
        bad_accuracy("Class support corruption", lambda value: value["datasets"]["cifar100"]["metrics"][INT8]["per_class"][0].update(support=19))
        bad_accuracy("Retrieval aggregate corruption", lambda value: value["datasets"]["cifar100"]["metrics"][INT8].update(class_query_map=0.0))
        bad_accuracy("Paired count corruption", lambda value: value["datasets"]["cifar100"]["comparisons"][0].update(both_correct=1))
        bad_accuracy("Paired CI corruption", lambda value: value["datasets"]["cifar100"]["comparisons"][0].update(paired_bootstrap_95ci_pp=[-99, 99]))
        bad_accuracy("Incomplete comparisons", lambda value: value["datasets"]["cifar100"]["comparisons"].pop())

        quant_path = artifacts["quantization"]
        quantization = module.read(quant_path)
        module.validate_quantization(quantization, quant_path, data, manifest, valid["sizes_bytes"])
        checks.append("Synthetic calibration provenance and hash checks")

        def bad_quant(name, change):
            value = copy.deepcopy(quantization)
            change(value)
            rejects(name, lambda: module.validate_quantization(value, quant_path, data, manifest, valid["sizes_bytes"]))

        bad_quant("Failed quant export", lambda value: value.update(status="failed"))
        bad_quant("Wrong FP32 source hash", lambda value: value["source_fp32_ort"].update(sha256="0" * 64))
        bad_quant("Wrong quant ORT hash", lambda value: value["image"]["ort"].update(sha256="0" * 64))
        bad_quant("Reported calibration overlap", lambda value: value["calibration"]["leakage_check"].update(native_sha256_overlap=1))
        bad_quant("Calibration manifest hash", lambda value: value["calibration"].update(manifest_sha256="0" * 64))
        bad_quant("No INT8 weight coverage", lambda value: value["coverage"]["quantized"]["initializer_elements"].update(INT8=0))
        bad_quant("INT8 operator count mismatch", lambda value: value["coverage"]["quantized_heavy_ops"].update(Conv=-1))
        mixed = copy.deepcopy(quantization)
        node = next(row for row in mixed["coverage"]["quantized"]["heavy_ops"] if row["both_inputs_quantized"])
        node.update(both_inputs_quantized=False, int8_dequantized_inputs=0)
        mixed["coverage"]["quantized_heavy_ops"][node["op_type"]] -= 1
        mixed_result = module.validate_quantization(mixed, quant_path, data, manifest, valid["sizes_bytes"])
        self.assertTrue(any(row["name"] == node["name"] for row in mixed_result["float_or_partial_nodes"]))
        checks.append("Mixed INT8 and retained FP32 operators are accepted and listed")

        calibration = module.read(quant_path.parent / "calibration_manifest.json")

        def write_json(path, value):
            path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

        for index, (name, change) in enumerate((
            ("Non-train calibration", lambda value: value["samples"][0].update(split="val")),
            ("Native calibration/eval overlap", lambda value: value["samples"][0].update(sha256=manifest["cifar100"]["samples"][0]["sha256"])),
            ("Duplicate calibration IDs", lambda value: value["samples"].__setitem__(1, copy.deepcopy(value["samples"][0]))),
            ("Calibration preprocessing differs", lambda value: value["preprocess"]["spec"].update(interpolation="bicubic")),
        )):
            value = copy.deepcopy(calibration)
            change(value)
            calibration_path = (root / f"bad-calibration-{index}.json").resolve()
            write_json(calibration_path, value)
            updated = copy.deepcopy(quantization)
            updated["calibration"].update(manifest_file=str(calibration_path), manifest_sha256=digest(calibration_path))
            rejects(name, lambda updated=updated: module.validate_quantization(updated, quant_path, data, manifest, valid["sizes_bytes"]))

        device_root = root / "synthetic-device"
        device_root.mkdir(exist_ok=True)
        metadata, rows = synthetic_device_fixture(device_root, module, data, valid["sizes_bytes"])
        device = module.validate_device(device_root, data, valid["sizes_bytes"])
        self.assertEqual(device["raw_sample_count"], 1200)
        self.assertGreater(device["models"][LEGACY]["image"]["first_output_norm"][0], 1)
        checks.append("1200 synthetic device samples with stable timing and legacy norm checks")

        filename = f"{INT8}-r1.json"

        def bad_device(name, change):
            value = copy.deepcopy(rows[filename])
            change(value)
            write_json(device_root / filename, value)
            try:
                rejects(name, lambda: module.validate_device(device_root, data, valid["sizes_bytes"]))
            finally:
                write_json(device_root / filename, rows[filename])

        bad_device("Failed device run", lambda value: value.update(status="failed"))
        bad_device("Old device runtime", lambda value: value.update(runtime_version="1.25.0"))
        bad_device("Device threads", lambda value: value.update(cpu_threads=1))
        bad_device("Device model hash", lambda value: value.update(actual_image_sha256="0" * 64))
        bad_device("Device input hash", lambda value: value.update(image_input_sha256="0" * 64))
        bad_device("Device sample count", lambda value: value.update(sample_count=99))
        bad_device("Missing device sample", lambda value: value["image"]["raw_samples_ms"].pop())
        bad_device("Device percentile", lambda value: value["image"]["summary"].update(p95_ms=900))
        bad_device("Nonfinite device output", lambda value: value["image"]["first_output"].__setitem__(0, float("nan")))
        bad_device("Zero device output", lambda value: value["image"].update(first_output=[0.0] * 512))
        bad_device("Device output dimension", lambda value: value["image"]["first_output"].pop())
        bad_device("MobileCLIP nonunit output", lambda value: value["image"]["first_output"].__setitem__(0, 3.0))

        for name, change in (
            ("Missing device round", lambda value: value["runs"].pop()),
            ("Duplicate device round", lambda value: value["runs"].append(copy.deepcopy(value["runs"][0]))),
            ("Non-reversed device order", lambda value: value["runs"].__setitem__(slice(3, 6), value["runs"][3:6][::-1])),
        ):
            value = copy.deepcopy(metadata)
            change(value)
            write_json(device_root / "run-metadata.json", value)
            try:
                rejects(name, lambda: module.validate_device(device_root, data, valid["sizes_bytes"]))
            finally:
                write_json(device_root / "run-metadata.json", metadata)

        accuracy_path = root / "accuracy.json"
        evaluation_path = root / "accuracy-manifest.json"
        write_json(accuracy_path, data)
        write_json(evaluation_path, manifest)
        for with_device in (True, False):
            output = root / ("README.md" if with_device else "README-without-device.md")
            argv = [str(BASE / "render_image_int8_report.py"), "--accuracy", str(accuracy_path), "--quantization", str(quant_path), "--output", str(output)]
            if with_device:
                argv += ["--device-dir", str(device_root)]
            with patch.object(sys, "argv", argv), contextlib.redirect_stdout(io.StringIO()):
                module.main()
            verified = module.read(output.with_name(output.stem + "-validation.json"))
            self.assertEqual(verified["status"], "passed")
            self.assertIn("quality", verified["status_scope"])
            self.assertEqual(("device" in verified), with_device)
            self.assertTrue(output.with_name(output.stem + "_zh.md").is_file())
            module.validate_bilingual_tables(output.read_text(encoding="utf-8"), output.with_name(output.stem + "_zh.md").read_text(encoding="utf-8"))
        checks.append("CLI writes matched bilingual reports and sidecars with and without device")

        rejects("Bilingual table drift", lambda: module.validate_bilingual_tables("| 75.30% |", "| 75.31% |"))
        write_json(root / "verification.json", {
            "status": "passed",
            "checks": checks,
            "note": "Synthetic JSON and fake model files only; no model or device execution. Values are deterministic fixtures, not benchmark measurements.",
        })


if __name__ == "__main__":
    unittest.main()
