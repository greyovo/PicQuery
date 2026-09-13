# MobileCLIP2 image INT8 measurements

[简体中文](<README_zh.md>)

The principal comparison changes only the MobileCLIP2 S0 image quantization configuration, sharing the same dynamic INT8 text tower and using ORT files. This is mixed precision: the retained FP32 nodes and actual INT8 coverage are listed below. Legacy CLIP with both towers quantized is a separate reference.

## Host accuracy

These accuracy measurements run on the host CPU with ONNX Runtime 1.29.0 and 4 threads. Full phone accuracy is reported separately; host results are not Android results.
Host: x86_64 CPU / WSL Ubuntu.
CIFAR-100 uses 20 fixed test images per class (2,000 total); Imagenette uses the complete validation split (3,925). All classes use the single English prompt `a photo of a {class}`. Evaluation data is not used to choose prompts or quantization parameters.

| Dataset | Model | Top1 | Top5 | Class-query P@10 | Class-query mAP |
|---|---|---:|---:|---:|---:|
| cifar100 | MobileCLIP2 S0 · FP32 image | 75.30% | 93.90% | 84.70% | 75.73% |
| cifar100 | MobileCLIP2 S0 · mixed INT8 image | 73.60% | 93.20% | 83.70% | 74.18% |
| cifar100 | Legacy CLIP · INT8 image/text | 53.75% | 80.90% | 60.60% | 47.57% |
| imagenette | MobileCLIP2 S0 · FP32 image | 98.11% | 99.90% | 100.00% | 99.30% |
| imagenette | MobileCLIP2 S0 · mixed INT8 image | 98.06% | 99.90% | 100.00% | 99.24% |
| imagenette | Legacy CLIP · INT8 image/text | 83.62% | 99.41% | 81.00% | 74.16% |

P@10 and mAP rank all images within each dataset for each class-text query and average over classes. They are not caption–image Recall@K. CIFAR source images are only 32×32, and Imagenette has 10 classes; neither replaces ImageNet-1k or real photo-library evaluation.

## Image quantization differences

Differences are mixed INT8 − FP32, in percentage points (pp). Paired 95% bootstrap intervals resample the same images 2,000 times with seed 20260913; they describe sampling uncertainty within these datasets only.

| Dataset | Top1 delta pp | Paired 95% CI pp | FP32-only / INT8-only correct | Prediction flips | Both-wrong flips |
|---|---:|---:|---:|---:|---:|
| cifar100 | -1.70 | [-2.70, -0.70] | 71 / 37 | 190 / 2000 (9.50%) | 82 |
| imagenette | -0.05 | [-0.28, +0.20] | 13 / 11 | 30 / 3925 (0.76%) | 6 |

Prediction flips include corrections, correct-to-wrong changes and changes between two wrong classes. Neither flip rate nor vector cosine replaces accuracy. Per-image and per-class details require a local evaluation rerun.

## Calibration and model size

Static quantization uses fixed MinMax calibration: 2 CIFAR-100 training images per class and 20 Imagenette training images per class, 200 from each dataset, 400 total, seed 20260913. Evaluation images are excluded, and parameters were not selected using evaluation results.
The recorded validation checked model/shared-text SHA256, identical preprocessing and calibration/evaluation identities. Sample IDs, native hashes and decoded RGB hashes were checked for overlap. Repeating these checks requires generating the local calibration and evaluation manifests.

| Model | Image MiB | Text MiB | Total MiB |
|---|---:|---:|---:|
| MobileCLIP2 S0 · FP32 image | 43.54 | 61.50 | 105.05 |
| MobileCLIP2 S0 · mixed INT8 image | 13.52 | 61.50 | 75.03 |
| Legacy CLIP · INT8 image/text | 91.44 | 61.56 | 152.99 |

QDQ: activation=QInt8, weight=QInt8, per_channel=True.
The image file is 31.1% of the FP32 size. Operator coverage is summarized below; a local quantization run regenerates detailed node and vector diagnostics.
A metadata status of passed verifies format, tensor contracts or conversion parity, not acceptable recognition quality; use the measured accuracy above to assess quality.

Heavy operators in the quantized ONNX graph:

| Operator | Total | Both inputs quantized to INT8 | Float or partially quantized |
|---|---:|---:|---:|
| Conv | 95 | 46 | 49 |
| MatMul | 6 | 6 | 0 |
| Gemm | 3 | 3 | 0 |

This mixed-precision candidate retains 49 heavy operators in FP32 and 0 with partially quantized inputs.

## Android device latency

Pixel 8a / Tensor G3 / Android 17 / ONNX Runtime 1.29.0 / CPU 4 threads.
Each model has two rounds with 10 warmups and 100 timed samples per tower; first invocation is recorded separately. There are 1,200 timed samples total, with reversed model order in the second round.

| Model | Image P50 / P95 ms | Text P50 / P95 ms | Image P50 by round | Text P50 by round |
|---|---:|---:|---:|---:|
| MobileCLIP2 S0 · FP32 image | 67.58 / 72.04 | 20.88 / 23.40 | 67.29 / 67.92 | 20.77 / 20.95 |
| MobileCLIP2 S0 · mixed INT8 image | 53.46 / 57.06 | 20.66 / 23.36 | 53.28 / 53.60 | 20.68 / 20.65 |
| Legacy CLIP · INT8 image/text | 26.75 / 29.44 | 21.88 / 24.52 | 26.53 / 26.90 | 21.93 / 21.78 |

FP32 / mixed INT8 image P50 ratio: 1.264; above 1 means INT8 is faster. Percentiles use nearest rank after pooling both rounds.

Timing includes copying preloaded inputs, runtime execution and copying outputs. It excludes image decoding/resizing, tokenization and similarity search. Inputs cycle through five public photos and 13 texts; this is separate from full-dataset accuracy evaluation.

| Model | Image load ms by round | Image first call ms | Text load ms | Text first call ms | Thermal status | Observed process PSS MiB |
|---|---:|---:|---:|---:|---|---:|
| MobileCLIP2 S0 · FP32 image | 149.74 / 177.82 | 140.77 / 171.07 | 179.41 / 175.47 | 89.65 / 99.13 | [0] | 227.2 |
| MobileCLIP2 S0 · mixed INT8 image | 131.26 / 139.02 | 107.01 / 114.30 | 182.29 / 193.57 | 98.58 / 64.62 | [0] | 228.7 |
| Legacy CLIP · INT8 image/text | 411.34 / 408.26 | 107.92 / 101.38 | 211.84 / 205.92 | 100.11 / 100.26 | [0] | 259.8 |

Model hashes are verified before loading, so the filesystem cache may be warm: load time is not cold-disk startup. PSS is sampled during single-tower execution, not peak memory or concurrent app tower residency. First outputs must be finite, nonzero and 512-dimensional; legacy CLIP retains raw norms. FP32 and quantized vectors need not be equal.
Extra sustained testing in the first round was set to 0 seconds. Two short rounds do not establish thermal steady state for long library indexing. Frequencies and core affinity were not fixed; temperature, USB charging and scheduling can affect timing.

## Summary and reproduction

[Compact measured data and model hashes](../measured-summary.json) · [Full phone accuracy summary](pixel8a-accuracy/README.md) · [Local reproduction commands](../../README.md#image-int8-quantization)

These are retained historical aggregates, not new measurements. Raw timing arrays, per-image predictions, calibration/input manifests and run logs are not included in Git. The original validation recomputed Top1, paired counts, flips and bootstrap intervals, and checked exported Top5/retrieval aggregates; the compact summary alone cannot repeat those raw-data checks.

Generate a new local run under `build/` to inspect individual predictions, reproduce rankings or audit calibration inputs. Keep `--keep-sensitive-fp32` in the quantization command: the initial full-operator attempt substantially degraded embeddings. The final mixed candidate and current App selection remain separate. Legacy CLIP uses INT8 image/text, stretch224 and its original normalization; its row cannot isolate image-quantization effects.
