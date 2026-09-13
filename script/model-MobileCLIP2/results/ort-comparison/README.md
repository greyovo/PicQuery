# MobileCLIP2 ORT export and v1 comparison

[简体中文](README_zh.md) · [All measurement series](../README.md)

Measured on 2026-09-13 with ONNX Runtime **1.29.0** in Python and Android. MobileCLIP models use FP32 image towers and dynamic INT8 text towers. The legacy app CLIP reference uses INT8 for both towers.

The Android ARM ORT exports match their source ONNX outputs in export checks. On Pixel 8a, their main observed benefit is shorter session loading; warmed inference is close to ONNX.

## Device: previous models remeasured with the same runtime

Pixel 8a / Tensor G3 / Android 17 / ARM64 / 4 KB pages, Debug APK, CPU intra-op=4, inter-op=1, batch=1. Each tower has 10 warmups and 100 timed samples per round, two rounds, reversed model order in the second round. Models have a 30-second cooldown; no additional sustained-load test was run. Nearest-rank percentiles pool both rounds, covering 2,000 raw timings across all models and towers.

<!-- archive-table: device-speed -->
| Model | Image P50 / P95 ms | Text P50 / P95 ms | Both towers MiB |
|---|---:|---:|---:|
| MobileCLIP v1-S0 ONNX | 71.26 / 78.22 | 15.94 / 17.98 | 96.39 |
| MobileCLIP v1-S2 ONNX | 210.91 / 266.13 | 27.81 / 36.51 | 197.77 |
| MobileCLIP2-S0 ONNX | 69.69 / 76.44 | 21.54 / 24.05 | 104.89 |
| MobileCLIP2-S0 ORT (ARM) | 70.00 / 76.96 | 21.77 / 24.78 | 105.05 |
| Legacy app CLIP ORT | 28.19 / 31.20 | 24.13 / 26.66 | 152.99 |

Load times below are medians across two rounds. Model SHA verification precedes loading, so filesystem caches may be warm. Image loading includes initial ORT environment setup; these values are neither cold app startup nor directly comparable with text loading.

<!-- archive-table: device-loading -->
| MobileCLIP2 format | Image session load ms | Text session load ms | Image first call ms | Text first call ms |
|---|---:|---:|---:|---:|
| ONNX | 216.03 | 298.15 | 171.82 | 106.50 |
| ORT / ARM | 157.39 | 181.60 | 123.84 | 107.18 |

ORT image/text session loading is approximately **27% / 39%** shorter in this run. Small inference differences do not establish a stable speedup. The v1-S0 text tower is smaller than v2-S0; cross-generation speed also depends on architecture, weight size and the legacy CLIP's 224-pixel preprocessing.

Recorded battery temperature spans 33.1–35.5 °C, thermal status is [0], and USB charging was left unchanged. Battery temperature is not SoC junction temperature, and a fixed cooldown does not guarantee equal thermal conditions. v1-S2 text P50 is 21.62 / 35.12 ms across rounds: this variation limits conclusions from the pooled value. PSS is sampled for a process running one tower, not peak memory with both app towers resident.

The retained aggregates and protocol are recorded in [measured-summary.json](../measured-summary.json); individual timings and device logs are not included.

## Host and export target

i9-13900K / WSL Ubuntu / CPU 4 threads, three rounds and 300 timed invocations per tower.

<!-- archive-table: host-speed -->
| Comparison | ONNX image / text P50 ms | ORT image / text P50 ms |
|---|---:|---:|
| Same ARM artifacts used on the phone | 12.38 / 6.87 | 18.34 / 6.67 |
| Separately exported amd64 artifacts | 12.26 / 6.91 | 12.09 / 6.88 |

The ARM export intentionally disables x86 NCHWc. Its fixed optimized ORT graph cannot reselect this optimization through the default ONNX loading path on x86. Exporting for amd64 brings image latency close to ONNX, supporting target-specific optimization as an explanation for the host difference. The amd64 artifacts are host controls and must not be deployed to Android ARM.

Both host comparisons are retained in the [compact measured summary](../measured-summary.json). Speed ratios do not transfer across devices.

## Models and correctness

| Android file | Size MiB | SHA256 |
|---|---:|---|
| `mobileclip2_s0_image.ort` | 43.54 | `c3050f9819b074cb05a2362393ba6c5dfc30f305ebfcd3e64508bc40710a1055` |
| `mobileclip2_s0_text_int8.ort` | 61.50 | `aa3ef129f2fb48e7af48bb2bcfc7928b4eac18d23cf9eaf31dcae92f618a783b` |

The files are generated under `app/src/main/assets/` and excluded from Git. Their combined size is slightly larger than the source ONNX files. Format conversion does not quantize them again or automatically shrink the runtime.

Both ARM and amd64 exports check 8 image inputs (5 photos plus black/white/random inputs) and 16 texts (13 queries plus golden/empty/full-context cases). Maximum absolute error against source ONNX with the same target and runtime is **0**. Shapes, finite values, L2 normalization and ORTM signatures pass; the ARM artifacts contain no NCHWc.

The phone also compared the first image and text for ONNX/ORT in each round. Other inputs were timed and checked for finite 512-dimensional outputs; these checks are not labeled accuracy. The [historical accuracy summary](../README.md#historical-onnxtflite-accuracy) keeps its original runtime and dataset scope. Accuracy was not rerun in this format-comparison experiment.

v1-S0/S2 are deployment-precision reexports of official checkpoints. The original app's MobileCLIP v1 binaries are unavailable, so these rows do not reproduce those exact binaries. The legacy app CLIP ORT reference uses the files already present in the repository.

## Reproduction

See the [model guide](../../README.md) for Python environments, exports and host/device commands. This experiment did not change the App's production model selection.

Git retains [measured-summary.json](../measured-summary.json) and these bilingual aggregate tables. Export/debug logs, per-input errors, raw timing arrays, environment dumps and script-validation receipts are not included. Model binaries and generated inputs/features must be prepared locally. Run the documented commands with outputs under ignored `build/` directories to obtain new raw data and validate the artifacts on your runtime and device.

GPU/NPU, Release/R8, other devices, sustained heating and end-to-end library-search latency were not tested in this experiment.
