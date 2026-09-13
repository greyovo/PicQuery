# MobileCLIP2 model guide

English | [中文](README_zh.md) · [App quick start](../../README.md) · [Report index](results/README.md)

This guide covers the current Android model contract, reproducible ONNX/TFLite/ORT exports, and separate accuracy and timing experiments. Run commands from the repository root. Use a separate output directory for a new experiment; archived reports describe their original artifacts and runtimes.

[Model contract](#input-and-output-contract) · [Source export](#reproduce-the-models) · [ORT export](#ort-export-and-v1-comparison) · [Mixed INT8](#image-int8-quantization) · [Android checks](#run-on-android) · [Historical models](#historical-models)

## Current App configuration

The active `modulesMobileCLIP2` module uses official **MobileCLIP2-S0 / dfndr2b**. The `onnx` and `tflite` flavors have separate application IDs and indexes. Neither ORT conversion nor the image INT8 experiment changes their selected assets.

| Flavor | Image asset | Text asset | Pair size |
|---|---|---|---:|
| `onnx` | `mobileclip2_s0_image.onnx` | `mobileclip2_s0_text_int8.onnx` | 104.89 MiB |
| `tflite` | `image_model.tflite` | `text_model_dynamic_wi8.tflite` | 105.73 MiB |

Both use FP32 image inference, dynamic INT8 text weights and normalized FP32 outputs. These are model-file sizes, not APK sizes or process memory. Full FP32 text exports are offline references; in particular, `text_model.tflite` is excluded from current APKs. Model binaries are Git-ignored; retain the shared `bpe_vocab_gz` and `mlkit/` assets when preparing `app/src/main/assets/`.

## Environment

| Component | Current version |
|---|---|
| JDK | 17 |
| Android SDK / minimum device API | 37 (`platforms;android-37.0`) / 29 |
| NDK / CMake | 29.0.14206865 / 3.22.1 |
| AGP / Gradle wrapper | 9.4.0 / 9.6.0 |
| Kotlin / KSP | 2.4.20 / 2.3.12 |
| Compose BOM | 2026.09.00 |
| ONNX Runtime Android / LiteRT | 1.29.0 / 1.4.2 |

The [version catalog](../../gradle/libs.versions.toml) is authoritative. SDK platform 37.0 needs current Android Studio or command-line tools 22+; older package tools may not read its metadata. Gradle/AGP can install missing packages after SDK licenses are accepted.

LiteRT remains on the compatible 1.x API. Version 2.2 removes Java `Delegate` / `Interpreter.Options.addDelegate` and changes JNI library names, requiring a separate migration. The bridge uses the packaged LiteRT runtime with matching pinned headers; see [native ABI notes](../../app/src/main/cpp/third_party/litert/README.md). Rebuild against the current headers rather than reusing a 1.4.1 probe library. Coil 2.7.0 and Accompanist System UI Controller 0.36.0 remain pinned for existing UI APIs. The root buildscript pins Kotlin Gradle Plugin 2.4.20 to align AGP's built-in Kotlin and kapt with the compiler plugins.

For **checkpoint export and evaluation**, use Python 3.12 with PyTorch, torchvision, OpenCLIP supporting MobileCLIP2, timm, the [official Apple package](https://github.com/apple-aiml-research/ml-mobileclip), NumPy, Pillow and ONNX. The original source export recorded torch 2.11.0+cpu, torchvision 0.26.0+cpu, OpenCLIP 3.3.0, timm 1.0.27.dev0 and ONNX 1.20.1. That original run used Python ORT 1.25.0; the later ORT/image-quantization experiments require **ORT 1.29.0**. Preserve versions in new metadata instead of treating an upgraded rerun as the historical result.

For **ORT conversion only**, the smaller pinned environment below is sufficient; it does not contain the PyTorch/OpenCLIP dependencies needed for calibration or accuracy evaluation.

<a id="input-and-output-contract"></a>

## Input and output contract

| Item | Contract |
|---|---|
| Image input | FP32 `[1,3,256,256]`, RGB NCHW, values in `[0,1]` |
| Resize / crop | Bilinear resize of shortest side to 256, then center crop 256 |
| Text input | CLIP BPE INT32 `[1,77]`, start/end tokens and zero padding |
| Each output | L2-normalized FP32 `[1,512]` |
| ONNX names | `image` or `text` → `embedding` |

Exports use `eval()` and `reparameterize_model`. The ONNX adapter also handles INT64 text inputs when a compared model requires them. Android preprocessing preserves the same resize/crop and channel contract, reuses private scratch/input buffers, and writes channel values in bulk.

The ONNX dynamic text quantizer handles MatMul/Gemm/Gather: linear weights use per-channel INT8 and embeddings use per-tensor quantization. Activations are dynamically quantized for integer multiplication, then embeddings are returned in FP32. Equal weight precision does not imply equal rounding across ONNX, TFLite or CPU architectures.

<a id="reproduce-the-models"></a>

## Reproduce the models

The source exporter resolves official `MobileCLIP2-S0 / dfndr2b` through OpenCLIP. The recorded checkpoint SHA-256 is `ab91a1a0c4330d6b1913e24d5035dfdea15423316aaec649610c6b1c6ddd0e95`. New exports record checkpoint, preprocessing, model hashes and numerical checks in `mobileclip2_onnx_metadata.json`.

Export ONNX in the full environment:


```bash
python script/model-MobileCLIP2/export_mobileclip2_onnx.py
```

This writes the current ONNX pair to `app/src/main/assets/`, and an FP32 text reference to `build/mobileclip2-reference/`. It does not import TFLite conversion packages. Use `--output-dir` and `--reference-dir` to stage an experimental export elsewhere.

TFLite export additionally needs `litert-torch` (or `ai-edge-torch`) and `ai-edge-quantizer`. The second command quantizes text weights; it leaves the FP32 image asset intact:


```bash
python script/model-MobileCLIP2/export_mobileclip2_tflite.py
python script/model-MobileCLIP2/quantize_tflite_dynamic.py
```

The TFLite image-quantization flag is experimental and is not the mixed ONNX/ORT recipe below. Do not substitute its output into the current App without independent validation.

<a id="ort-export-and-v1-comparison"></a>

## Export ORT

Install the converter requirements in an isolated environment:


```bash
python3 -m venv build/mobileclip2-ort-venv
build/mobileclip2-ort-venv/bin/python -m pip install -r script/model-MobileCLIP2/requirements-ort.txt
build/mobileclip2-ort-venv/bin/python script/model-MobileCLIP2/export_mobileclip2_ort.py --target-platform arm
```

On Windows, use the virtual environment's `Scripts/python.exe`. The requirements pin ONNX Runtime 1.29.0, ONNX 1.20.1, NumPy 2.4.4 and FlatBuffers 25.12.19. Conversion reuses the existing FP32 image and dynamic INT8 text ONNX files; it does not requantize them or export FP32 text by default.

The official converter uses **Fixed** optimization and an explicit ARM target. Default outputs in `app/src/main/assets/` are:

- `mobileclip2_s0_image.ort` and `mobileclip2_s0_text_int8.ort`.
- `mobileclip2_required_operators_and_types.config`.
- `mobileclip2_ort_metadata.json`, including source/output hashes, target/runtime, contracts and multi-input verification.

Staged conversion checks the `ORTM` signature, output finiteness/normalization and ONNX–ORT numerical parity before replacing its own output files. Default checks include three synthetic image inputs and three token sequences. The checked-in golden token fixture, `app/src/androidTest/assets/mobileclip2/reference_dog_256.json`, is required; `--reference-fixture` selects an alternative valid fixture. After preparing the five-image/13-prompt manifest below, add `--verification-manifest build/image-int8-baseline-fixtures/manifest.json` for those inputs too. `--image-fixture` accepts an already preprocessed NumPy input.

For an x86 host-specific export, use `--target-platform amd64 --output-dir build/mobileclip2-ort-amd64`; do not deploy that export to ARM. The ARM target excludes x86 NCHWc optimization. Required-operator configs are for custom reduced runtime builds; they do not shrink the current full-runtime APK automatically. See the [official ORT format guide](https://onnxruntime.ai/docs/performance/model-optimizations/ort-format-models.html).

<a id="image-int8-quantization"></a>

## Mixed INT8 image experiment

The [quantization report](results/image-int8/README.md) and [full phone accuracy report](results/image-int8/pixel8a-accuracy/README.md) describe a separate candidate. Both MobileCLIP2 configurations use the same dynamic INT8 text ORT and preprocessing. On Pixel 8a / Tensor G3 / Android 17, ORT 1.29.0, CPU 4 threads:

| Measurement | FP32 image ORT | Mixed INT8 image ORT |
|---|---:|---:|
| CIFAR-100 Top1, 2,000 test images | 74.60% | 73.90% |
| Imagenette Top1, 3,925 validation images | 98.04% | 97.96% |
| Image P50, separate short benchmark | 67.58 ms | 53.46 ms |
| Image file | 43.54 MiB | 13.52 MiB |
| Image + text files | 105.05 MiB | 75.03 MiB |

The recipe uses static S8S8 QDQ, per-channel weights and MinMax calibration. **49 sensitive reparameterized/grouped convolutions remain FP32**; 46 Conv, six MatMul and three Gemm operators are quantized. Input and output tensors remain FP32. The `image_int8` filename does not mean every layer runs in INT8.

Calibration uses 400 training images: two per CIFAR-100 class and 20 per Imagenette class, seed 20260913. Native file and decoded RGB hashes are checked against all 5,925 held-out evaluation images. Layer selection used training-input error, not evaluation labels. The initial full-operator attempt lost most embedding agreement and was retained as a failed experiment; see the [experiment record](results/image-int8/experiment.md).

### Prepare calibration and verification inputs

Use the full evaluation environment with ORT 1.29.0. Prerequisites are the current FP32 image ONNX/ORT and INT8 text ONNX/ORT, plus historical CLIP assets for the auxiliary row in [image_int8_models.json](image_int8_models.json). Obtain historical assets from the resources below. The data cache is `build/mobileclip-accuracy-data/` and must contain CIFAR-100 train/test and Imagenette 320px train/val.

Populate the data and run only existing baselines first; the new candidate need not exist yet:


```bash
python script/model-MobileCLIP2/evaluate_accuracy.py --models script/model-MobileCLIP2/image_int8_models.json --model v2_s0_fp32_image_ort --model legacy_clip_int8 --download --cache build/mobileclip-image-int8-accuracy-cache --output build/mobileclip2-image-int8-results/baseline.json --threads 4
```

<a id="host-comparison"></a>

Place the five public torchvision gallery images in `build/mobileclip2-fixtures/`, retaining these filenames: [dog1.jpg](https://raw.githubusercontent.com/pytorch/vision/main/gallery/assets/dog1.jpg), [dog2.jpg](https://raw.githubusercontent.com/pytorch/vision/main/gallery/assets/dog2.jpg), [astronaut.jpg](https://raw.githubusercontent.com/pytorch/vision/main/gallery/assets/astronaut.jpg), [leaning_tower.jpg](https://raw.githubusercontent.com/pytorch/vision/main/gallery/assets/leaning_tower.jpg), [pottery.jpg](https://raw.githubusercontent.com/pytorch/vision/main/gallery/assets/pottery.jpg). These are numerical/timing fixtures, not a labeled accuracy dataset. Create a source-ONNX-only manifest, then quantize:


```bash
python -c "import json,pathlib; p=pathlib.Path('build/image-int8-baseline-models.json'); p.parent.mkdir(parents=True,exist_ok=True); p.write_text(json.dumps([s for s in json.loads(pathlib.Path('script/model-MobileCLIP2/ort_benchmark_models.json').read_text()) if s['id']=='v2_s0_onnx_int8']),encoding='utf-8')"
python script/model-MobileCLIP2/prepare_device_benchmark.py --models build/image-int8-baseline-models.json --output build/image-int8-baseline-fixtures
python script/model-MobileCLIP2/quantize_mobileclip2_image.py --output-dir build/mobileclip2-image-int8 --evaluation-manifest build/mobileclip2-image-int8-results/baseline-manifest.json --verification-manifest build/image-int8-baseline-fixtures/manifest.json --threads 4 --keep-sensitive-fp32
python script/model-MobileCLIP2/evaluate_accuracy.py --models script/model-MobileCLIP2/image_int8_models.json --cache build/mobileclip-image-int8-accuracy-cache --output build/mobileclip2-image-int8-results/accuracy.json --threads 4
python script/model-MobileCLIP2/prepare_device_benchmark.py --models script/model-MobileCLIP2/image_int8_models.json --output build/mobileclip2-image-int8-device-benchmark
```

**Keep `--keep-sensitive-fp32` in the quantization command.** Omitting it reproduces the unsuccessful full-operator approach. The candidate outputs are `build/mobileclip2-image-int8/mobileclip2_s0_image_int8.onnx` and `.ort`, with `quantization_metadata.json` and `calibration_manifest.json`. Production assets and the text model remain unchanged. Do not replace a candidate during an evaluation/device run.

Metadata `passed` means format, contract and same-target ONNX–ORT parity checks passed; it is not an accuracy acceptance decision. The first mixed-image host/phone vector check failed its strict threshold, despite same-host format parity passing. Complete phone accuracy therefore measures the deployment directly rather than assuming x86 and ARM outputs are equal.

### Short phone benchmark

Install the ONNX Debug App and matching test APK using the Android section below. In PowerShell, use the explicit serial and add `--adb` / `-AdbPath` if ADB is not on PATH:


```powershell
python script/model-MobileCLIP2/upload_device_benchmark.py --serial DEVICE_SERIAL --manifest build/mobileclip2-image-int8-device-benchmark/upload-list.json
New-Item -ItemType Directory -Force build/mobileclip2-image-int8-results/pixel8a | Out-Null
Copy-Item build/mobileclip2-image-int8-device-benchmark/manifest.json build/mobileclip2-image-int8-results/pixel8a/fixture-manifest.json
& script/model-MobileCLIP2/run_device_benchmark.ps1 -DeviceSerial DEVICE_SERIAL -OutputDirectory build/mobileclip2-image-int8-results/pixel8a -Models @('v2_s0_fp32_image_ort','v2_s0_int8_image_ort','legacy_clip_int8') -RoundCount 2 -SampleCount 100 -WarmupCount 10 -SustainSeconds 0 -CooldownSeconds 30
```

This records two rounds, reversing model order in the second round. Each tower has 10 warm-ups and 100 timed calls per round. Timing includes preloaded-input copy, runtime invocation and output copy, excluding photo decoding, resize, tokenization and database search. Loading/first invocation are separate; no sustained thermal test is enabled. These times are not App search or indexing latency.

Validate and render the host accuracy plus phone timing report in the Python evaluation environment:


```bash
python script/model-MobileCLIP2/render_image_int8_report.py --accuracy build/mobileclip2-image-int8-results/accuracy.json --quantization build/mobileclip2-image-int8/quantization_metadata.json --device-dir build/mobileclip2-image-int8-results/pixel8a --output build/mobileclip2-image-int8-results/README.md
```

The renderer writes English `README.md` and Chinese `README_zh.md` from the same verified data. Keep host accuracy labeled separately from phone timing. Model bytes and source/input hashes link the measurements; this does not make different measurement scopes interchangeable.

### Full accuracy on Android

Prepare the exact 5,925 evaluation inputs and 110 class prompts from the completed host manifest. This requires about **4.66 GB** of fixture storage:


```bash
python script/model-MobileCLIP2/prepare_image_int8_device_accuracy.py --accuracy build/mobileclip2-image-int8-results/accuracy.json --output build/mobileclip2-image-int8-device-accuracy
```

With the model files uploaded by the short-benchmark steps and the matching App/test APKs installed, upload the inputs and export features:


```powershell
python script/model-MobileCLIP2/upload_device_benchmark.py --serial DEVICE_SERIAL --directory mobileclip-accuracy --manifest build/mobileclip2-image-int8-device-accuracy/upload-list.json
python script/model-MobileCLIP2/run_image_int8_device_accuracy.py --adb adb --serial DEVICE_SERIAL --fixtures build/mobileclip2-image-int8-device-accuracy --output build/mobileclip2-image-int8-results/pixel8a-accuracy
```

The Android test streams one input at a time with CPU 4 threads, closes the image session before opening text, and writes raw FP32 `[N,512]` features. It does not use personal photos or edit the App's index. The runner retrieves files through binary-safe ADB output and verifies shapes, lengths and SHA-256. Partial or failed exports are not accuracy results.

Compute metrics and both report languages from the complete retrieved features:


```bash
python script/model-MobileCLIP2/evaluate_image_int8_device.py --input build/mobileclip2-image-int8-results/pixel8a-accuracy --host build/mobileclip2-image-int8-results/accuracy.json --evaluation-manifest build/mobileclip2-image-int8-results/accuracy-manifest.json --source-input-dir build/mobileclip2-image-int8-device-accuracy
```

The fixed prompt is `a photo of a {class}`. CIFAR-100 uses 20 test images per class; Imagenette uses its full validation split. Reported Top1/Top5, retrieval metrics, paired confidence intervals and host/phone differences come from this protocol. They do not establish free-form, Chinese, OCR or personal-gallery quality. Full export duration includes I/O and hashing and is not an inference-latency sample.

Large input and feature files are excluded from Git. A fresh checkout can inspect archived JSON, predictions and hashes, but must regenerate/download the feature artifacts to recompute rankings from raw embeddings.

<a id="run-on-android"></a>

## Run on Android

Build and install both flavors, replacing `DEVICE_SERIAL` with the intended device:


```bash
./gradlew :app:assembleOnnxDebug :app:assembleTfliteDebug
adb -s DEVICE_SERIAL install -r app/build/outputs/apk/onnx/debug/app-onnx-debug.apk
adb -s DEVICE_SERIAL install -r app/build/outputs/apk/tflite/debug/app-tflite-debug.apk
```

On Windows, use `.\gradlew.bat`. Default ABIs are ARM64/ARMv7; add `-Pmobileclip2Abis=x86_64` for an x86_64 emulator. ONNX uses 4 image / 4 text threads; native XNNPACK uses 4 image / 2 text threads. The settings row reads the same configuration as the encoders. English still passes through translation; CLIP BPE-normalized duplicate candidates are encoded once.

For a small UI comparison, create `Pictures/PicQuery-Demo`, grant photo access, then use **Index → Add album → Index → Finish**. Search `dog` and `astronaut` in each flavor after indexing the same photos. **Range** restricts already indexed albums; it is separate from selecting which albums to encode. See the [App quick start](../../README.md#index-and-search).

Run local checks:


```bash
./gradlew :app:testOnnxDebugUnitTest :app:testTfliteDebugUnitTest \
  :app:ktlintCheck :app:lintOnnxDebug :app:lintTfliteDebug
```

ktlint 14.2.0 / engine 1.8.0 supports AGP 9. Source checks must include Kotlin files under `src/main/java`, `src/test/java` and `src/androidTest/java`; a `NO-SOURCE` task is not evidence that those files passed. The [baseline](../../app/config/ktlint/baseline.xml) records legacy findings, and `.editorconfig` allows PascalCase `@Composable` names. Do not regenerate the baseline automatically to silence new findings. Read the current task outputs rather than relying on an earlier aggregate check status.

Run offline regression checks in the full Python evaluation environment. These require no existing model files, phone or dataset downloads:

```bash
python -m unittest discover -s script/model-MobileCLIP2/tests -v
```

The submission records [Android build and device validation](results/submission/android-validation.json) and [code/offline regression checks](results/submission/code-validation.json) separately. These checks do not rerun the archived speed measurements.

Build/install the matching test APKs and invoke tests directly on the selected device. This avoids the App uninstall/data-loss behavior observed with an earlier `connected*AndroidTest` workflow; `install -r` retains indexes:


```bash
./gradlew :app:assembleOnnxDebugAndroidTest :app:assembleTfliteDebugAndroidTest
adb -s DEVICE_SERIAL install -r app/build/outputs/apk/androidTest/onnx/debug/app-onnx-debug-androidTest.apk
adb -s DEVICE_SERIAL install -r app/build/outputs/apk/androidTest/tflite/debug/app-tflite-debug-androidTest.apk
adb -s DEVICE_SERIAL shell am instrument -w -r -e class me.grey.picquery.feature.MobileCLIP2InstrumentedTest,me.grey.picquery.feature.MobileCLIP2PreprocessorTest -e sampleCount 50 -e warmupCount 10 me.grey.picquery.mobileclip2.onnx.test/androidx.test.runner.AndroidJUnitRunner
adb -s DEVICE_SERIAL shell am instrument -w -r -e class me.grey.picquery.feature.MobileCLIP2InstrumentedTest,me.grey.picquery.feature.MobileCLIP2PreprocessorTest -e sampleCount 50 -e warmupCount 10 me.grey.picquery.mobileclip2.tflite.test/androidx.test.runner.AndroidJUnitRunner
```

Golden-fixture tests check tokenization, preprocessing, packaged model identity and embeddings. Cross-runtime INT8 tolerances are fixture-specific; they do not replace labeled accuracy evaluation. Debug Pixel 8a tests and demo searches do not validate Release/R8, GPU/NPU, other devices or whole-APK 16 KB compatibility. The tested phone used 4 KB pages and displayed a compatibility notice.

If Windows JDK Unix domain sockets fail with an overly long TEMP path, point the current shell's `TEMP`, `TMP` and `JAVA_TOOL_OPTIONS` `-Djava.io.tmpdir` to an existing short workspace directory such as `build/java-tmp`. This is a local build workaround, not a required repository-wide setting.

<a id="historical-models"></a>
<a id="recognition-accuracy-across-v1-and-v2"></a>

## Historical models and other comparisons

The original App MobileCLIP `vision_model.ort` / `text_model.ort` pair was unavailable. The v1 S0/S2 rows are official checkpoint re-exports, not identified copies of that old App pair. Historical CLIP uses INT8 image/text, stretch-to-224 and its own normalization, so its comparison changes more than the image quantizer or serialization format.

| Reference | Assets | Original resources |
|---|---|---|
| OpenAI CLIP INT8 | `clip-image-int8.ort`, `clip-text-int8.ort` | [Notebook](https://colab.research.google.com/drive/1bW1aMg0er1T4aOcU5pCNYVgmVzBJ4-x4#scrollTo=hPscj2wlZlHb), [download](https://drive.google.com/drive/folders/1VHgEvYyKsiVte8-lywD8qS8SfgcvMc3z?usp=drive_link) |
| Earlier MobileCLIP | `vision_model.ort`, `text_model.ort` | [Original download](https://drive.google.com/drive/folders/1HgGDfsHHIlDK_Fx0Spnujxt51SgguNCq?usp=drive_link) |

Old modules are not the default App configuration. Restoring one requires consistent dependency injection, asset packaging and index namespacing.

To prepare official v1 models and reproduce a new host comparison, use the full Python environment and a separate output:


```bash
python script/model-MobileCLIP2/prepare_accuracy_models.py
python script/model-MobileCLIP2/evaluate_accuracy.py --download --output build/mobileclip-accuracy-rerun/accuracy.json
python script/model-MobileCLIP2/prepare_accuracy_models.py --verify-tokenizers-only
python script/model-MobileCLIP2/benchmark_versions.py --models script/model-MobileCLIP2/ort_benchmark_models.json --output build/mobileclip-ort-comparison/host.json --threads 4 --rounds 3 --warmup 10 --samples 100
```

The ORT matrix requires the v1 exports, current v2 ONNX/ORT pair and historical CLIP assets. The archived [v1/v2 accuracy report](results/识别精度对比.md) used an earlier runtime; the [ORT comparison](results/ort-comparison/README.md) records its own ORT 1.29.0 host/phone protocol. A rerun must report its actual versions and hashes; do not combine historical accuracy with new timing into a single claimed configuration.

For a small ONNX/TFLite conversion comparison on your own images:


```bash
python script/model-MobileCLIP2/benchmark_mobileclip2.py \
  --image /path/to/photo1.jpg --image /path/to/photo2.jpg \
  --text 'a photo of a dog' --text 'an astronaut in a space suit' \
  --output build/mobileclip2-comparison.json
```

This additionally requires `ai-edge-litert`. Omitting images runs synthetic smoke inputs only; omitting text uses 13 prompts. Embedding agreement and small-gallery rankings are conversion diagnostics, not general recognition accuracy.

The [report index](results/README.md) preserves original evidence, including the old LiteRT 1.4.1 Java lazy-delegate threading problem and the later native-thread correction. Earlier App/optimization reports used Android ONNX Runtime 1.23.2 / LiteRT 1.4.1. Their historical timings do not establish the performance of current dependencies. Historical Chinese reports remain in their original language; current result summaries are paired English/Chinese.

## Sources and model terms

- [Apple implementation and inference instructions](https://github.com/apple-aiml-research/ml-mobileclip).
- [Official MobileCLIP2-S0 model](https://huggingface.co/apple/MobileCLIP2-S0).
- [ONNX Runtime quantization](https://onnxruntime.ai/docs/performance/model-optimizations/quantization.html).
- [Apple model terms](https://github.com/apple-aiml-research/ml-mobileclip/blob/main/LICENSE_MODELS); repository code uses the [MIT license](../../LICENSE).
