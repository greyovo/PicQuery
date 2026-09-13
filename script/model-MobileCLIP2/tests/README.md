# Offline report checks

[简体中文](README_zh.md)

Run from the repository root in the existing MobileCLIP evaluation Python environment:

```sh
python -m unittest discover -s script/model-MobileCLIP2/tests -p 'test_*.py' -v
```

No tests invoke model inference, access a device or download data. The report/evaluator regressions use deterministic synthetic manifests, synthetic embeddings and small temporary fake model files. They reject malformed hashes, sample mappings, partial runs, invalid vectors, mismatched runtime/threads, corrupt metrics and bilingual numerical drift. Temporary reports are explicitly synthetic and are removed after each test.

`test_report_archive.py` checks the retained English/Chinese tables against `results/measured-summary.json`, including current host/device accuracy, phone speed, ORT/v1 comparison tables and historical accuracy summaries. The other tests rebuild the report validators and device evaluators from synthetic fixtures, keeping hash, path, schema, statistics and shared-text checks without depending on raw benchmark logs.

Use the dependencies already documented in [the model guide](../README.md). These tests introduce no additional packages and do not require ignored model binaries or multi-gigabyte input tensors. The only committed measurement input they require is the compact `results/measured-summary.json`. Run new experiments under `build/` so local evidence cannot overwrite the published summaries.
