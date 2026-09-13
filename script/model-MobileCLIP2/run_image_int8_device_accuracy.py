#!/usr/bin/env python3
"""Run installed Android accuracy instrumentation and retrieve verified FP32 features.

Windows-safe standard-library runner. Fixtures and both APKs must already be on
the selected device. This script never uploads files, installs APKs, or uses UI.
"""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import re
import shlex
import subprocess
import sys
import time


ROOT = Path(__file__).resolve().parents[2]
PACKAGE = "me.grey.picquery.mobileclip2.onnx"
TEST_CLASS = "me.grey.picquery.feature.MobileCLIPVersionsBenchmarkTest#exportAccuracyFeatures"
REMOTE_OUTPUT = "files/mobileclip-accuracy-output"
MODEL_IDS = {"v2_s0_fp32_image_ort", "v2_s0_int8_image_ort"}


def utc_now():
    return datetime.now(timezone.utc).isoformat()


def require(condition, message):
    if not condition:
        raise ValueError(message)


def basename(value):
    require(isinstance(value, str) and re.fullmatch(r"[A-Za-z0-9_.-]+", value)
            and value not in (".", ".."), f"Invalid fixed basename: {value!r}")
    return value


def count(value, label):
    require(type(value) is int and value > 0, f"Invalid count: {label}")
    return value


def same_hash(actual, expected, label):
    require(isinstance(actual, str) and re.fullmatch(r"[A-Fa-f0-9]{64}", actual)
            and isinstance(expected, str) and re.fullmatch(r"[A-Fa-f0-9]{64}", expected)
            and actual.lower() == expected.lower(), f"SHA256 mismatch: {label}")


def digest(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def save_json(path, value):
    temporary = path.with_name(path.name + ".tmp")
    temporary.write_text(json.dumps(value, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    temporary.replace(path)


class Device:
    def __init__(self, adb, serial):
        self.command = [adb, "-s", serial]

    def text(self, *arguments):
        result = subprocess.run(self.command + list(arguments), stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        if result.returncode:
            raise RuntimeError(f"ADB command failed ({result.returncode}): {' '.join(arguments)}\n"
                               + result.stderr.decode("utf-8", errors="replace"))
        return result.stdout.decode("utf-8", errors="replace").strip()

    def retrieve(self, name, destination):
        # Pass stdout directly to a binary file. No PowerShell/text decoding is involved.
        remote = f"{REMOTE_OUTPUT}/{basename(name)}"
        with destination.open("wb") as stream:
            result = subprocess.run(self.command + ["exec-out", "run-as", PACKAGE, "cat", remote],
                                    stdout=stream, stderr=subprocess.PIPE)
        if result.returncode:
            raise RuntimeError(f"Cannot retrieve {remote}: " + result.stderr.decode("utf-8", errors="replace"))

    def apk_identity(self, package):
        try:
            lines = self.text("shell", "pm", "path", package).splitlines()
            require(lines and all(line.startswith("package:") for line in lines), f"APK path unavailable: {package}")
            records = []
            for line in lines:
                path = line.removeprefix("package:")
                require(path.startswith("/") and path.endswith(".apk"), "Unexpected installed APK path")
                checksum = self.text("shell", "sha256sum", shlex.quote(path)).split()[0]
                same_hash(checksum, checksum, "installed APK")
                records.append({"path": path, "sha256": checksum})
            return {"status": "recorded", "files": records}
        except (OSError, RuntimeError, ValueError, IndexError) as error:
            return {"status": "unavailable", "reason": str(error)}


def collect_model(device, model, manifest, manifest_sha, output, progress):
    model_id = model["id"]
    log_path = output / f"{model_id}-instrumentation.log"
    progress.update({"model_id": model_id, "status": "running", "started_utc": utc_now(),
                     "instrumentation_log": log_path.name})
    print(f"START {model_id}: streaming accuracy features", flush=True)
    started = time.monotonic()
    with log_path.open("wb") as stream:
        execution = subprocess.run(device.command + [
            "shell", "am", "instrument", "-w", "-r", "-e", "class", TEST_CLASS,
            "-e", "modelId", model_id, f"{PACKAGE}.test/androidx.test.runner.AndroidJUnitRunner"
        ], stdout=stream, stderr=subprocess.STDOUT)
    progress["instrumentation_elapsed_seconds"] = time.monotonic() - started
    progress["instrumentation_returncode"] = execution.returncode
    log = log_path.read_text(encoding="utf-8", errors="replace")
    success = re.search(r"(?m)^OK\s+\(1 test\)\s*$", log) is not None
    failure = re.search(r"FAILURES!!!|INSTRUMENTATION_FAILED|INSTRUMENTATION_ABORTED|shortMsg=Process crashed"
                        r"|(?m:^INSTRUMENTATION_STATUS_CODE:\s*-[12]\s*$)", log) is not None
    # Retrieve the report even after a test failure, so device diagnostics are retained.
    report_path = output / f"{model_id}-report.json"
    device.retrieve("report.json", report_path)
    progress["report_file"] = report_path.name
    progress["report_sha256"] = digest(report_path)
    report = json.loads(report_path.read_text(encoding="utf-8-sig"))
    require(execution.returncode == 0 and success and not failure,
            f"Instrumentation did not pass for {model_id}; inspect {log_path.name} and {report_path.name}")
    require(report["status"] == "passed" and report["model_id"] == model_id, f"Invalid device report: {model_id}")
    require(report["runtime_version"] == "1.29.0" and report["cpu_threads"] == 4
            and report["inter_op_threads"] == 1, f"Unexpected runtime/CPU configuration: {model_id}")
    same_hash(report["manifest_sha256"], manifest_sha, "device input manifest")
    for tower in ("image", "text"):
        same_hash(report["actual_" + tower + "_sha256"], model[tower + "_sha256"], model_id + " " + tower)
    if "evaluation_manifest_sha256" in manifest:
        same_hash(report["evaluation_manifest_sha256"], manifest["evaluation_manifest_sha256"], "evaluation manifest")
    datasets = manifest["datasets"]
    expected = {row["id"]: row for row in datasets}
    rows = report["datasets"]
    require(isinstance(rows, list) and len(rows) == len(expected)
            and {row["id"] for row in rows} == set(expected), "Missing/duplicate output datasets")
    progress["outputs"] = []
    for row in rows:
        dataset_id = basename(row["id"])
        source = expected[dataset_id]
        for key in ("image_file", "image_count", "tokens_file", "token_count"):
            require(row[key] == source[key], f"Input contract mismatch: {dataset_id} {key}")
        for key in ("image_sha256", "tokens_sha256"):
            same_hash(row[key], source[key], dataset_id + " " + key)
        for tower, count_key in (("image", "image_count"), ("text", "token_count")):
            record = row[tower]
            feature_count = count(source[count_key], dataset_id + " " + count_key)
            expected_name = f"{model_id}-{dataset_id}-{tower}.f32"
            require(basename(record["file"]) == expected_name, "Unexpected output filename")
            require(record["status"] == "passed" and type(record["count"]) is int
                    and record["count"] == feature_count and record["shape"] == [feature_count, 512],
                    f"Output count/shape mismatch: {expected_name}")
            expected_bytes = feature_count * 512 * 4
            require(type(record["bytes"]) is int and record["bytes"] == expected_bytes,
                    f"Declared output bytes mismatch: {expected_name}")
            destination = output / expected_name
            device.retrieve(expected_name, destination)
            actual_bytes = destination.stat().st_size
            require(actual_bytes == expected_bytes, f"Retrieved output bytes/count mismatch: {expected_name}")
            actual_sha = digest(destination)
            same_hash(actual_sha, record["sha256"], expected_name)
            progress["outputs"].append({"dataset": dataset_id, "tower": tower, "file": expected_name,
                                        "sha256": actual_sha, "bytes": actual_bytes, "count": feature_count,
                                        "shape": [feature_count, 512]})
            print(f"VERIFIED {expected_name}: {feature_count} x 512 float32, {actual_bytes:,} bytes", flush=True)
    progress.update({"status": "passed", "finished_utc": utc_now(),
                     "runtime_version": report["runtime_version"], "cpu_threads": report["cpu_threads"],
                     "inter_op_threads": report["inter_op_threads"],
                     "session_configuration": report.get("session_configuration"),
                     "device": report.get("device"), "sdk": report.get("sdk")})


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", required=True, help="Path to adb executable")
    parser.add_argument("--serial", required=True, help="Explicit authorized Android device serial")
    parser.add_argument("--fixtures", type=Path, default=ROOT / "build/mobileclip2-image-int8-device-accuracy")
    parser.add_argument("--output", type=Path, default=Path(__file__).resolve().parent / "results/image-int8/pixel8a-accuracy")
    args = parser.parse_args(argv)
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=True)
    metadata_path = output / "run-metadata.json"
    metadata = {"status": "running", "started_utc": utc_now(), "device_serial": args.serial,
                "package": PACKAGE, "test_class": TEST_CLASS, "script_sha256": digest(Path(__file__)),
                "note": "Preinstalled APKs and uploaded fixtures required. Features are retrieved through binary stdout and verified by SHA256, byte size and row count. No accuracy score is computed here.",
                "runs": []}
    save_json(metadata_path, metadata)
    try:
        manifest_data = (args.fixtures / "manifest.json").read_bytes()
        manifest = json.loads(manifest_data.decode("utf-8-sig"))
        require(manifest["schema_version"] == 1, "Unsupported fixture manifest schema")
        models = manifest["models"]
        require(len(models) == 2 and {row["id"] for row in models} == MODEL_IDS, "Expected the FP32 and mixed-INT8 model pair")
        for model in models:
            basename(model["id"])
            for tower in ("image", "text"):
                basename(model[tower + "_file"])
                same_hash(model[tower + "_sha256"], model[tower + "_sha256"], "fixture model")
        datasets = manifest["datasets"]
        require(len(datasets) == 2 and {row["id"] for row in datasets} == {"cifar100", "imagenette"},
                "Expected CIFAR-100 and Imagenette datasets")
        for row in datasets:
            for key in ("id", "image_file", "tokens_file"):
                basename(row[key])
            for key in ("image_count", "token_count"):
                count(row[key], key)
            for key in ("image_sha256", "tokens_sha256"):
                same_hash(row[key], row[key], "fixture inputs")
        (output / "manifest.json").write_bytes(manifest_data)
        metadata["manifest_sha256"] = hashlib.sha256(manifest_data).hexdigest()
        metadata["fixtures"] = str(args.fixtures.resolve())
        device = Device(args.adb, args.serial)
        require(device.text("get-state") == "device", "Selected Android device is not ready")
        metadata["device"] = {key: device.text("shell", "getprop", prop) for key, prop in {
            "model": "ro.product.model", "soc": "ro.soc.model", "android": "ro.build.version.release",
            "sdk": "ro.build.version.sdk", "fingerprint": "ro.build.fingerprint"}.items()}
        metadata["installed_apks"] = {package: device.apk_identity(package) for package in (PACKAGE, PACKAGE + ".test")}
        save_json(metadata_path, metadata)
        for model in models:
            progress = {"model_id": model["id"], "status": "running"}
            metadata["runs"].append(progress)
            save_json(metadata_path, metadata)
            collect_model(device, model, manifest, metadata["manifest_sha256"], output, progress)
            save_json(metadata_path, metadata)
        metadata.update({"status": "complete", "completed_utc": utc_now()})
        save_json(metadata_path, metadata)
        print(f"COMPLETE: verified two model pairs; outputs in {output}", flush=True)
        return 0
    except (OSError, ValueError, RuntimeError, KeyError, TypeError, subprocess.SubprocessError) as error:
        metadata.update({"status": "failed", "failed_utc": utc_now(), "failure": str(error)})
        if metadata["runs"] and metadata["runs"][-1]["status"] == "running":
            metadata["runs"][-1].update({"status": "failed", "failure": str(error)})
        save_json(metadata_path, metadata)
        print(f"FAILED: {error}\nInspect {metadata_path}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
