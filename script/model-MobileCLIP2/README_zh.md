# MobileCLIP2 模型指南

[English](README.md) | 中文 · [App 快速开始](../../README_zh.md) · [报告索引](results/README_zh.md)

本指南说明当前 Android 模型契约、ONNX/TFLite/ORT 导出方法，以及独立的精度和计时实验。所有命令从仓库根目录运行。新实验使用独立输出目录；归档报告只描述其记录时的模型与运行库。

[模型契约](#input-and-output-contract) · [源模型导出](#reproduce-the-models) · [ORT 导出](#ort-export-and-v1-comparison) · [混合 INT8](#image-int8-quantization) · [Android 检查](#run-on-android) · [历史模型](#historical-models)

## 当前 App 配置

当前启用的 `modulesMobileCLIP2` 模块使用官方 **MobileCLIP2-S0 / dfndr2b**。`onnx` 与 `tflite` 变体拥有独立应用 ID 和索引。ORT 转换和图像 INT8 实验均不会改变它们选用的资产。

| 变体 | 图像资产 | 文本资产 | 两塔合计 |
|---|---|---|---:|
| `onnx` | `mobileclip2_s0_image.onnx` | `mobileclip2_s0_text_int8.onnx` | 104.89 MiB |
| `tflite` | `image_model.tflite` | `text_model_dynamic_wi8.tflite` | 105.73 MiB |

两者均使用 FP32 图像推理、动态 INT8 文本权重及归一化 FP32 输出。表中是模型文件体积，不是 APK 大小或进程内存。完整 FP32 文本导出仅作离线参照；其中 `text_model.tflite` 不打入当前 APK。模型二进制文件由 Git 忽略；准备 `app/src/main/assets/` 时保留共享的 `bpe_vocab_gz` 和 `mlkit/` 资产。

## 环境

| 组件 | 当前版本 |
|---|---|
| JDK | 17 |
| Android SDK / 最低设备 API | 37（`platforms;android-37.0`）/ 29 |
| NDK / CMake | 29.0.14206865 / 3.22.1 |
| AGP / Gradle wrapper | 9.4.0 / 9.6.0 |
| Kotlin / KSP | 2.4.20 / 2.3.12 |
| Compose BOM | 2026.09.00 |
| ONNX Runtime Android / LiteRT | 1.29.0 / 1.4.2 |

以[版本目录](../../gradle/libs.versions.toml)为准。SDK 平台 37.0 需要当前 Android Studio 或 command-line tools 22+；较旧工具可能无法读取其包元数据。接受 SDK 许可后，Gradle/AGP 可以安装缺少的组件。

LiteRT 保留兼容的 1.x API。2.2 移除了 Java `Delegate` / `Interpreter.Options.addDelegate`，并改变 JNI 库名称，需要独立迁移。桥接库使用 APK 内的 LiteRT 运行库和匹配的固定头文件，详见[原生 ABI 说明](../../app/src/main/cpp/third_party/litert/README_zh.md)。应使用当前头文件重新构建，不要复用 1.4.1 探针库。Coil 2.7.0 和 Accompanist System UI Controller 0.36.0 保持固定，以兼容现有 UI API。根构建脚本固定 Kotlin Gradle Plugin 2.4.20，使 AGP 内置 Kotlin、kapt 与编译器插件版本一致。

**Checkpoint 导出与评估**使用 Python 3.12，需 PyTorch、torchvision、支持 MobileCLIP2 的 OpenCLIP、timm、[Apple 官方包](https://github.com/apple-aiml-research/ml-mobileclip)、NumPy、Pillow 和 ONNX。最初源模型导出记录为 torch 2.11.0+cpu、torchvision 0.26.0+cpu、OpenCLIP 3.3.0、timm 1.0.27.dev0、ONNX 1.20.1。该次 Python ORT 为 1.25.0；后续 ORT 和图像量化实验要求 **ORT 1.29.0**。新实验应记录实际版本，不应将升级后的重跑视为历史结果。

**仅转换 ORT**时，下方固定依赖的轻量环境即可；它不包含校准或精度评估所需的 PyTorch/OpenCLIP 依赖。

<a id="input-and-output-contract"></a>

## 输入输出契约

| 项目 | 契约 |
|---|---|
| 图像输入 | FP32 `[1,3,256,256]`，RGB NCHW，范围 `[0,1]` |
| 缩放 / 裁剪 | 双线性缩放短边至 256，再中心裁剪 256 |
| 文本输入 | CLIP BPE INT32 `[1,77]`，含起止 token 和零填充 |
| 每塔输出 | L2 归一化 FP32 `[1,512]` |
| ONNX 名称 | `image` 或 `text` → `embedding` |

导出前执行 `eval()` 和 `reparameterize_model`。被比较的模型需要 INT64 文本输入时，ONNX 适配器也会处理。Android 预处理保持相同缩放、裁剪和通道契约，复用私有暂存数组与输入缓冲区，批量写入通道值。

ONNX 动态文本量化处理 MatMul/Gemm/Gather：线性层权重采用逐通道 INT8，embedding 权重采用逐张量量化。整数乘法会动态量化激活，最终返回 FP32 向量。相同权重精度不代表 ONNX、TFLite 或不同 CPU 架构具有相同舍入结果。

<a id="reproduce-the-models"></a>

## 导出模型

源导出器通过 OpenCLIP 获取官方 `MobileCLIP2-S0 / dfndr2b`。记录的 checkpoint SHA-256 为 `ab91a1a0c4330d6b1913e24d5035dfdea15423316aaec649610c6b1c6ddd0e95`。新导出会在 `mobileclip2_onnx_metadata.json` 中记录 checkpoint、预处理、模型哈希和数值检查。

在完整环境中导出 ONNX：


```bash
python script/model-MobileCLIP2/export_mobileclip2_onnx.py
```

该命令将当前 ONNX 模型对写入 `app/src/main/assets/`，FP32 文本参照写入 `build/mobileclip2-reference/`，无需导入 TFLite 转换包。实验导出可用 `--output-dir` 和 `--reference-dir` 写到其他目录。

TFLite 导出另需 `litert-torch`（或 `ai-edge-torch`）和 `ai-edge-quantizer`。第二条命令量化文本权重，保留 FP32 图像资产：


```bash
python script/model-MobileCLIP2/export_mobileclip2_tflite.py
python script/model-MobileCLIP2/quantize_tflite_dynamic.py
```

TFLite 图像量化选项仍为实验功能，与下方混合 ONNX/ORT 方案不同。未经独立验证，不要将其输出替换到当前 App。

<a id="ort-export-and-v1-comparison"></a>

## 导出 ORT

在隔离环境中安装转换器依赖：


```bash
python3 -m venv build/mobileclip2-ort-venv
build/mobileclip2-ort-venv/bin/python -m pip install -r script/model-MobileCLIP2/requirements-ort.txt
build/mobileclip2-ort-venv/bin/python script/model-MobileCLIP2/export_mobileclip2_ort.py --target-platform arm
```

Windows 下使用虚拟环境的 `Scripts/python.exe`。依赖固定为 ONNX Runtime 1.29.0、ONNX 1.20.1、NumPy 2.4.4、FlatBuffers 25.12.19。转换复用现有 FP32 图像和动态 INT8 文本 ONNX，不重新量化，默认也不导出 FP32 文本。

官方转换器使用 **Fixed** 优化和明确的 ARM 目标，默认在 `app/src/main/assets/` 生成：

- `mobileclip2_s0_image.ort` 和 `mobileclip2_s0_text_int8.ort`。
- `mobileclip2_required_operators_and_types.config`。
- `mobileclip2_ort_metadata.json`，记录源文件与输出哈希、目标与运行库、契约及多输入验证。

转换先在暂存目录检查 `ORTM` 标记、输出有限性与归一化、ONNX–ORT 数值一致性，通过后才替换自身输出。默认包含三种合成图像输入和三组 token 序列。仓库中的 golden token fixture `app/src/androidTest/assets/mobileclip2/reference_dog_256.json` 是必需输入，可用 `--reference-fixture` 指定其他有效 fixture。准备好下方五图/13 条文本清单后，追加 `--verification-manifest build/image-int8-baseline-fixtures/manifest.json` 可一并验证这些输入。`--image-fixture` 接受已预处理的 NumPy 输入。

x86 主机专用导出使用 `--target-platform amd64 --output-dir build/mobileclip2-ort-amd64`，不要将它部署到 ARM。ARM 目标排除 x86 NCHWc 优化。算子配置用于自定义裁剪运行库，不会自动缩小当前完整运行库 APK。详见[官方 ORT 格式说明](https://onnxruntime.ai/docs/performance/model-optimizations/ort-format-models.html)。

<a id="image-int8-quantization"></a>

## 混合 INT8 图像实验

[量化报告](results/image-int8/README_zh.md)与[完整真机精度报告](results/image-int8/pixel8a-accuracy/README_zh.md)描述独立候选。两种 MobileCLIP2 配置共用相同动态 INT8 文本 ORT 和预处理。以下来自 Pixel 8a / Tensor G3 / Android 17，ORT 1.29.0、CPU 4 线程：

| 测量项 | FP32 图像 ORT | 混合 INT8 图像 ORT |
|---|---:|---:|
| CIFAR-100 Top1，2,000 张测试图 | 74.60% | 73.90% |
| Imagenette Top1，3,925 张验证图 | 98.04% | 97.96% |
| 图像 P50，独立短时 benchmark | 67.58 ms | 53.46 ms |
| 图像文件 | 43.54 MiB | 13.52 MiB |
| 图像 + 文本文件 | 105.05 MiB | 75.03 MiB |

方案使用静态 S8S8 QDQ、逐通道权重和 MinMax 校准。**49 个敏感的重参数化或分组卷积保留 FP32**；量化 46 个 Conv、六个 MatMul 和三个 Gemm。输入输出仍为 FP32。`image_int8` 文件名不表示每层均以 INT8 执行。

校准使用 400 张训练图：CIFAR-100 每类两张、Imagenette 每类 20 张，种子 20260913。原始文件与解码 RGB 哈希均检查与全部 5,925 张保留评测图不重叠。保留层的选择依据训练输入误差，而非评测标签。最初全算子方案丢失了大部分向量一致性，其失败记录予以保留，详见[实验记录](results/image-int8/experiment.md)。

### 准备校准与验证输入

使用 ORT 1.29.0 完整评估环境。前提为当前 FP32 图像 ONNX/ORT 和 INT8 文本 ONNX/ORT，以及 [image_int8_models.json](image_int8_models.json) 辅助比较行所需的历史 CLIP 资产；历史资产资源见下文。数据缓存为 `build/mobileclip-accuracy-data/`，需包含 CIFAR-100 train/test 与 Imagenette 320px train/val。

先准备数据并只运行已有基线，此时新候选可以尚不存在：


```bash
python script/model-MobileCLIP2/evaluate_accuracy.py --models script/model-MobileCLIP2/image_int8_models.json --model v2_s0_fp32_image_ort --model legacy_clip_int8 --download --cache build/mobileclip-image-int8-accuracy-cache --output build/mobileclip2-image-int8-results/baseline.json --threads 4
```

<a id="host-comparison"></a>

将五张公开 torchvision 示例图放入 `build/mobileclip2-fixtures/`，保留文件名：[dog1.jpg](https://raw.githubusercontent.com/pytorch/vision/main/gallery/assets/dog1.jpg)、[dog2.jpg](https://raw.githubusercontent.com/pytorch/vision/main/gallery/assets/dog2.jpg)、[astronaut.jpg](https://raw.githubusercontent.com/pytorch/vision/main/gallery/assets/astronaut.jpg)、[leaning_tower.jpg](https://raw.githubusercontent.com/pytorch/vision/main/gallery/assets/leaning_tower.jpg)、[pottery.jpg](https://raw.githubusercontent.com/pytorch/vision/main/gallery/assets/pottery.jpg)。这些用于数值和计时检查，不是有标签精度数据集。先创建仅含源 ONNX 的清单，再量化：


```bash
python -c "import json,pathlib; p=pathlib.Path('build/image-int8-baseline-models.json'); p.parent.mkdir(parents=True,exist_ok=True); p.write_text(json.dumps([s for s in json.loads(pathlib.Path('script/model-MobileCLIP2/ort_benchmark_models.json').read_text()) if s['id']=='v2_s0_onnx_int8']),encoding='utf-8')"
python script/model-MobileCLIP2/prepare_device_benchmark.py --models build/image-int8-baseline-models.json --output build/image-int8-baseline-fixtures
python script/model-MobileCLIP2/quantize_mobileclip2_image.py --output-dir build/mobileclip2-image-int8 --evaluation-manifest build/mobileclip2-image-int8-results/baseline-manifest.json --verification-manifest build/image-int8-baseline-fixtures/manifest.json --threads 4 --keep-sensitive-fp32
python script/model-MobileCLIP2/evaluate_accuracy.py --models script/model-MobileCLIP2/image_int8_models.json --cache build/mobileclip-image-int8-accuracy-cache --output build/mobileclip2-image-int8-results/accuracy.json --threads 4
python script/model-MobileCLIP2/prepare_device_benchmark.py --models script/model-MobileCLIP2/image_int8_models.json --output build/mobileclip2-image-int8-device-benchmark
```

**量化命令必须保留 `--keep-sensitive-fp32`。** 省略会复现不成功的全算子方案。候选输出为 `build/mobileclip2-image-int8/mobileclip2_s0_image_int8.onnx` 与 `.ort`，同时生成 `quantization_metadata.json` 和 `calibration_manifest.json`。生产资产和文本模型不变。评估或设备运行期间不要替换候选。

Metadata 中 `passed` 表示格式、契约及同目标 ONNX–ORT 一致性检查通过，不代表精度验收。混合图像模型的首次主机/手机向量比较未达到严格阈值，即使同主机格式一致性检查已通过。因此完整真机精度直接评估部署结果，不假定 x86 与 ARM 输出相等。

### 真机短时 benchmark

先按下方 Android 章节安装 ONNX Debug App 和匹配测试 APK。PowerShell 中明确指定设备；ADB 不在 PATH 时补充 `--adb` / `-AdbPath`：


```powershell
python script/model-MobileCLIP2/upload_device_benchmark.py --serial DEVICE_SERIAL --manifest build/mobileclip2-image-int8-device-benchmark/upload-list.json
New-Item -ItemType Directory -Force build/mobileclip2-image-int8-results/pixel8a | Out-Null
Copy-Item build/mobileclip2-image-int8-device-benchmark/manifest.json build/mobileclip2-image-int8-results/pixel8a/fixture-manifest.json
& script/model-MobileCLIP2/run_device_benchmark.ps1 -DeviceSerial DEVICE_SERIAL -OutputDirectory build/mobileclip2-image-int8-results/pixel8a -Models @('v2_s0_fp32_image_ort','v2_s0_int8_image_ort','legacy_clip_int8') -RoundCount 2 -SampleCount 100 -WarmupCount 10 -SustainSeconds 0 -CooldownSeconds 30
```

该命令记录两轮，第二轮反转模型顺序。每塔每轮预热 10 次、计时 100 次。计时包含预加载输入拷贝、运行库执行和输出拷贝，不含照片解码、缩放、分词与数据库搜索。加载和首次执行单列；未启用持续热测试。这些时间不是 App 搜索或索引延迟。

回到 Python 评估环境，验证并生成主机精度与手机计时报告：


```bash
python script/model-MobileCLIP2/render_image_int8_report.py --accuracy build/mobileclip2-image-int8-results/accuracy.json --quantization build/mobileclip2-image-int8/quantization_metadata.json --device-dir build/mobileclip2-image-int8-results/pixel8a --output build/mobileclip2-image-int8-results/README.md
```

生成器从同一份已验证数据输出英文 `README.md` 与中文 `README_zh.md`。主机精度与手机计时分别标注。模型文件及源数据/输入哈希关联这些测量，但不意味着不同测量范围可以混用。

### Android 完整精度评估

根据已完成的主机清单准备完全一致的 5,925 张评测输入和 110 条类别提示词，输入文件约需 **4.66 GB** 存储空间：


```bash
python script/model-MobileCLIP2/prepare_image_int8_device_accuracy.py --accuracy build/mobileclip2-image-int8-results/accuracy.json --output build/mobileclip2-image-int8-device-accuracy
```

完成短时 benchmark 步骤中的模型上传，并安装匹配 App/测试 APK 后，上传输入并导出特征：


```powershell
python script/model-MobileCLIP2/upload_device_benchmark.py --serial DEVICE_SERIAL --directory mobileclip-accuracy --manifest build/mobileclip2-image-int8-device-accuracy/upload-list.json
python script/model-MobileCLIP2/run_image_int8_device_accuracy.py --adb adb --serial DEVICE_SERIAL --fixtures build/mobileclip2-image-int8-device-accuracy --output build/mobileclip2-image-int8-results/pixel8a-accuracy
```

Android 测试以 CPU 4 线程逐张流式推理，图像会话关闭后才打开文本会话，写出原始 FP32 `[N,512]` 特征，不读取个人照片或修改 App 索引。Runner 通过保留二进制字节的 ADB 输出拉取文件，核对形状、长度和 SHA-256。不完整或失败的导出不能算作精度结果。

从完整拉取的特征计算指标并生成双语报告：


```bash
python script/model-MobileCLIP2/evaluate_image_int8_device.py --input build/mobileclip2-image-int8-results/pixel8a-accuracy --host build/mobileclip2-image-int8-results/accuracy.json --evaluation-manifest build/mobileclip2-image-int8-results/accuracy-manifest.json --source-input-dir build/mobileclip2-image-int8-device-accuracy
```

固定提示词为 `a photo of a {class}`。CIFAR-100 每类选 20 张测试图，Imagenette 使用完整验证集。Top1/Top5、检索指标、配对置信区间及主机/手机差异均属于此协议，不能代表自由描述、中文、OCR 或个人相册效果。完整导出耗时包含 I/O 与哈希校验，不能作为推理延迟样本。

大型输入和特征文件不纳入 Git。新克隆可检查归档 JSON、逐图预测与哈希；从原始向量重算排序前，必须重新生成或取得特征文件。

<a id="run-on-android"></a>

## 在 Android 上运行

构建并安装两个变体，将 `DEVICE_SERIAL` 替换为目标设备：


```bash
./gradlew :app:assembleOnnxDebug :app:assembleTfliteDebug
adb -s DEVICE_SERIAL install -r app/build/outputs/apk/onnx/debug/app-onnx-debug.apk
adb -s DEVICE_SERIAL install -r app/build/outputs/apk/tflite/debug/app-tflite-debug.apk
```

Windows 使用 `.\gradlew.bat`。默认 ABI 为 ARM64/ARMv7，x86_64 模拟器追加 `-Pmobileclip2Abis=x86_64`。ONNX 使用图像 4 / 文本 4 线程，原生 XNNPACK 使用图像 4 / 文本 2 线程；设置页读取与编码器相同的配置。英文仍经过翻译，按 CLIP BPE 规范化后重复的候选只编码一次。

小范围 UI 对比可创建 `Pictures/PicQuery-Demo`，授予照片权限，然后依次 **Index → Add album → Index → Finish**。两个变体索引相同照片后分别搜索 `dog` 和 `astronaut`。**Range** 限制已有索引的搜索范围，与选择待编码相册不同。详见 [App 快速开始](../../README_zh.md#建立索引与搜索)。

运行本地检查：


```bash
./gradlew :app:testOnnxDebugUnitTest :app:testTfliteDebugUnitTest \
  :app:ktlintCheck :app:lintOnnxDebug :app:lintTfliteDebug
```

ktlint 14.2.0 / 规则引擎 1.8.0 支持 AGP 9。源码检查必须覆盖 `src/main/java`、`src/test/java` 与 `src/androidTest/java` 中的 Kotlin 文件；`NO-SOURCE` 不能证明这些文件已通过检查。[基线](../../app/config/ktlint/baseline.xml)记录历史问题，`.editorconfig` 允许 `@Composable` 使用 PascalCase。不要自动重生成基线掩盖新增问题；应检查本次任务输出，不依赖较早的聚合检查状态。

在完整 Python 评估环境运行离线回归检查，无需已有模型文件、手机或数据集下载：

```bash
python -m unittest discover -s script/model-MobileCLIP2/tests -v
```

本次提交分别记录 [Android 构建与设备验证](results/submission/android-validation.json)和[代码/离线回归检查](results/submission/code-validation.json)。这些检查不重跑归档速度测量。

构建、安装匹配的测试 APK，直接在目标设备执行测试。此前 `connected*AndroidTest` 流程曾卸载 App 并清除数据；以下 `install -r` 方式保留索引：


```bash
./gradlew :app:assembleOnnxDebugAndroidTest :app:assembleTfliteDebugAndroidTest
adb -s DEVICE_SERIAL install -r app/build/outputs/apk/androidTest/onnx/debug/app-onnx-debug-androidTest.apk
adb -s DEVICE_SERIAL install -r app/build/outputs/apk/androidTest/tflite/debug/app-tflite-debug-androidTest.apk
adb -s DEVICE_SERIAL shell am instrument -w -r -e class me.grey.picquery.feature.MobileCLIP2InstrumentedTest,me.grey.picquery.feature.MobileCLIP2PreprocessorTest -e sampleCount 50 -e warmupCount 10 me.grey.picquery.mobileclip2.onnx.test/androidx.test.runner.AndroidJUnitRunner
adb -s DEVICE_SERIAL shell am instrument -w -r -e class me.grey.picquery.feature.MobileCLIP2InstrumentedTest,me.grey.picquery.feature.MobileCLIP2PreprocessorTest -e sampleCount 50 -e warmupCount 10 me.grey.picquery.mobileclip2.tflite.test/androidx.test.runner.AndroidJUnitRunner
```

Golden fixture 测试检查分词、预处理、打包模型身份和向量。跨运行库 INT8 容差针对特定输入，不能替代有标签精度评估。Pixel 8a Debug 测试和演示搜索不验证 Release/R8、GPU/NPU、其他设备或整包 16 KB 兼容性；测试手机使用 4 KB 页且出现过兼容提示。

Windows 下若过长 TEMP 路径导致 JDK Unix domain socket 失败，可将当前终端的 `TEMP`、`TMP` 以及 `JAVA_TOOL_OPTIONS` 中的 `-Djava.io.tmpdir` 指向已存在的短工作区目录，例如 `build/java-tmp`。这是本机构建排错方法，不是全仓库必需设置。

<a id="historical-models"></a>
<a id="recognition-accuracy-across-v1-and-v2"></a>

## 历史模型与其他比较

原 App MobileCLIP `vision_model.ort` / `text_model.ort` 文件缺失。v1 S0/S2 比较行是官方 checkpoint 重新导出，不是已确认来源的原 App 模型对。历史 CLIP 使用图像/文本 INT8、拉伸至 224 和自身归一化，因此其比较同时改变了图像量化器或序列化格式以外的因素。

| 参考 | 资产 | 原始资源 |
|---|---|---|
| OpenAI CLIP INT8 | `clip-image-int8.ort`、`clip-text-int8.ort` | [Notebook](https://colab.research.google.com/drive/1bW1aMg0er1T4aOcU5pCNYVgmVzBJ4-x4#scrollTo=hPscj2wlZlHb)、[下载](https://drive.google.com/drive/folders/1VHgEvYyKsiVte8-lywD8qS8SfgcvMc3z?usp=drive_link) |
| 较早的 MobileCLIP | `vision_model.ort`、`text_model.ort` | [原始下载](https://drive.google.com/drive/folders/1HgGDfsHHIlDK_Fx0Spnujxt51SgguNCq?usp=drive_link) |

旧模块不是当前 App 默认配置。恢复时需要统一调整依赖注入、资产打包和索引命名空间。

在完整 Python 环境准备官方 v1 模型，使用独立输出重跑主机比较：


```bash
python script/model-MobileCLIP2/prepare_accuracy_models.py
python script/model-MobileCLIP2/evaluate_accuracy.py --download --output build/mobileclip-accuracy-rerun/accuracy.json
python script/model-MobileCLIP2/prepare_accuracy_models.py --verify-tokenizers-only
python script/model-MobileCLIP2/benchmark_versions.py --models script/model-MobileCLIP2/ort_benchmark_models.json --output build/mobileclip-ort-comparison/host.json --threads 4 --rounds 3 --warmup 10 --samples 100
```

ORT 比较矩阵需要 v1 导出、当前 v2 ONNX/ORT 模型对和历史 CLIP 资产。归档的 [v1/v2 精度报告](results/识别精度对比.md)使用较早运行库；[ORT 对比](results/ort-comparison/README_zh.md)记录自身 ORT 1.29.0 主机/手机协议。重跑必须记录实际版本和哈希，不要把历史精度与新计时合成同一配置的结论。

用自己的少量图片比较 ONNX/TFLite 转换结果：


```bash
python script/model-MobileCLIP2/benchmark_mobileclip2.py \
  --image /path/to/photo1.jpg --image /path/to/photo2.jpg \
  --text 'a photo of a dog' --text 'an astronaut in a space suit' \
  --output build/mobileclip2-comparison.json
```

该比较另需 `ai-edge-litert`。省略图像参数时只运行合成冒烟输入；省略文本参数时使用 13 条提示词。向量一致性与小图集排序属于转换诊断，不是通用识别精度。

[报告索引](results/README_zh.md)保留原始证据，包括旧 LiteRT 1.4.1 Java lazy delegate 的线程问题与后续原生线程修正。较早 App/优化报告使用 Android ONNX Runtime 1.23.2 / LiteRT 1.4.1，其历史计时不能代表当前依赖的性能。历史中文报告保留原文，当前结果摘要提供中英双语。

## 来源与模型条款

- [Apple 实现与推理说明](https://github.com/apple-aiml-research/ml-mobileclip)。
- [官方 MobileCLIP2-S0 模型](https://huggingface.co/apple/MobileCLIP2-S0)。
- [ONNX Runtime 量化说明](https://onnxruntime.ai/docs/performance/model-optimizations/quantization.html)。
- [Apple 模型条款](https://github.com/apple-aiml-research/ml-mobileclip/blob/main/LICENSE_MODELS)；仓库代码使用 [MIT 协议](../../LICENSE)。
