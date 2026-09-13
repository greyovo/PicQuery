# Measurement archive

[简体中文](README_zh.md) · [Model export and reproduction](../README.md)

These are distinct experiments recorded on 2026-09-13. Keep the runtime, platform, precision and sample scope attached to every number. The tables below are checked against archived JSON; they are not new measurements from the documentation cleanup.

## Current image-quantization experiment

Both configurations use MobileCLIP2 S0 ORT files and the **same dynamic INT8 text tower**. The image candidate is **mixed precision**, with 46 Conv, 6 MatMul and 3 Gemm operators quantized, while 49 sensitive Conv operations retain FP32. It is not an all-INT8 image model. Calibration uses 400 training images, separately from evaluation.

Accuracy covers 2,000 CIFAR-100 test images (20 per class) and all 3,925 Imagenette validation images. A fixed English class prompt is used. Full reports include Top1/Top5, class-query P@10/mAP, per-class results, paired 95% intervals and prediction flips. Class-query retrieval is not free-caption retrieval.

<!-- archive-table: current-accuracy -->
| Platform / runtime | Image configuration | CIFAR-100 Top1 (correct / total) | Imagenette Top1 (correct / total) |
|---|---|---:|---:|
| Host x86 CPU / ORT 1.29.0 | FP32 | 75.30% (1506 / 2000) | 98.11% (3851 / 3925) |
| Host x86 CPU / ORT 1.29.0 | Mixed INT8 | 73.60% (1472 / 2000) | 98.06% (3849 / 3925) |
| Pixel 8a ARM64 CPU / ORT 1.29.0 | FP32 | 74.60% (1492 / 2000) | 98.04% (3848 / 3925) |
| Pixel 8a ARM64 CPU / ORT 1.29.0 | Mixed INT8 | 73.90% (1478 / 2000) | 97.96% (3845 / 3925) |

All sessions use 4 CPU threads. The phone exported every image and class-text embedding on Android API 37; the host then computed normalization, rankings and metrics. Its text outputs are byte-identical between image configurations. The host-only rows are kept separate because the same quantized model can have different numerical behavior on x86 and ARM.

[Host accuracy, quantization coverage and device timing](image-int8/README.md) · [Complete phone accuracy](image-int8/pixel8a-accuracy/README.md) · [Experiment history, including the initial failed candidate (Chinese)](image-int8/experiment.md).

The independent short benchmark uses Pixel 8a / Tensor G3 / Android 17 / ORT 1.29.0 / CPU 4 threads: two reversed-order rounds, 10 warmups and 100 timed calls per tower/round, 1,200 timings across three models. It cycles five photos and 13 texts; full accuracy-export duration is excluded.

<!-- archive-table: current-speed -->
| Model | Image P50 ms | Text P50 ms | Image file MiB |
|---|---:|---:|---:|
| MobileCLIP2 S0 FP32 image + dynamic INT8 text | 67.58 | 20.88 | 43.54 |
| MobileCLIP2 S0 mixed INT8 image + same text | 53.46 | 20.66 | 13.52 |
| Legacy CLIP INT8 image + INT8 text | 26.75 | 21.88 | 91.44 |

Raw timings, P95, first calls, loads and thermal observations remain in the linked report. Short CPU measurements do not establish sustained throughput, peak app memory, GPU/NPU performance or end-to-end photo-library search latency.

## ORT format and v1 comparison

[Bilingual ORT/v1 report](ort-comparison/README.md): the five-model matrix remeasures official v1-S0/v1-S2 reexports, v2-S0 ONNX, v2-S0 ARM ORT and legacy CLIP ORT on the same Pixel 8a with ORT 1.29.0. MobileCLIP rows use FP32 image + dynamic INT8 text; legacy CLIP uses INT8 for both towers. Two rounds provide 2,000 raw timings. ORT reduces session loading in this run, while warmed ONNX/ORT inference is similar.

The accompanying x86 host comparison uses three rounds and 300 calls per tower. Android ARM and separately exported amd64 ORT files are distinct targets. Do not deploy the amd64 file to Android or transfer a host speed ratio to the phone.

**Original app MobileCLIP v1 binaries are unavailable.** The v1 rows use official-checkpoint reexports at deployment precision, not the exact former app binaries. The legacy CLIP row uses the repository's existing ORT files and its original stretch224/normalization path; it is a reference with a different architecture and preprocessing.

## Historical ONNX/TFLite accuracy

[Original report (Chinese)](识别精度对比.md) · [Raw metrics](accuracy-results.json) · [Sample manifest](accuracy-results-manifest.json).

This is a **host CPU** experiment with ONNX Runtime **1.25.0** and Python LiteRT **2.1.4**, 4 threads, the same 2,000/3,925 evaluation samples and fixed English class prompt. The five deployment rows are below; the raw file contains nine rows including separate FP32-text quantization controls. MobileCLIP image towers are FP32 and text weights are dynamic INT8; legacy CLIP has INT8 image/text towers.

<!-- archive-table: historical-accuracy -->
| Model / format | CIFAR-100 Top1 | Imagenette Top1 |
|---|---:|---:|
| MobileCLIP v1-S0 / ONNX | 70.95% | 98.17% |
| MobileCLIP v1-S2 / ONNX | 80.45% | 99.31% |
| MobileCLIP2-S0 / ONNX | 75.05% | 98.04% |
| MobileCLIP2-S0 / TFLite | 77.45% | 99.41% |
| Legacy CLIP / ORT | 53.75% | 83.62% |

These historical host results do not establish phone accuracy and must not be pooled with current ORT 1.29.0 results. Higher accuracy on one small dataset is not evidence of a universal format advantage.

## Other archived evidence

- [Original host speed comparison](speed-comparison.md): source-language report with its own runtime and host configuration.
- [Initial Pixel 8a speed report](pixel8a-speed/真机速度对比.md) and [TFLite thread diagnostics](pixel8a-tflite-diagnostics/诊断报告.md): original Chinese records. The initial LiteRT 1.4.1 lazy XNNPACK delegate did not apply the requested worker count; read the diagnostics and explicit native-delegate controls before treating those numbers as a fair multithreaded comparison.
- [Current Android build/function verification](submission/android-validation.json) and [current script/archive verification](submission/code-validation.json): submission checks, separate from historical accuracy and timing experiments.

## What a fresh checkout can reproduce

JSON metrics, per-image predictions, paired statistics, run receipts, manifests and SHA256 records are archived. Large model binaries, prepared input tensors and raw `.f32` feature arrays are generated locally and excluded from Git. The [offline tests](../tests/README.md) audit archived correctness counts, report values, bilingual tables and synthetic invalid inputs without loading model binaries. Raw-array checks explicitly skip when feature files are absent.

Recomputing full cosine rankings or inference requires regenerating the model/input/feature artifacts using the [documented commands](../README.md). The JSON archive is evidence, not a claim that a clean checkout contains every byte needed for a complete rerun. Historical hashes identify the exact producer used in each experiment; current source hashes may change during maintenance without altering the archived measurements.
