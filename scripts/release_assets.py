"""Restore pinned model assets and validate APKs before publishing a release."""

import argparse
import hashlib
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import urllib.request
import zipfile


def model_sets(project):
    modern_build = project / "app/build.gradle.kts"
    if modern_build.exists() and "mobileclip2_s0_image.onnx" in modern_build.read_text(encoding="utf-8"):
        return {
            "onnx": ("mobileclip2_s0_image.onnx", "mobileclip2_s0_text_int8.onnx"),
            "tflite": ("image_model.tflite", "text_model_dynamic_wi8.tflite"),
        }
    if (project / "app/build.gradle").is_file():
        # v1.1.3 and v1.1.4 both select modulesCLIP.
        return {"": ("clip-image-int8.ort", "clip-text-int8.ort")}
    raise ValueError("Unsupported model configuration; update the release asset manifest.")


def digest(stream):
    return hashlib.file_digest(stream, "sha256").hexdigest()


def restore_models(project, archive, expected_sha256):
    if not re.fullmatch(r"[0-9a-fA-F]{64}", expected_sha256):
        raise ValueError("MODELS_SHA256 must be the model ZIP's SHA-256 checksum.")
    with archive.open("rb") as stream:
        if digest(stream) != expected_sha256.lower():
            raise ValueError("Model ZIP checksum mismatch.")
    required = [name for names in model_sets(project).values() for name in names]
    with zipfile.ZipFile(archive) as bundle:
        for name in required:
            if bundle.namelist().count(name) != 1 or bundle.getinfo(name).file_size == 0:
                raise ValueError(f"Model ZIP must contain one nonempty root entry: {name}")
        assets = project / "app/src/main/assets"
        assets.mkdir(parents=True, exist_ok=True)
        # Copy only explicit filenames, never extract archive paths or symlinks.
        # Tracked vocabulary/translation assets and unrelated files stay intact.
        for name in required:
            with bundle.open(name) as source, (assets / name).open("wb") as target:
                shutil.copyfileobj(source, target)


def validate_tag(tag):
    if not re.fullmatch(r"v?[0-9][0-9A-Za-z._-]*", tag):
        raise ValueError("Expected a version tag such as v1.1.4 (no paths or options).")


def prepare_apks(project, destination, tag, apksigner):
    validate_tag(tag)
    selected = []
    for flavor, models in model_sets(project).items():
        directory = project / "app/build/outputs/apk" / flavor / "release"
        apks = sorted(directory.glob("*.apk"))
        if not apks:
            raise ValueError(f"No release APK found in {directory}")
        for apk in apks:
            if "unsigned" in apk.stem or "debug" in apk.stem:
                raise ValueError(f"Refusing a non-release or unsigned APK: {apk.name}")
            subprocess.run([apksigner, "verify", "--verbose", str(apk)], check=True)
            with zipfile.ZipFile(apk) as package:
                for name in (*models, "bpe_vocab_gz"):
                    with package.open(f"assets/{name}") as packed:
                        with (project / "app/src/main/assets" / name).open("rb") as source:
                            if digest(packed) != digest(source):
                                raise ValueError(f"Packaged asset does not match source: {name}")
            suffix = apk.stem.removeprefix("app").removesuffix("-release")
            selected.append((apk, f"PicQuery-{tag}{suffix}.apk"))
    names = [name for _, name in selected]
    if len(set(names)) != len(names):
        raise ValueError("Release APK filenames collide.")
    if destination.exists() and any(destination.iterdir()):
        raise ValueError("Release asset directory must be empty.")
    destination.mkdir(parents=True, exist_ok=True)
    checksums = []
    for apk, name in selected:
        target = destination / name
        shutil.copyfile(apk, target)
        with target.open("rb") as stream:
            checksums.append(f"{digest(stream)}  {name}\n")
    (destination / "SHA256SUMS").write_text("".join(checksums), encoding="utf-8", newline="\n")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    models = commands.add_parser("models")
    models.add_argument("project", type=Path)
    models.add_argument("--archive", type=Path, help="Use a local ZIP instead of MODELS_URL")
    apks = commands.add_parser("apks")
    apks.add_argument("project", type=Path)
    apks.add_argument("destination", type=Path)
    apks.add_argument("--tag", required=True)
    apks.add_argument("--apksigner", required=True)
    tag = commands.add_parser("tag")
    tag.add_argument("tag")
    args = parser.parse_args()
    if args.command == "tag":
        validate_tag(args.tag)
    elif args.command == "apks":
        prepare_apks(args.project, args.destination, args.tag, args.apksigner)
    else:
        expected = os.environ.get("MODELS_SHA256", "")
        if args.archive:
            restore_models(args.project, args.archive, expected)
        else:
            url = os.environ.get("MODELS_URL", "")
            if not url.startswith("https://") or not expected:
                parser.error("Set MODELS_URL (HTTPS) and MODELS_SHA256; see docs/releases.md.")
            with tempfile.TemporaryDirectory() as directory:
                archive = Path(directory) / "models.zip"
                with urllib.request.urlopen(url, timeout=120) as source, archive.open("wb") as target:
                    shutil.copyfileobj(source, target)
                restore_models(args.project, archive, expected)


if __name__ == "__main__":
    main()
