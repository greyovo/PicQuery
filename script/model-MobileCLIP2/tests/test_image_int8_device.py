"""Portable synthetic device-export regressions; no phones, models or downloads required."""
from __future__ import annotations

import copy
import json
import os
import pathlib
import sys
import tempfile
import unittest
from types import SimpleNamespace
from unittest.mock import patch

import numpy as np

from fixture_support import BASE, DEVICE_IDENTITY, FP32, IMAGE_CONTRACT, INT8, REPO, RUNTIME, TEXT_CONTRACT, digest, json_hash, make_artifacts, stable_sha, token_hash


class TestImageInt8Device(unittest.TestCase):
    def test_synthetic_artifact_contracts(self):
        previous_cwd = pathlib.Path.cwd()
        os.chdir(REPO)
        self.addCleanup(os.chdir, previous_cwd)
        temporary = tempfile.TemporaryDirectory(prefix="mobileclip-device-")
        self.addCleanup(temporary.cleanup)
        root = pathlib.Path(temporary.name)
        artifacts = make_artifacts(root)
        sys.path.insert(0, str(BASE.resolve()))
        import evaluate_image_int8_device as module

        host_path = artifacts["host"]
        evaluation_path = artifacts["evaluation"]
        host = module.read(host_path)
        evaluation = module.read(evaluation_path)
        prompts = {
            name: bundle["prompts"]
            for name, bundle in evaluation.items()
        }
        manifest = {
            "schema_version": 1,
            "evaluation_manifest_sha256": digest(evaluation_path),
            "evaluation_samples_sha256": json_hash(evaluation),
            "host_accuracy_sha256": digest(host_path),
            "versions": {"onnxruntime": RUNTIME},
            "preprocess": host["models"][FP32]["preprocess"],
            "models": [],
            "datasets": [],
        }
        for model in module.MODELS:
            spec = host["models"][model]
            manifest["models"].append({
                "id": model,
                "image_file": pathlib.Path(spec["image"]).name,
                "image_sha256": spec["image_sha256"],
                "text_file": pathlib.Path(spec["text"]).name,
                "text_sha256": spec["text_sha256"],
            })
        for name, (count, _) in module.DATASETS.items():
            dataset_prefix = stable_sha(f"device-input:{name}")
            manifest["datasets"].append({
                "id": name,
                "image_file": f"{name}-images.f32",
                "image_sha256": stable_sha(f"{dataset_prefix}:images"),
                "image_count": count,
                "tokens_file": f"{name}-tokens.i32",
                "tokens_sha256": token_hash(prompts[name]),
                "token_count": len(prompts[name]),
            })
        manifest_path = root / "manifest.json"
        manifest_path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

        args = SimpleNamespace(input=root, host=host_path, evaluation_manifest=evaluation_path, source_input_dir=None, parity=artifacts["parity"])
        original_digest = module.digest
        model_digests = {}

        def cached_models(path):
            if path.suffix == ".ort":
                key = str(path)
                if key not in model_digests:
                    model_digests[key] = original_digest(path)
                return model_digests[key]
            return original_digest(path)

        module.digest = cached_models
        self.addCleanup(setattr, module, "digest", original_digest)
        models, datasets, _ = module.validate_inputs(args, manifest, evaluation, host)
        checks = []

        def write_json(path, value):
            path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

        def rejects(name, func):
            try:
                func()
            except (AssertionError, KeyError, ValueError):
                checks.append(name)
            else:
                raise AssertionError(f"Accepted invalid input: {name}")

        checks.append("Synthetic model manifest and prompt-order alignment")

        def bad_input(name, change):
            value = copy.deepcopy(manifest)
            change(value)
            rejects(name, lambda: module.validate_inputs(args, value, evaluation, host))

        bad_input("Unknown schema", lambda value: value.update(schema_version=2))
        bad_input("Wrong eval manifest hash", lambda value: value.update(evaluation_manifest_sha256="0" * 64))
        bad_input("Wrong sample identities hash", lambda value: value.update(evaluation_samples_sha256="0" * 64))
        bad_input("Changed host result", lambda value: value.update(host_accuracy_sha256="0" * 64))
        bad_input("Old runtime", lambda value: value["versions"].update(onnxruntime="1.25.0"))
        bad_input("Different model hash", lambda value: value["models"][0].update(image_sha256="0" * 64))
        bad_input("Duplicate model IDs", lambda value: value["models"].append(copy.deepcopy(value["models"][0])))
        bad_input("Duplicate datasets", lambda value: value["datasets"].append(copy.deepcopy(value["datasets"][0])))
        bad_input("Wrong image count", lambda value: value["datasets"][0].update(image_count=1999))
        bad_input("Different class token order/hash", lambda value: value["datasets"][0].update(tokens_sha256="0" * 64))

        reports = {}
        for model in module.MODELS:
            report = {
                **DEVICE_IDENTITY,
                "status": "passed",
                "model_id": model,
                "runtime_version": RUNTIME,
                "cpu_threads": 4,
                "inter_op_threads": 1,
                "model_hashes_verified": True,
                "input_hashes_verified": True,
                "manifest_sha256": module.digest(manifest_path),
                "evaluation_manifest_sha256": module.digest(evaluation_path),
                "model": models[model],
                "datasets": [],
                "actual_image_sha256": models[model]["image_sha256"],
                "actual_text_sha256": models[model]["text_sha256"],
                "output_format": "Raw little-endian float32 [count,512], manifest order; no additional normalization.",
                "session_configuration": {
                    "execution_provider": "CPUExecutionProvider",
                    "intra_op_threads": 4,
                    "inter_op_threads": 1,
                    "execution_mode": "ORT_SEQUENTIAL (runtime default)",
                    "graph_optimization": "runtime default",
                },
            }
            report["image_session"] = {"runtime_version": RUNTIME, "load_ms": 1.0, "tensor_contract": IMAGE_CONTRACT}
            report["text_session"] = {"runtime_version": RUNTIME, "load_ms": 1.0, "tensor_contract": TEXT_CONTRACT}
            for name, (count, class_count) in module.DATASETS.items():
                labels = np.array([sample["label"] for sample in evaluation[name]["samples"]])
                predicted = labels.copy()
                if model == INT8:
                    predicted[:10] = (predicted[:10] + 1) % class_count
                images = np.zeros((count, 512), dtype="<f4")
                images[np.arange(count), predicted] = 3.0
                text = np.zeros((class_count, 512), dtype="<f4")
                text[np.arange(class_count), np.arange(class_count)] = 2.0
                row = copy.deepcopy(datasets[name])
                for tower, values in (("image", images), ("text", text)):
                    path = root / f"{model}-{name}-{tower}.f32"
                    values.tofile(path)
                    row[tower] = {
                        "status": "passed",
                        "file": path.name,
                        "sha256": module.digest(path),
                        "shape": list(values.shape),
                        "count": len(values),
                        "completed_count": len(values),
                        "bytes": path.stat().st_size,
                        "elapsed_ms": 0.0,
                    }
                report["datasets"].append(row)
            reports[model] = report
            write_json(root / f"{model}-report.json", report)

        run_metadata = {
            "status": "complete",
            "manifest_sha256": module.digest(manifest_path),
            "device": {
                "model": DEVICE_IDENTITY["device"],
                "sdk": str(DEVICE_IDENTITY["sdk"]),
                "fingerprint": DEVICE_IDENTITY["fingerprint"],
            },
            "runs": [],
        }
        for model, report in reports.items():
            outputs = []
            for row in report["datasets"]:
                for tower in ("image", "text"):
                    outputs.append({key: row[tower][key] for key in ("file", "sha256", "bytes", "count", "shape")})
            run_metadata["runs"].append({
                "model_id": model,
                "status": "passed",
                "instrumentation_returncode": 0,
                "report_file": f"{model}-report.json",
                "report_sha256": module.digest(root / f"{model}-report.json"),
                "outputs": outputs,
            })
        write_json(root / "run-metadata.json", run_metadata)

        result, _ = module.evaluate(args)
        for name, (count, _) in module.DATASETS.items():
            row = result["datasets"][name]
            self.assertEqual(row["metrics"][FP32]["top1_correct"], count)
            self.assertEqual(row["metrics"][INT8]["top1_correct"], count - 10)
            self.assertEqual(row["fp32_int8_prediction_flip_count"], 10)
            self.assertTrue(np.isclose(row["comparisons"][0]["b_minus_a_top1_pp"], -1000 / count))
            for model in module.MODELS:
                item = row["host_device_comparisons"][model]
                self.assertEqual(item["prediction_flip_count"], len(item["prediction_flips"]))
        self.assertEqual(result["first_input_parity"]["status"], "failed")
        checks.append("Complete synthetic 5925-image export scoring with separate parity context")

        for name, change in (
            ("Run still active", lambda value: value.update(status="running")),
            ("Run refers to different manifest", lambda value: value.update(manifest_sha256="0" * 64)),
            ("Missing model run", lambda value: value["runs"].pop()),
            ("Duplicate model run", lambda value: value["runs"].append(copy.deepcopy(value["runs"][0]))),
            ("Failed model run", lambda value: value["runs"][0].update(status="failed")),
            ("Nonzero instrumentation return", lambda value: value["runs"][0].update(instrumentation_returncode=1)),
            ("Stale completed report hash", lambda value: value["runs"][0].update(report_sha256="0" * 64)),
            ("Unexpected report file", lambda value: value["runs"][0].update(report_file=f"{INT8}-report.json")),
        ):
            value = copy.deepcopy(run_metadata)
            change(value)
            write_json(root / "run-metadata.json", value)
            try:
                rejects(name, lambda: module.validate_run(root, module.digest(manifest_path)))
            finally:
                write_json(root / "run-metadata.json", run_metadata)

        for name, change in (
            ("Run device fingerprint mismatch", lambda value: value["device"].update(fingerprint="different")),
            ("Incomplete retrieval receipt", lambda value: value["runs"][0]["outputs"].pop()),
            ("Receipt feature hash mismatch", lambda value: value["runs"][0]["outputs"][0].update(sha256="0" * 64)),
        ):
            value = copy.deepcopy(run_metadata)
            change(value)
            write_json(root / "run-metadata.json", value)
            try:
                rejects(name, lambda: module.evaluate(args))
            finally:
                write_json(root / "run-metadata.json", run_metadata)

        def validate_one(report):
            write_json(root / f"{INT8}-report.json", report)
            return module.validate_report(root, INT8, models[INT8], datasets, module.digest(manifest_path), module.digest(evaluation_path))

        def bad_report(name, change):
            value = copy.deepcopy(reports[INT8])
            change(value)
            try:
                rejects(name, lambda: validate_one(value))
            finally:
                write_json(root / f"{INT8}-report.json", reports[INT8])

        bad_report("Running export", lambda value: value.update(status="running"))
        bad_report("Wrong model ID", lambda value: value.update(model_id=FP32))
        bad_report("Input manifest mismatch", lambda value: value.update(manifest_sha256="0" * 64))
        bad_report("Missing runtime verification", lambda value: value.update(runtime_version="1.25.0"))
        bad_report("Wrong intra threads", lambda value: value.update(cpu_threads=1))
        bad_report("Wrong inter threads", lambda value: value.update(inter_op_threads=4))
        bad_report("Model hash verification false", lambda value: value.update(model_hashes_verified=False))
        bad_report("Input verification false", lambda value: value.update(input_hashes_verified=False))
        bad_report("Output format mismatch", lambda value: value.update(output_format="big endian"))
        bad_report("Device ABI mismatch", lambda value: value.update(abis=["x86_64"]))
        bad_report("Unsupported device API", lambda value: value.update(sdk=28))
        bad_report("Provider mismatch", lambda value: value["session_configuration"].update(execution_provider="CUDAExecutionProvider"))
        bad_report("Session runtime mismatch", lambda value: value["image_session"].update(runtime_version="1.25.0"))
        bad_report("Tensor contract mismatch", lambda value: value["image_session"]["tensor_contract"].update(output_shape=[1, 1024]))
        bad_report("Different actual model hash", lambda value: value.update(actual_image_sha256="0" * 64))
        bad_report("Missing dataset", lambda value: value["datasets"].pop())
        bad_report("Duplicate dataset", lambda value: value["datasets"].append(copy.deepcopy(value["datasets"][0])))
        bad_report("Wrong input tensor hash", lambda value: value["datasets"][0].update(image_sha256="0" * 64))
        bad_report("Tower still running", lambda value: value["datasets"][0]["image"].update(status="running"))
        bad_report("Incomplete processed count", lambda value: value["datasets"][0]["image"].update(completed_count=1999))
        bad_report("Wrong feature shape", lambda value: value["datasets"][0]["image"].update(shape=[2000, 511]))
        bad_report("Wrong feature SHA", lambda value: value["datasets"][0]["image"].update(sha256="0" * 64))
        bad_report("Nonfinite export timing", lambda value: value["datasets"][0]["image"].update(elapsed_ms=float("nan")))
        supported = copy.deepcopy(reports[INT8])
        supported["sdk"] = 29
        try:
            validate_one(supported)
            checks.append("Supported API 29 accepted; SDK 37 is not hardcoded")
        finally:
            write_json(root / f"{INT8}-report.json", reports[INT8])

        small = root / "small.f32"
        values = np.zeros((2, 512), dtype="<f4")
        values[:, 0] = 3
        values.tofile(small)
        record = {
            "status": "passed",
            "file": "small.f32",
            "shape": [2, 512],
            "count": 2,
            "completed_count": 2,
            "bytes": 4096,
            "sha256": module.digest(small),
            "elapsed_ms": 1,
        }
        normalized, _ = module.load_features(root, record, "small.f32", 2)
        self.assertTrue(np.array_equal(normalized[:, 0], np.ones(2)))
        self.assertTrue(np.allclose(np.linalg.norm(normalized, axis=1), 1))
        checks.append("Raw little-endian features normalized per row")
        for label, value in (("NaN feature", float("nan")), ("Infinite feature", float("inf")), ("Zero row", 0.0)):
            bad = values.copy()
            bad[0, 0] = value
            bad.tofile(small)
            updated = {**record, "sha256": module.digest(small)}
            rejects(label, lambda updated=updated: module.load_features(root, updated, "small.f32", 2))
        small.write_bytes(b"x" * 4095)
        rejects("Truncated feature bytes", lambda: module.load_features(root, record, "small.f32", 2))
        rejects("Artifact traversal", lambda: module.local_file(root, "../manifest.json"))

        with patch.object(sys, "argv", [str(BASE / "evaluate_image_int8_device.py"), "--input", str(root), "--host", str(host_path), "--evaluation-manifest", str(evaluation_path), "--parity", str(args.parity)]):
            module.main()
        written = module.read(root / "device-accuracy.json")
        self.assertEqual(written["datasets"]["cifar100"]["metrics"][INT8]["top1_correct"], 1990)
        self.assertIn("export duration", written["scope"].lower())
        self.assertTrue((root / "README_zh.md").is_file())
        module.validate_bilingual_tables((root / "README.md").read_text(encoding="utf-8"), (root / "README_zh.md").read_text(encoding="utf-8"))
        self.assertTrue(written["shared_text_outputs_byte_identical"])
        checks.append("CLI writes bilingual reports and independent device JSON without mixing latency")

        bad_text = copy.deepcopy(result["export_reports"])
        bad_text[INT8]["outputs"]["cifar100"]["text"]["sha256"] = "0" * 64
        rejects("Different shared text output bytes", lambda: module.validate_shared_text_outputs(bad_text))
        write_json(root / "verification.json", {
            "status": "passed",
            "checks": checks,
            "note": "Synthetic features only. Source, model and prompt integrity are validated against generated fixtures; no device execution occurred.",
        })


if __name__ == "__main__":
    unittest.main()
