# Pixel 8a full-dataset accuracy

[简体中文](<README_zh.md>)

Both image configurations computed all image and class-text embeddings on Pixel 8a (Android API 37, ARM64, ONNX Runtime 1.29.0, CPU 4 threads).
Each model covers 2,000 CIFAR-100 test images and all 3,925 Imagenette validation images, 5,925 images total, with 100 and 10 class texts respectively. The phone embeddings were transferred to the host for L2 normalization, cosine ranking and metrics using the existing protocol. Host embeddings do not substitute for phone outputs.

## Phone accuracy

| Dataset | Image configuration | Top1 | Top5 | Class-query P@10 | Class-query mAP |
|---|---|---:|---:|---:|---:|
| cifar100 | FP32 image + dynamic INT8 text | 74.60% | 93.95% | 85.10% | 75.81% |
| cifar100 | Mixed INT8 image + same dynamic INT8 text | 73.90% | 93.10% | 84.80% | 74.37% |
| imagenette | FP32 image + dynamic INT8 text | 98.04% | 99.87% | 100.00% | 99.30% |
| imagenette | Mixed INT8 image + same dynamic INT8 text | 97.96% | 99.85% | 100.00% | 99.25% |

Both configurations share the same dynamic INT8 text model and preprocessed inputs. Their exported text feature bytes are identical for both datasets. The mixed INT8 image configuration retains sensitive Conv operations in FP32. P@10/mAP query all images with class text and average over classes; they are not free-caption–image Recall@K.

## Phone mixed INT8 versus FP32

Differences are mixed INT8 − FP32, in percentage points (pp). Paired bootstrap resamples the same images 2,000 times (seed 20260913); 95% intervals describe the current dataset distributions only.

| Dataset | Top1 delta pp | 95% CI pp | FP32-only / INT8-only correct | Prediction flips |
|---|---:|---:|---:|---:|
| cifar100 | -0.70 | [-1.80, +0.30] | 65 / 51 | 192 |
| imagenette | -0.08 | [-0.31, +0.15] | 12 / 9 | 26 |

## Same model: host versus phone

Host: Linux-6.6.87.2-microsoft-standard-WSL2-x86_64-with-glibc2.39. Both platforms use ONNX Runtime 1.29.0, 4 threads and the same models/images/class texts. Differences below are phone − host. Per-image flip records contain sample IDs, ground truth and both predictions.

| Dataset | Configuration | Host / phone Top1 | Top1 delta pp | Paired 95% CI pp | Prediction flips / rate |
|---|---|---:|---:|---:|---:|
| cifar100 | FP32 image + dynamic INT8 text | 75.30% / 74.60% | -0.70 | [-1.25, -0.15] | 51 / 2.55% |
| cifar100 | Mixed INT8 image + same dynamic INT8 text | 73.60% / 73.90% | +0.30 | [-0.50, +1.10] | 119 / 5.95% |
| imagenette | FP32 image + dynamic INT8 text | 98.11% / 98.04% | -0.08 | [-0.20, +0.03] | 8 / 0.20% |
| imagenette | Mixed INT8 image + same dynamic INT8 text | 98.06% / 97.96% | -0.10 | [-0.33, +0.13] | 24 / 0.61% |

JSON also includes cross-platform Top5/P@10/mAP deltas and per-image flips. Flips may correct a prediction, make it wrong, or change between wrong classes. Vector cosine cannot replace accuracy measured against ground truth.

The earlier first-image host/phone cosine for this mixed model reached a minimum of 0.98734200, below that check's 0.9999 threshold.
This establishes cross-platform numerical differences, but a first-vector check alone cannot establish recognition failure or identify the responsible kernel. Full phone-dataset accuracy measures the actual effect here.

[Earlier first-input platform parity](<../pixel8a/host-device-parity.json>) uses a small set of benchmark inputs, separate from the full evaluation datasets.

## Validation and scope

SHA256 links model files, export manifests, evaluation samples and host results. Device model/input hashes match the manifest. All eight retrieved feature files are checked for byte length, shape and SHA256; every vector is finite and nonzero. Text input hashes are reproduced from the same class prompts, and shared-text output hashes match byte for byte.
Original host image/token input files were also rehashed sequentially.
The fixed English prompt is `a photo of a {class}`. No evaluation samples were used for calibration, tuning or prompt selection. CIFAR has 32×32 source images and Imagenette is a 10-class subset. Results do not cover personal photo libraries, Chinese queries, OCR or free-caption retrieval.
This is one complete feature export for accuracy evaluation. Export I/O, hashing and total execution duration are not inference-speed measurements. Warmed short-benchmark P50/P95 are reported separately and must not be pooled with export durations.

Raw .f32 feature arrays and large model/input files are generated locally and excluded from Git. Archived JSON retains measurements, per-image predictions, pairing statistics and hashes. A fresh checkout can audit these records; recomputing complete cosine rankings requires regenerating the phone feature files first.

[Full metrics, predictions and validation](<device-accuracy.json>) · [Run record](<run-metadata.json>) · [Phone input manifest](<manifest.json>) · [Evaluation IDs, labels and hashes](<../accuracy-manifest.json>) · [Host accuracy](<../accuracy.json>)
