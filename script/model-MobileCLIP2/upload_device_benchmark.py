#!/usr/bin/env python3
"""Copy benchmark files into the debug app's private directory without media permissions."""
import argparse
import hashlib
import json
import re
import subprocess
import uuid
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--serial", required=True)
    parser.add_argument("--package", default="me.grey.picquery.mobileclip2.onnx")
    parser.add_argument("--manifest", type=Path, default=Path("build/mobileclip-device-benchmark/upload-list.json"))
    parser.add_argument("--directory", choices=("mobileclip-benchmark", "mobileclip-accuracy"), default="mobileclip-benchmark")
    args = parser.parse_args()
    if not re.fullmatch(r"[A-Za-z0-9_.]+", args.package):
        parser.error("Invalid package name")
    adb = [args.adb, "-s", args.serial]
    root = f"files/{args.directory}"
    subprocess.run(adb + ["shell", "run-as", args.package, "mkdir", "-p", root], check=True)
    uploads = json.loads(args.manifest.read_text(encoding="utf-8-sig"))
    for item in uploads:
        name = item["destination"]
        if not re.fullmatch(r"[A-Za-z0-9_.-]+", name) or name in (".", ".."):
            raise ValueError(f"Invalid destination filename: {name}")
        source = Path(item["source"])
        # ADB sync is binary-safe on Windows and waits for the complete transfer.
        # exec-in can return before dd finishes; shell stdin can truncate at Ctrl-Z.
        staging = f"/data/local/tmp/picquery-benchmark-{uuid.uuid4().hex}"
        try:
            subprocess.run(adb + ["push", str(source), staging], capture_output=True, check=True)
            subprocess.run(adb + ["shell", "run-as", args.package, "cp", staging, f"{root}/{name}"],
                           capture_output=True, check=True)
        finally:
            subprocess.run(adb + ["shell", "rm", "-f", staging], capture_output=True, check=True)
        size = subprocess.check_output(adb + ["shell", "run-as", args.package, "stat", "-c", "%s", f"{root}/{name}"])
        if int(size) != source.stat().st_size:
            raise RuntimeError(f"Upload size mismatch: {name}; got {int(size)}, expected {source.stat().st_size}")
        with source.open("rb") as stream:
            expected_hash = hashlib.file_digest(stream, "sha256").hexdigest()
        actual_hash = subprocess.check_output(
            adb + ["shell", "run-as", args.package, "sha256sum", f"{root}/{name}"]
        ).decode().split()[0]
        if actual_hash != expected_hash:
            raise RuntimeError(f"Upload SHA256 mismatch: {name}")
        print(f"Uploaded {name}: {int(size):,} bytes", flush=True)
    print("Upload complete; instrumentation independently verifies model and input SHA256.")


if __name__ == "__main__":
    main()
