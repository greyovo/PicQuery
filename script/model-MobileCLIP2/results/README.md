# Measurement summaries

[简体中文](README_zh.md) · [Model export and reproduction](../README.md)

These are distinct experiments recorded on 2026-09-13. Keep the runtime, platform, precision and sample scope attached to every number. The retained aggregate values, model hashes and protocols are in [measured-summary.json](measured-summary.json). This cleanup does not introduce new measurements.

## Current image-quantization experiment

Both configurations use MobileCLIP2 S0 ORT files and the **same dynamic INT8 text tower**. The image candidate is **mixed precision**, with 46 Conv, 6 MatMul and 3 Gemm operators quantized, while 49 sensitive Conv operations retain FP32. It is not an all-INT8 image model. Calibration uses 400 training images, separately from evaluation.

Accuracy covers 2,000 CIFAR-100 test images (20 per class) and all 3,925 Imagenette validation images. A fixed English class prompt is used. The summaries include Top1/Top5, class-query P@10/mAP, paired 95% intervals and aggregate prediction flips. Class-query retrieval is not free-caption retrieval.

<!-- archive-table: current-accuracy -->
| Platform / runtime | Image configuration | CIFAR-100 Top1 (correct / total) | Imagenette Top1 (correct / total) |
|---|---|---:|---:|
| Host x86 CPU / ORT 1.29.0 | FP32 | 75.30% (1506 / 2000) | 98.11% (3851 / 3925) |
| Host x86 CPU / ORT 1.29.0 | Mixed INT8 | 73.60% (1472 / 2000) | 98.06% (3849 / 3925) |
| Pixel 8a ARM64 CPU / ORT 1.29.0 | FP32 | 74.60% (1492 / 2000) | 98.04% (3848 / 3925) |
| Pixel 8a ARM64 CPU / ORT 1.29.0 | Mixed INT8 | 73.90% (1478 / 2000) | 97.96% (3845 / 3925) |

All sessions use 4 CPU threads. The phone exported every image and class-text embedding on Android API 37; the host then computed normalization, rankings and metrics. Its text outputs are byte-identical between image configurations. The host-only rows are kept separate because the same quantized model can have different numerical behavior on x86 and ARM.

[Host accuracy, quantization coverage and device timing](image-int8/README.md) · [Complete phone accuracy](image-int8/pixel8a-accuracy/README.md).

The independent short benchmark uses Pixel 8a / Tensor G3 / Android 17 / ORT 1.29.0 / CPU 4 threads: two reversed-order rounds, 10 warmups and 100 timed calls per tower/round, 1,200 timings across three models. It cycles five photos and 13 texts; full accuracy-export duration is excluded.

<!-- archive-table: current-speed -->
| Model | Image P50 ms | Text P50 ms | Image file MiB |
|---|---:|---:|---:|
| MobileCLIP2 S0 FP32 image + dynamic INT8 text | 67.58 | 20.88 | 43.54 |
| MobileCLIP2 S0 mixed INT8 image + same text | 53.46 | 20.66 | 13.52 |
| Legacy CLIP INT8 image + INT8 text | 26.75 | 21.88 | 91.44 |

The linked summary retains P95, first-call/load aggregates and thermal observations; individual timing samples are not included. Short CPU measurements do not establish sustained throughput, peak app memory, GPU/NPU performance or end-to-end photo-library search latency.

## ORT format and v1 comparison

[Bilingual ORT/v1 report](ort-comparison/README.md): the five-model matrix remeasures official v1-S0/v1-S2 reexports, v2-S0 ONNX, v2-S0 ARM ORT and legacy CLIP ORT on the same Pixel 8a with ORT 1.29.0. MobileCLIP rows use FP32 image + dynamic INT8 text; legacy CLIP uses INT8 for both towers. Two rounds recorded 2,000 timed calls. ORT reduces session loading in this run, while warmed ONNX/ORT inference is similar.

The accompanying x86 host comparison uses three rounds and 300 calls per tower. Android ARM and separately exported amd64 ORT files are distinct targets. Do not deploy the amd64 file to Android or transfer a host speed ratio to the phone.

**Original app MobileCLIP v1 binaries are unavailable.** The v1 rows use official-checkpoint reexports at deployment precision, not the exact former app binaries. The legacy CLIP row uses the repository's existing ORT files and its original stretch224/normalization path; it is a reference with a different architecture and preprocessing.

## Historical ONNX/TFLite accuracy

The five deployment configurations below are retained in the [compact measured summary](measured-summary.json).

This is a **host CPU** experiment with ONNX Runtime **1.25.0** and Python LiteRT **2.1.4**, 4 threads, the same 2,000/3,925 evaluation samples and fixed English class prompt. The historical experiment also included separate FP32-text quantization controls; the table retains the five deployment configurations. MobileCLIP image towers are FP32 and text weights are dynamic INT8; legacy CLIP has INT8 image/text towers.

<!-- archive-table: historical-accuracy -->
| Model / format | CIFAR-100 Top1 | Imagenette Top1 |
|---|---:|---:|
| MobileCLIP v1-S0 / ONNX | 70.95% | 98.17% |
| MobileCLIP v1-S2 / ONNX | 80.45% | 99.31% |
| MobileCLIP2-S0 / ONNX | 75.05% | 98.04% |
| MobileCLIP2-S0 / TFLite | 77.45% | 99.41% |
| Legacy CLIP / ORT | 53.75% | 83.62% |

These historical host results do not establish phone accuracy and must not be pooled with current ORT 1.29.0 results. Higher accuracy on one small dataset is not evidence of a universal format advantage.

## Threading context

The initial LiteRT 1.4.1 Java lazy XNNPACK delegate did not apply the requested worker count. Those early observations do not establish a fair comparison with correctly configured multithreaded inference. Current App configuration and checks are described in the [model guide](../README.md#run-on-android).

## Reproduction

Git retains these bilingual aggregate summaries and [measured-summary.json](measured-summary.json). Raw benchmark/build logs, screenshots, per-image predictions, input manifests and feature arrays are not included. Model download checksums remain separate from measurement data.

The [offline tests](../tests/README.md) check retained summary consistency and synthetic error cases; they do not reconstruct historical inference from missing raw data. Follow the [reproduction commands](../README.md) to generate a new local run under ignored `build/` directories. Recomputing rankings, per-image changes or confidence intervals requires the corresponding regenerated inputs and outputs. Keep each rerun's actual runtime, model hashes and protocol attached to its results.
