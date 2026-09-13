#!/usr/bin/env python3
"""Measure Android CPU parallelism with default, explicit and disabled XNNPACK."""
import argparse
import json
import subprocess
import time
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--serial", required=True)
    parser.add_argument("--output", type=Path, default=Path("script/model-MobileCLIP2/results/pixel8a-tflite-diagnostics"))
    parser.add_argument("--samples", type=int, default=30)
    parser.add_argument("--cases", nargs="+", help="Optional subset, including native1/native2/native4/native8")
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    package = "me.grey.picquery.mobileclip2.onnx"
    adb = [args.adb, "-s", args.serial]

    def call(*parts):
        return subprocess.check_output(adb + list(parts), text=True, encoding="utf-8", errors="replace")

    cases = [
        ("default4", "v2_s0_tflite_int8", 4, "default"),
        ("xnn1", "v2_s0_tflite_int8", 1, "true"),
        ("xnn4", "v2_s0_tflite_int8", 4, "true"),
        ("xnn8", "v2_s0_tflite_int8", 8, "true"),
        ("builtin4", "v2_s0_tflite_int8", 4, "false"),
        ("onnx1", "v2_s0_onnx_int8", 1, "default"),
        ("onnx4", "v2_s0_onnx_int8", 4, "default"),
    ]
    if args.cases:
        available = {row[0]: row for row in cases + [
            (f"native{n}", "v2_s0_tflite_int8", n, "native") for n in (1, 2, 4, 8)
        ]}
        cases = [available[name] for name in args.cases]
    for index, (name, model, threads, xnnpack) in enumerate(cases):
        if index:
            time.sleep(10)
        call("shell", "am", "force-stop", package)
        arguments = ["shell", "am", "instrument", "-w", "-r", "-e", "class",
                     "me.grey.picquery.feature.MobileCLIPVersionsBenchmarkTest#benchmarkModel"]
        for key, value in {"modelId": model, "roundId": name, "sampleCount": args.samples,
                           "warmupCount": 10, "sustainSeconds": 0, "cpuThreads": threads, "xnnpack": xnnpack}.items():
            arguments += ["-e", key, str(value)]
        arguments += [package + ".test/androidx.test.runner.AndroidJUnitRunner"]
        print(f"START {name}", flush=True)
        with (args.output / f"{name}-instrumentation.log").open("w", encoding="utf-8") as log:
            process = subprocess.Popen(adb + arguments, stdout=log, stderr=subprocess.STDOUT)
            pid = ""
            deadline = time.monotonic() + 10
            while process.poll() is None and time.monotonic() < deadline and not pid:
                probe = subprocess.run(adb + ["shell", "pidof", package], capture_output=True, text=True)
                pid = probe.stdout.strip()
                if not pid:
                    time.sleep(0.2)
            process.wait(timeout=180)
        raw = call("exec-out", "run-as", package, "cat", f"files/mobileclip-benchmark-{model}-{name}.json")
        row = json.loads(raw)
        (args.output / f"{name}.json").write_text(raw, encoding="utf-8")
        if pid.isdigit():
            logcat = call("logcat", "-d", "--pid", pid, "-v", "threadtime", "-s", "tflite")
            (args.output / f"{name}-delegate.log").write_text(logcat, encoding="utf-8")
        assert process.returncode == 0 and row["status"] == "passed"
        assert "OK (1 test)" in (args.output / f"{name}-instrumentation.log").read_text()
        assert row["requested_xnnpack"] == xnnpack and row["cpu_threads"] == threads
        for tower in ("image", "text"):
            measured = row[tower]
            assert measured["summary"]["count"] == args.samples and len(measured["first_output"]) == 512
            print(f"  {tower}: P50={measured['summary']['p50_ms']:.2f}ms, CPU/wall={measured['cpu_parallelism']:.2f}", flush=True)
        call("shell", "am", "force-stop", package)
    print("COMPLETE", flush=True)


if __name__ == "__main__":
    main()
