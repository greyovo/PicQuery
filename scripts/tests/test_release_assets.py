"""Host-side release artifact checks; no Android device or signing key required."""

import hashlib
import importlib.util
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch
import warnings
import zipfile


SPEC = importlib.util.spec_from_file_location(
    "release_assets", Path(__file__).resolve().parents[1] / "release_assets.py"
)
release_assets = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(release_assets)

LEGACY_MODELS = ("clip-image-int8.ort", "clip-text-int8.ort")
CURRENT_MODELS = {
    "onnx": ("mobileclip2_s0_image.onnx", "mobileclip2_s0_text_int8.onnx"),
    "tflite": ("image_model.tflite", "text_model_dynamic_wi8.tflite"),
}


class ReleaseAssetsTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.project = self.root / "project"
        self.assets = self.project / "app/src/main/assets"
        self.assets.mkdir(parents=True)
        self.destination = self.root / "release-assets"
        self.profile = {"": LEGACY_MODELS}
        (self.project / "app/build.gradle").write_text("// Legacy release\n")
        (self.assets / "bpe_vocab_gz").write_bytes(b"tracked vocabulary")
        translation = self.assets / "mlkit/model.bin"
        translation.parent.mkdir()
        translation.write_bytes(b"tracked translation")

    def use_current_profile(self):
        (self.project / "app/build.gradle").unlink()
        (self.project / "app/build.gradle.kts").write_text(
            'val imageModel = "mobileclip2_s0_image.onnx"\n'
        )
        self.profile = CURRENT_MODELS

    def model_payloads(self):
        return {
            name: ("model content: " + name).encode()
            for names in self.profile.values()
            for name in names
        }

    def write_archive(self, entries=None):
        archive = self.root / "models.zip"
        entries = self.model_payloads().items() if entries is None else entries
        with warnings.catch_warnings():
            warnings.simplefilter("ignore", UserWarning)
            with zipfile.ZipFile(archive, "w") as bundle:
                for name, data in entries:
                    bundle.writestr(name, data)
        return archive, hashlib.sha256(archive.read_bytes()).hexdigest()

    def stage_local_models(self):
        for name, data in self.model_payloads().items():
            (self.assets / name).write_bytes(data)

    def write_apk(self, flavor="", filename=None, omitted=(), overrides=None):
        self.stage_local_models()
        filename = filename or (f"app-{flavor}-release.apk" if flavor else "app-release.apk")
        directory = self.project / "app/build/outputs/apk" / flavor / "release"
        directory.mkdir(parents=True, exist_ok=True)
        apk = directory / filename
        payloads = {
            f"assets/{name}": (self.assets / name).read_bytes()
            for name in (*self.profile[flavor], "bpe_vocab_gz")
        }
        payloads["assets/mlkit/model.bin"] = b"tracked translation"
        payloads.update(overrides or {})
        with zipfile.ZipFile(apk, "w") as package:
            for name, data in payloads.items():
                if name not in omitted:
                    package.writestr(name, data)
        return apk

    def prepare(self, tag="v1.1.4"):
        release_assets.prepare_apks(self.project, self.destination, tag, "apksigner")

    def assert_no_release_output(self):
        self.assertFalse(self.destination.exists())

    def test_model_profiles_match_historical_and_current_contracts(self):
        self.assertEqual(release_assets.model_sets(self.project), {"": LEGACY_MODELS})
        self.use_current_profile()
        self.assertEqual(release_assets.model_sets(self.project), CURRENT_MODELS)

    def test_unknown_model_profile_is_rejected(self):
        (self.project / "app/build.gradle").unlink()
        (self.project / "app/build.gradle.kts").write_text("// Unknown backend\n")
        with self.assertRaisesRegex(ValueError, "Unsupported model configuration"):
            release_assets.model_sets(self.project)

    def test_restore_legacy_models_preserves_tracked_assets_and_ignores_other_entries(self):
        entries = list(self.model_payloads().items()) + [
            ("../../escaped.txt", b"must not be extracted"),
            ("mlkit/model.bin", b"must not overwrite translation"),
            ("bpe_vocab_gz", b"must not overwrite vocabulary"),
            ("unrelated.onnx", b"unused model"),
        ]
        archive, checksum = self.write_archive(entries)
        release_assets.restore_models(self.project, archive, checksum.upper())
        for name, data in self.model_payloads().items():
            self.assertEqual((self.assets / name).read_bytes(), data)
        self.assertEqual((self.assets / "bpe_vocab_gz").read_bytes(), b"tracked vocabulary")
        self.assertEqual((self.assets / "mlkit/model.bin").read_bytes(), b"tracked translation")
        self.assertFalse((self.assets / "unrelated.onnx").exists())
        self.assertEqual(list(self.root.rglob("escaped.txt")), [])

    def test_restore_current_models_stages_both_backend_pairs(self):
        self.use_current_profile()
        archive, checksum = self.write_archive()
        release_assets.restore_models(self.project, archive, checksum)
        for name, data in self.model_payloads().items():
            self.assertEqual((self.assets / name).read_bytes(), data)

    def test_checksum_mismatch_preserves_existing_models(self):
        original = self.assets / LEGACY_MODELS[0]
        original.write_bytes(b"existing model")
        archive, _ = self.write_archive()
        with self.assertRaisesRegex(ValueError, "checksum mismatch"):
            release_assets.restore_models(self.project, archive, "0" * 64)
        self.assertEqual(original.read_bytes(), b"existing model")
        self.assertFalse((self.assets / LEGACY_MODELS[1]).exists())

    def test_malformed_checksum_is_rejected(self):
        archive, _ = self.write_archive()
        for checksum in ("", "abc", "x" * 64, "0" * 65):
            with self.subTest(checksum=checksum), self.assertRaises(ValueError):
                release_assets.restore_models(self.project, archive, checksum)

    def test_missing_empty_or_duplicate_model_is_rejected_before_staging(self):
        payloads = list(self.model_payloads().items())
        cases = {
            "missing": payloads[:1],
            "empty": [payloads[0], (payloads[1][0], b"")],
            "duplicate": payloads + [payloads[1]],
        }
        for case, entries in cases.items():
            with self.subTest(case=case):
                archive, checksum = self.write_archive(entries)
                with self.assertRaisesRegex(ValueError, "nonempty root entry"):
                    release_assets.restore_models(self.project, archive, checksum)
                self.assertFalse((self.assets / LEGACY_MODELS[0]).exists())

    def test_models_in_nested_archive_directory_are_rejected(self):
        archive, checksum = self.write_archive(
            [("models/" + name, data) for name, data in self.model_payloads().items()]
        )
        with self.assertRaisesRegex(ValueError, "nonempty root entry"):
            release_assets.restore_models(self.project, archive, checksum)

    @patch.object(release_assets.subprocess, "run")
    def test_prepare_legacy_apk_names_checksums_and_signature_verification(self, run):
        apk = self.write_apk()
        self.prepare()
        target = self.destination / "PicQuery-v1.1.4.apk"
        self.assertEqual(target.read_bytes(), apk.read_bytes())
        self.assertEqual(
            (self.destination / "SHA256SUMS").read_text(),
            hashlib.sha256(target.read_bytes()).hexdigest() + "  PicQuery-v1.1.4.apk\n",
        )
        run.assert_called_once_with(["apksigner", "verify", "--verbose", str(apk)], check=True)

    @patch.object(release_assets.subprocess, "run")
    def test_prepare_both_flavors_preserves_distinct_names_and_checksums(self, run):
        self.use_current_profile()
        originals = {flavor: self.write_apk(flavor) for flavor in CURRENT_MODELS}
        self.prepare("v1.2.0")
        expected_lines = []
        for flavor, original in originals.items():
            name = f"PicQuery-v1.2.0-{flavor}.apk"
            target = self.destination / name
            self.assertEqual(target.read_bytes(), original.read_bytes())
            expected_lines.append(hashlib.sha256(target.read_bytes()).hexdigest() + "  " + name)
        self.assertEqual((self.destination / "SHA256SUMS").read_text().splitlines(), expected_lines)
        self.assertEqual(run.call_count, 2)

    @patch.object(release_assets.subprocess, "run")
    def test_missing_flavor_apk_rejects_entire_release(self, run):
        self.use_current_profile()
        self.write_apk("onnx")
        with self.assertRaisesRegex(ValueError, "No release APK"):
            self.prepare()
        self.assert_no_release_output()

    @patch.object(release_assets.subprocess, "run")
    def test_unsigned_and_debug_apks_are_rejected(self, run):
        for filename in ("app-release-unsigned.apk", "app-debug.apk"):
            with self.subTest(filename=filename):
                apk = self.write_apk(filename=filename)
                with self.assertRaisesRegex(ValueError, "non-release or unsigned"):
                    self.prepare()
                self.assert_no_release_output()
                apk.unlink()
        run.assert_not_called()

    @patch.object(release_assets.subprocess, "run")
    def test_debug_directory_does_not_supply_a_release_apk(self, run):
        apk = self.write_apk()
        debug = apk.parent.parent / "debug"
        debug.mkdir()
        apk.rename(debug / "app-debug.apk")
        with self.assertRaisesRegex(ValueError, "No release APK"):
            self.prepare()
        run.assert_not_called()
        self.assert_no_release_output()

    @patch.object(release_assets.subprocess, "run")
    def test_missing_packaged_model_or_vocabulary_rejects_release(self, run):
        for name in (*LEGACY_MODELS, "bpe_vocab_gz"):
            with self.subTest(name=name):
                self.write_apk(omitted=(f"assets/{name}",))
                with self.assertRaises((ValueError, KeyError)):
                    self.prepare()
                self.assert_no_release_output()

    @patch.object(release_assets.subprocess, "run")
    def test_mismatched_packaged_model_or_vocabulary_rejects_release(self, run):
        for name in (*LEGACY_MODELS, "bpe_vocab_gz"):
            with self.subTest(name=name):
                self.write_apk(overrides={f"assets/{name}": b"wrong build input"})
                with self.assertRaisesRegex(ValueError, "does not match source"):
                    self.prepare()
                self.assert_no_release_output()

    @patch.object(release_assets.subprocess, "run")
    def test_invalid_tags_are_rejected_before_signature_verification(self, run):
        self.write_apk()
        for tag in ("", "../v1.1.4", "v1.1.4/extra", "--help", "v1.1.4\n", "v1.1.4;id"):
            with self.subTest(tag=tag), self.assertRaises(ValueError):
                self.prepare(tag)
        run.assert_not_called()
        self.assert_no_release_output()

    def test_valid_version_tags(self):
        for tag in ("v1.1.4", "1.2.0", "v1.2.0-rc.1"):
            with self.subTest(tag=tag):
                release_assets.validate_tag(tag)

    @patch.object(release_assets.subprocess, "run")
    def test_signature_verification_failure_prevents_publication(self, run):
        self.write_apk()
        run.side_effect = subprocess.CalledProcessError(1, ["apksigner", "verify"])
        with self.assertRaises(subprocess.CalledProcessError):
            self.prepare()
        self.assert_no_release_output()

    @patch.object(release_assets.subprocess, "run")
    def test_nonempty_destination_is_preserved(self, run):
        self.write_apk()
        self.destination.mkdir()
        original = self.destination / "PicQuery-v1.1.4.apk"
        original.write_bytes(b"previous release")
        with self.assertRaisesRegex(ValueError, "must be empty"):
            self.prepare()
        self.assertEqual(original.read_bytes(), b"previous release")
        self.assertEqual(list(self.destination.iterdir()), [original])


if __name__ == "__main__":
    unittest.main()
