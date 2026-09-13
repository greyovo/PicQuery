"""Offline regression checks for historical diagnostic provenance."""
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest


SCRIPTS = Path(__file__).resolve().parents[1]
ARCHIVE = SCRIPTS / "results/pixel8a-tflite-diagnostics"


class DiagnosticProvenanceTest(unittest.TestCase):
    def render(self, previous):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            inputs = root / "diagnostics"
            inputs.mkdir()
            original = json.loads((ARCHIVE / "summary.json").read_text(encoding="utf-8-sig"))
            for case in original["cases"]:
                shutil.copyfile(ARCHIVE / f"{case}.json", inputs / f"{case}.json")
            if previous is not None:
                (inputs / "summary.json").write_text(json.dumps(previous), encoding="utf-8")
            # Model current build products with different bytes from the experiment.
            # A renderer must never attribute these bytes to historical measurements.
            for name in original["artifact_sha256"]:
                artifact = root / name
                artifact.parent.mkdir(parents=True, exist_ok=True)
                artifact.write_bytes(b"a later build, not the measured artifact")
            manifest = root / "build/mobileclip-device-benchmark/manifest.json"
            manifest.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(SCRIPTS / "results/pixel8a-speed/fixture-manifest.json", manifest)
            before = {p.name: hashlib.sha256(p.read_bytes()).hexdigest()
                      for p in inputs.glob("*.json") if p.name != "summary.json"}
            result = subprocess.run(
                [sys.executable, str(SCRIPTS / "summarize_tflite_diagnostics.py"), "--input", str(inputs)],
                cwd=root, capture_output=True, text=True, encoding="utf-8",
            )
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
            self.assertEqual(before, {p.name: hashlib.sha256(p.read_bytes()).hexdigest()
                                      for p in inputs.glob("*.json") if p.name != "summary.json"})
            return json.loads((inputs / "summary.json").read_text(encoding="utf-8")), original

    def test_preserves_recorded_hashes_and_measured_statistics(self):
        original = json.loads((ARCHIVE / "summary.json").read_text(encoding="utf-8-sig"))
        actual, _ = self.render(original)
        self.assertEqual(actual["cases"], original["cases"])
        self.assertEqual(actual["artifact_sha256"], original["artifact_sha256"])

    def test_does_not_invent_provenance_for_missing_receipt(self):
        actual, original = self.render(None)
        self.assertEqual(actual["cases"], original["cases"])
        self.assertEqual(actual["artifact_sha256"], {})

    def test_does_not_reuse_hashes_from_different_measurements(self):
        previous = json.loads((ARCHIVE / "summary.json").read_text(encoding="utf-8-sig"))
        previous["cases"] = {}
        actual, _ = self.render(previous)
        self.assertEqual(actual["artifact_sha256"], {})


if __name__ == "__main__":
    unittest.main()
