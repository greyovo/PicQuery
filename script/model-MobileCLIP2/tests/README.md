# Offline report checks

[简体中文](README_zh.md)

Run from the repository root in the existing MobileCLIP evaluation Python environment:

```sh
python -m unittest discover -s script/model-MobileCLIP2/tests -p 'test_*.py' -v
```

No tests invoke model inference, access a device or download data. The report/evaluator regressions use archived labels and small temporary fake model files; synthetic embeddings are generated locally. They reject malformed hashes, sample mappings, partial runs, invalid vectors, mismatched runtime/threads, corrupt metrics and bilingual numerical drift. Temporary reports are explicitly synthetic and are removed after each test.

`test_report_archive.py` checks the published English/Chinese tables against archived metrics, recomputes Top1 from predictions and labels, checks paired counts/intervals, recomputes timing percentiles, and verifies the phone report hash chain and identical text-output hashes. It rehashes raw `.f32` arrays and compares the text bytes when those locally generated files exist; otherwise that single test reports an explicit skip. Full ranking recomputation requires regenerating feature arrays and running the evaluator.

Use the dependencies already documented in [the model guide](../README.md). These tests introduce no additional packages and do not require ignored model binaries or multi-gigabyte input tensors. The archived JSON is required. Run new experiments under `build/` so test fixtures cannot overwrite published measurements.
