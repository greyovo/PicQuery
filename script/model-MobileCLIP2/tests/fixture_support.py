"""Small synthetic artifact fixtures; no model inference, downloads or devices.

Archived labels/predictions provide realistic class balance. Model files are
explicitly fake bytes: these tests exercise report validation, never runtimes.
"""
import hashlib
import json
from pathlib import Path

BASE = Path(__file__).resolve().parents[1]
REPO = BASE.parents[1]
ARCHIVE = BASE / "results" / "image-int8"


def read(path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def write(path, value):
    path.write_text(json.dumps(value, ensure_ascii=False), encoding="utf-8")


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def make_artifacts(root):
    """Relocate archive references to temporary fake models and real small JSON."""
    host = read(ARCHIVE / "accuracy.json")
    host["host"] = "SYNTHETIC OFFLINE TEST - NOT MEASUREMENTS"
    relocated = {}
    for spec in host["models"].values():
        for tower in ("image", "text"):
            original = spec[tower]
            if original not in relocated:
                path = root / Path(original).name
                path.write_bytes(("FAKE MODEL FOR VALIDATOR TEST: " + original).encode())
                relocated[original] = path
            path = relocated[original]
            spec[tower], spec[tower + "_sha256"] = str(path), digest(path)
        spec["total_bytes"] = sum(Path(spec[t]).stat().st_size for t in ("image", "text"))
    host_path = root / "source-host.json"
    write(host_path, host)
    evaluation_path = ARCHIVE / "accuracy-manifest.json"

    quant = read(ARCHIVE / "quantization-metadata.json")
    fp32, int8 = (host["models"][name] for name in ("v2_s0_fp32_image_ort", "v2_s0_int8_image_ort"))
    for record, spec, tower in ((quant["source_fp32_ort"], fp32, "image"),
                                (quant["image"]["ort"], int8, "image"),
                                (quant["shared_text"]["ort"], fp32, "text")):
        path = Path(spec[tower])
        record.update(file=str(path), sha256=digest(path), bytes=path.stat().st_size)
    for name, record in (("source", quant["source"]), ("quantized", quant["image"]["onnx"])):
        path = root / f"{name}.onnx"
        path.write_bytes(f"FAKE ONNX {name}".encode())
        record.update(file=str(path), sha256=digest(path), bytes=path.stat().st_size)
    calibration = read(ARCHIVE / "calibration-manifest.json")
    source = BASE / "evaluate_accuracy.py"
    calibration["preprocess"].update(source_file=str(source), source_file_sha256=digest(source))
    calibration_path = root / "calibration_manifest.json"
    write(calibration_path, calibration)
    quant["calibration"].update(manifest_file=str(calibration_path), manifest_sha256=digest(calibration_path))
    quant["calibration"]["leakage_check"].update(evaluation_manifest_file=str(evaluation_path),
                                                evaluation_manifest_sha256=digest(evaluation_path))
    quant_path = root / "quantization_metadata.json"
    write(quant_path, quant)

    device_manifest = read(ARCHIVE / "pixel8a-accuracy" / "manifest.json")
    device_manifest["host_accuracy_sha256"] = digest(host_path)
    for row in device_manifest["models"]:
        spec = host["models"][row["id"]]
        for tower in ("image", "text"):
            row[tower + "_sha256"] = spec[tower + "_sha256"]
    write(root / "manifest.json", device_manifest)

    parity = read(ARCHIVE / "pixel8a" / "host-device-parity.json")
    # Preserve the failed parity diagnostic while binding it to synthetic models.
    parity["input_report_sha256"] = {}
    for row in parity["results"]:
        for tower in ("image", "text"):
            row["towers"][tower]["model_sha256"] = host["models"][row["model_id"]][tower + "_sha256"]
    parity_path = root / "parity.json"
    write(parity_path, parity)
    return {"host": host_path, "evaluation": evaluation_path, "quantization": quant_path, "parity": parity_path}
