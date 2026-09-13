#!/usr/bin/env python3
"""Check frozen image-INT8 benchmark first outputs without loading any model/device.

Run after run-metadata.json reports complete. Exit 2 leaves the parity report
untouched while benchmarks are incomplete; exit 1 records a failed completed run.
Only cosine is an acceptance threshold. Raw embedding norms and absolute errors
remain visible, including for the unnormalized legacy CLIP outputs.
"""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
import hashlib
import json
import math
import os
from pathlib import Path
import sys
import tempfile


DEFAULT_RESULTS = Path(__file__).resolve().parents[2] / "build/mobileclip2-image-int8-results/pixel8a"
MODEL_IDS = ("v2_s0_fp32_image_ort", "v2_s0_int8_image_ort", "legacy_clip_int8")
FROZEN_IMAGE_SHA256 = "d5b21e38a87cfeb802ffda26051cd7e59ba6e5ea9c6f10fd1d36780067afe031"
RUNTIME = "1.29.0"
THREADS = 4
THRESHOLDS = {"image": 0.9999, "text": 0.995}


def require(condition, message):
    if not condition:
        raise ValueError(message)


def read_json(path, provenance):
    data = path.read_bytes()
    provenance[path.name] = hashlib.sha256(data).hexdigest()
    return json.loads(data.decode("utf-8-sig"))


def checked_hash(value, label):
    require(isinstance(value, str) and len(value) == 64
            and all(c in "0123456789abcdefABCDEF" for c in value), f"Invalid SHA256: {label}")
    return value.lower()


def matching_hash(expected, actual, label):
    require(checked_hash(expected, label) == checked_hash(actual, label), f"SHA256 mismatch: {label}")


def vector(value, label):
    require(isinstance(value, list) and len(value) == 512, f"Expected 512 values: {label}")
    require(all(isinstance(x, (int, float)) and not isinstance(x, bool)
                and math.isfinite(x) for x in value), f"Non-finite/non-numeric output: {label}")
    norm = math.hypot(*value)
    require(math.isfinite(norm) and norm > 0, f"Zero or invalid output norm: {label}")
    return value, norm


def compare(first, second, label):
    left, left_norm = vector(first, label + " first")
    right, right_norm = vector(second, label + " second")
    cosine = math.fsum((a / left_norm) * (b / right_norm) for a, b in zip(left, right))
    return {"cosine": max(-1.0, min(1.0, cosine)),
            "max_absolute_error": max(abs(a - b) for a, b in zip(left, right)),
            "normalized_max_absolute_error": max(abs(a / left_norm - b / right_norm)
                                                  for a, b in zip(left, right)),
            "first_norm": left_norm, "second_norm": right_norm}


def check_complete_run(directory, run, report):
    provenance = report["input_report_sha256"]
    host = read_json(directory / "host-reference.json", provenance)
    manifest = read_json(directory / "fixture-manifest.json", provenance)
    require(host["runtime"] == RUNTIME and host["provider"] == "CPUExecutionProvider"
            and host["threads"] == THREADS, "Host CPU/runtime configuration mismatch")
    require(set(host["models"]) == set(MODEL_IDS), "Unexpected host model set")
    require(run["rounds"] == 2, "Expected two completed benchmark rounds")
    require(len(manifest["models"]) == len(MODEL_IDS), "Unexpected fixture model count")
    models = {model["id"]: model for model in manifest["models"]}
    require(set(models) == set(MODEL_IDS), "Unexpected or duplicate fixture model IDs")
    matching_hash(FROZEN_IMAGE_SHA256, models[MODEL_IDS[1]]["image_sha256"], "frozen mixed image")
    matching_hash(manifest["tokens_sha256"], host["tokens_sha256"], "host tokens")
    require(models[MODEL_IDS[0]]["preprocess"] == models[MODEL_IDS[1]]["preprocess"],
            "FP32/mixed preprocessing differs")
    for field in ("image_input_sha256", "text_sha256"):
        matching_hash(models[MODEL_IDS[0]][field], models[MODEL_IDS[1]][field], "FP32/mixed " + field)

    expected_runs = {(model_id, number) for model_id in MODEL_IDS for number in (1, 2)}
    actual_runs = [(entry["model"], entry["round"]) for entry in run["runs"]]
    require(len(actual_runs) == 6 and set(actual_runs) == expected_runs, "Missing/duplicate benchmark runs")
    report["device"] = {key: run[key] for key in
                        ("device_serial", "model", "soc", "android", "fingerprint", "page_size", "package")}
    report["configuration"] = {"runtime": RUNTIME, "cpu_threads": THREADS,
                               "host_provider": host["provider"], "rounds": 2,
                               "device_provider_evidence": "backend=onnx and the benchmark's CPU-only session configuration; provider list is not recorded in device JSON"}
    vectors = {}
    for entry in run["runs"]:
        model_id, number = entry["model"], entry["round"]
        expected_name = f"{model_id}-r{number}.json"
        require(entry["report"] == expected_name, "Unexpected benchmark report path")
        phone = read_json(directory / expected_name, provenance)
        model, reference = models[model_id], host["models"][model_id]
        require(phone["status"] == "passed" and phone["model_id"] == model_id
                and phone["round_id"] == f"r{number}", f"Unsuccessful/mismatched report: {expected_name}")
        require(model["backend"] == phone["backend"] == "onnx" and model["format"] == "ort",
                f"Unexpected backend/format: {expected_name}")
        require(phone["runtime_version"] == RUNTIME and phone["cpu_threads"] == THREADS
                and phone["requested_xnnpack"] == "default", f"Device CPU/runtime mismatch: {expected_name}")
        require(phone["fingerprint"] == run["fingerprint"] and phone["device"] == run["model"],
                f"Device identity mismatch: {expected_name}")
        require(phone["model_hashes_verified"] is True and phone["input_hashes_verified"] is True,
                f"Device did not verify binary hashes: {expected_name}")
        require(phone["sample_count"] == run["sample_count"] and phone["warmup_count"] == run["warmup_count"],
                f"Sampling configuration mismatch: {expected_name}")
        require(phone["preloaded_image_count"] == model["image_count"]
                and phone["preloaded_text_count"] == manifest["token_count"],
                f"Preloaded fixture count mismatch: {expected_name}")
        matching_hash(manifest["tokens_sha256"], phone["tokens_sha256"], expected_name + " tokens")
        matching_hash(model["image_input_sha256"], reference["image_input_sha256"], model_id + " host image input")
        matching_hash(model["image_input_sha256"], phone["image_input_sha256"], expected_name + " image input")
        row = {"model_id": model_id, "round_id": f"r{number}", "device_report": expected_name,
               "image_input_sha256": model["image_input_sha256"], "tokens_sha256": manifest["tokens_sha256"],
               "towers": {}}
        for tower in ("image", "text"):
            matching_hash(model[tower + "_sha256"], reference[tower]["sha256"], model_id + " host " + tower)
            matching_hash(model[tower + "_sha256"], phone["actual_" + tower + "_sha256"], expected_name + " " + tower)
            contract = phone[tower]["tensor_contract"]
            expected_shape = [1, 3, model["input_size"], model["input_size"]] if tower == "image" else [1, 77]
            allowed_types = ("FLOAT",) if tower == "image" else ("INT32", "INT64")
            require(contract["input_shape"] == expected_shape and contract["input_type"] in allowed_types
                    and contract["output_type"] == "FLOAT32", f"Tensor contract mismatch: {expected_name} {tower}")
            output = phone[tower]["first_output"]
            metrics = compare(reference[tower]["output"], output, expected_name + " " + tower)
            metrics["host_norm"] = metrics.pop("first_norm")
            metrics["phone_norm"] = metrics.pop("second_norm")
            metrics.update({"model_sha256": model[tower + "_sha256"], "cosine_minimum": THRESHOLDS[tower],
                            "status": "passed" if metrics["cosine"] >= THRESHOLDS[tower] else "failed"})
            row["towers"][tower] = metrics
            vectors[(model_id, number, tower)] = output
        report["results"].append(row)

    for number in (1, 2):
        metrics = compare(vectors[(MODEL_IDS[0], number, "image")], vectors[(MODEL_IDS[1], number, "image")],
                          f"FP32/mixed phone image round {number}")
        metrics["fp32_norm"] = metrics.pop("first_norm")
        metrics["mixed_norm"] = metrics.pop("second_norm")
        report["fp32_vs_mixed_image"].append({"round_id": f"r{number}", "acceptance_gate": False, **metrics})
    host_comparison = compare(host["models"][MODEL_IDS[0]]["image"]["output"],
                              host["models"][MODEL_IDS[1]]["image"]["output"], "FP32/mixed host image")
    report["host_fp32_vs_mixed_image"] = {"acceptance_gate": False, **host_comparison}
    metrics = [row["towers"][tower] for row in report["results"] for tower in ("image", "text")]
    report["status"] = "passed" if all(row["status"] == "passed" for row in metrics) else "failed"
    report["checked_runs"] = len(report["results"])
    report["checked_first_outputs"] = len(metrics)
    report["minimum_cosine"] = {tower: min(row["towers"][tower]["cosine"] for row in report["results"])
                                for tower in ("image", "text")}


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--results-dir", type=Path, default=DEFAULT_RESULTS)
    args = parser.parse_args(argv)
    directory = args.results_dir.resolve()
    report = {"status": "failed", "created_at_utc": datetime.now(timezone.utc).isoformat(),
              "thresholds": {"host_phone_image_cosine_minimum": THRESHOLDS["image"],
                             "host_phone_text_cosine_minimum": THRESHOLDS["text"],
                             "max_absolute_error_is_descriptive": True},
              "scope": "Only the first preloaded image and first prompt per tower/round; no model or device execution.",
              "hash_validation": "Cross-checks manifest, host and device-recorded binary hashes; device benchmark asserts actual model/input SHA256. This script hashes the consumed JSON reports only.",
              "limits": ["Dynamic INT8 text may differ across host and phone kernels.",
                         "Cosine normalizes both vectors; legacy raw embeddings need not have unit norm.",
                         "FP32-versus-mixed differences are descriptive, not a same-model parity threshold.",
                         "This is not full-dataset accuracy, throughput, or 16 KB page-size validation."],
              "input_report_sha256": {}, "results": [], "fp32_vs_mixed_image": []}
    try:
        run = read_json(directory / "run-metadata.json", report["input_report_sha256"])
    except (OSError, ValueError) as error:
        print(f"Benchmark metadata is not ready: {error}", file=sys.stderr)
        return 2
    if run.get("status") != "complete":
        print("Benchmark is not complete; no parity report written.", file=sys.stderr)
        return 2
    try:
        check_complete_run(directory, run, report)
    except (OSError, ValueError, KeyError, TypeError) as error:
        report["failure"] = str(error)
    destination = directory / "host-device-parity.json"
    with tempfile.NamedTemporaryFile(mode="w", encoding="utf-8", dir=directory,
                                     prefix=".host-device-parity-", suffix=".tmp", delete=False) as stream:
        temporary = Path(stream.name)
        json.dump(report, stream, indent=2, allow_nan=False)
        stream.write("\n")
    try:
        os.replace(temporary, destination)
    finally:
        temporary.unlink(missing_ok=True)
    print(f"{report['status']}: {destination}")
    if "failure" in report:
        print(report["failure"], file=sys.stderr)
    elif "minimum_cosine" in report:
        print(json.dumps(report["minimum_cosine"]))
    return 0 if report["status"] == "passed" else 1


if __name__ == "__main__":
    sys.exit(main())
