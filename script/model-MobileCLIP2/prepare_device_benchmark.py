#!/usr/bin/env python3
"""Prepare exact host-tested model/input manifest for Android instrumentation."""
import argparse
import hashlib
import json
from pathlib import Path

import numpy as np
import open_clip
from PIL import Image

from benchmark_mobileclip2 import DEFAULT_TEXTS, digest
from evaluate_accuracy import preprocess


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=Path("build/mobileclip-device-benchmark"))
    parser.add_argument("--models", type=Path, default=Path("script/model-MobileCLIP2/accuracy_models.json"))
    parser.add_argument("--fixtures", type=Path, default=Path("build/mobileclip2-fixtures"))
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    configs = json.loads(args.models.read_text(encoding="utf-8-sig"))
    configs = [c for c in configs if "control" not in c["id"]]
    if not configs or len({c["id"] for c in configs}) != len(configs):
        parser.error("The model configuration must contain distinct, nonempty model IDs")
    by_id = {c["id"]: c for c in configs}
    fixture_root = args.fixtures
    fixture_names = ["dog1.jpg", "dog2.jpg", "astronaut.jpg", "leaning_tower.jpg", "pottery.jpg"]
    photos = []
    for name in fixture_names:
        with Image.open(fixture_root / name) as image:
            photos.append(image.convert("RGB"))
    tokens = open_clip.get_tokenizer("MobileCLIP2-S0")(DEFAULT_TEXTS).numpy().astype("<i4")
    token_file = args.output / "tokens_13x77.i32"
    token_file.write_bytes(tokens.tobytes())
    manifest = {
        "tokens_file": token_file.name, "token_count": len(tokens), "tokens_sha256": digest(token_file),
        "prompts": DEFAULT_TEXTS, "fixture_sha256": {p: digest(fixture_root / p) for p in fixture_names},
        "models_config_sha256": digest(args.models),
        "models": [],
    }
    uploads = {token_file.name: str(token_file)}
    inputs = {}
    for spec in configs:
        signature = json.dumps(spec["preprocess"], sort_keys=True)
        if signature not in inputs:
            signature_hash = hashlib.sha256(signature.encode()).hexdigest()
            path = args.output / f"images_{spec['preprocess']['size']}_{signature_hash}.f32"
            values = np.concatenate([preprocess(p, spec["preprocess"]) for p in photos])
            path.write_bytes(values.astype("<f4").tobytes())
            inputs[signature] = path
            uploads[path.name] = str(path)
        image_path, text_path = Path(spec["image"]), Path(spec["text"])
        model_format = image_path.suffix[1:]
        if model_format not in ("onnx", "ort", "tflite") or text_path.suffix != image_path.suffix:
            raise ValueError(f"Unsupported or mixed model formats: {spec['id']}")
        if spec.get("format", model_format) != model_format:
            raise ValueError(f"Declared format disagrees with model files: {spec['id']}")
        source_id = spec.get("source_model_id", spec["id"])
        if source_id not in by_id:
            raise ValueError(f"Missing source model: {source_id}")
        row = {
            "id": spec["id"], "label": spec["label"],
            "backend": "tflite" if image_path.suffix == ".tflite" else "onnx",
            "format": model_format, "source_model_id": source_id,
            "image_file": image_path.name, "text_file": text_path.name,
            "image_sha256": digest(image_path), "text_sha256": digest(text_path),
            "image_file_bytes": image_path.stat().st_size, "text_file_bytes": text_path.stat().st_size,
            "input_size": spec["preprocess"]["size"], "image_count": len(photos),
            "image_input_file": inputs[signature].name,
            "image_input_sha256": digest(inputs[signature]),
            "preprocess": spec["preprocess"], "precision": spec["label"],
        }
        if source_id != spec["id"]:
            source = by_id[source_id]
            if model_format != "ort" or spec["preprocess"] != source["preprocess"]:
                raise ValueError("A format comparison must preserve source preprocessing")
            metadata_path = Path(spec["export_metadata"])
            metadata = json.loads(metadata_path.read_text(encoding="utf-8-sig"))
            if metadata.get("status") != "passed" or metadata.get("source_model_id") != source_id:
                raise ValueError(f"Missing successful export verification: {metadata_path}")
            for tower, path in (("image", image_path), ("text", text_path)):
                record = metadata[tower]
                source_path = Path(source[tower])
                if (source_path.suffix != ".onnx" or record["file"] != path.name
                        or record["sha256"] != row[f"{tower}_sha256"]
                        or record["source_file"] != source_path.name
                        or record["source_sha256"] != digest(source_path)):
                    raise ValueError(f"Export provenance mismatch: {spec['id']} {tower}")
                row[f"source_{tower}_sha256"] = record["source_sha256"]
            row["export_metadata_sha256"] = digest(metadata_path)
            row["export_metadata"] = metadata
        manifest["models"].append(row)
        for path in (image_path, text_path):
            if path.name in uploads and uploads[path.name] != str(path):
                raise ValueError(f"Duplicate destination filename: {path.name}")
            uploads[path.name] = str(path)
    path = args.output / "manifest.json"
    path.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    uploads[path.name] = str(path)
    (args.output / "upload-list.json").write_text(json.dumps([
        {"source": source, "destination": name} for name, source in uploads.items()
    ], indent=2) + "\n", encoding="utf-8")
    print(f"Prepared {len(configs)} model pairs, {len(photos)} images, {len(tokens)} prompts")
    print(f"Upload bytes: {sum(Path(p).stat().st_size for p in uploads.values()):,}")


if __name__ == "__main__":
    main()
