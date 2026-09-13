# PicQuery

[English](README.md) | 中文

![PicQuery](assets/cover_cn.jpg)

用中文、英文描述或一张图片搜索本地照片。PicQuery 在设备上建立和搜索照片索引，免费使用，无内购。

本分支通过 **ONNX Runtime** 和 **TFLite / LiteRT** 两个 Android 构建变体接入 Apple **MobileCLIP2-S0 / dfndr2b**。两者可以同时安装，各自保存索引，便于用相同照片和查询比较效果。

[Google Play](https://play.google.com/store/apps/details?id=me.grey.picquery) · [发布版本](https://github.com/greyovo/PicQuery/releases) · [模型指南](script/model-MobileCLIP2/README_zh.md) · [测量报告](script/model-MobileCLIP2/results/README_zh.md)

已发布版本可能使用较早的模型。下述配置需要构建本分支。

APK 位于发布页面的 **Assets** 区域。维护者可参考 [APK 发布与历史版本补传指南](docs/releases.md)，为缺少安装包的版本补充附件。

## 构建与安装

| 环境 | 版本 |
|---|---|
| JDK | 17 |
| Android SDK 平台 | 37（`platforms;android-37.0`） |
| SDK 工具 | 当前 Android Studio 或 command-line tools 22+ |
| Android NDK | 29.0.14206865 |
| CMake | 3.22.1 |
| Android 设备 | Android 10 / API 29 及以上 |

通过 Android Studio 或 `local.properties` 设置 SDK 路径，使用仓库提供的 Gradle wrapper。[版本目录](gradle/libs.versions.toml)固定依赖版本；当前推理运行库为 ONNX Runtime **1.29.0**、LiteRT **1.4.2**。两个变体均需要 NDK/CMake 构建原生 delegate 桥接库。

### 准备模型资产

模型二进制文件由 Git 忽略。请从 [Google Drive](https://drive.google.com/drive/folders/1eWHZ08c7TmVJU9ReQAe7z8o-EE9R297g?usp=sharing) 下载预导出的 App 包，按[SHA-256 校验与安装步骤](script/model-MobileCLIP2/downloads/README_zh.md)将模型放入 `app/src/main/assets/`。也可以自行[导出模型](script/model-MobileCLIP2/README_zh.md#reproduce-the-models)。保留共享的 `bpe_vocab_gz` 和内置 `mlkit/` 资产。

| 变体 | 图像资产 | 文本资产 | 两个模型合计 |
|---|---|---|---:|
| `onnx` | `mobileclip2_s0_image.onnx` | `mobileclip2_s0_text_int8.onnx` | 104.89 MiB |
| `tflite` | `image_model.tflite` | `text_model_dynamic_wi8.tflite` | 105.73 MiB |

两个变体均使用 **FP32 图像模型与动态 INT8 文本权重**，输出归一化 FP32 向量。`text_model.tflite` 是离线 FP32 参照，不打入 APK。每个变体只打包自身模型及共享资产；表中是模型文件体积，不是 APK 大小或运行内存。

```bash
./gradlew :app:assembleOnnxDebug :app:assembleTfliteDebug
adb -s DEVICE_SERIAL install -r app/build/outputs/apk/onnx/debug/app-onnx-debug.apk
adb -s DEVICE_SERIAL install -r app/build/outputs/apk/tflite/debug/app-tflite-debug.apk
```

Windows 下将 `./gradlew` 替换为 `.\gradlew.bat`。通过 `adb devices -l` 查看设备，将 `DEVICE_SERIAL` 替换为目标设备序列号。

| 启动器名称 | 应用 ID |
|---|---|
| PicQuery MC2 ONNX | `me.grey.picquery.mobileclip2.onnx` |
| PicQuery MC2 TFLite | `me.grey.picquery.mobileclip2.tflite` |

默认 APK 包含 ARM64 和 ARMv7 库。x86_64 模拟器构建需追加 `-Pmobileclip2Abis=x86_64`。

## 建立索引与搜索

1. 打开一个变体并授予照片权限。建议先准备小相册，例如 `Pictures/PicQuery-Demo`，再启动 App。
2. 点击 **Index → Add album**，选择目标相册并核对照片数量。点击 **Index**，等待完成后点击 **Finish**。
3. 搜索 `dog`、`astronaut`、其他描述，或使用图片查询。比较后端时，在另一个变体中为相同相册建立索引。
4. 限定已有索引的搜索范围时，打开 **Range**，关闭 **All albums**，选择相册并点击 **Finish**。进程重启后范围会重置。在 **Settings → Album Index Manager** 查看或删除索引。

仅选中的相册会进行编码；启动时读取可访问媒体的元数据，不会自动为所有相册建立索引。新添加的相册若未显示，可重启 App。

ONNX 使用 **图像 4 / 文本 4 个 CPU 线程**；TFLite 原生 XNNPACK 使用 **图像 4 / 文本 2 个线程**。英文查询仍经过翻译；按 CLIP BPE 规范化后相同的候选文本只编码一次。

## 实测结果

独立图像量化实验比较 **FP32 图像 ORT** 和 **混合 INT8 图像 ORT**，两者共用同一个动态 INT8 文本模型。这**不是** ONNX 与 TFLite 变体的对比。以下结果来自 Pixel 8a / Tensor G3 / Android 17，ONNX Runtime 1.29.0、CPU 4 线程：

| 测量项 | FP32 图像 | 混合 INT8 图像 |
|---|---:|---:|
| CIFAR-100 Top1，2,000 张测试图 | 74.60% | 73.90% |
| Imagenette Top1，3,925 张验证图 | 98.04% | 97.96% |
| 图像编码 P50，独立短时 benchmark | 67.58 ms | 53.46 ms |
| 图像 ORT 文件 | 43.54 MiB | 13.52 MiB |
| 图像 + 文本 ORT 文件 | 105.05 MiB | 75.03 MiB |

[完整真机精度与置信区间](script/model-MobileCLIP2/results/image-int8/pixel8a-accuracy/README_zh.md) · [量化、体积与计时协议](script/model-MobileCLIP2/results/image-int8/README_zh.md)

精度使用固定英文类别提示词和手机计算的特征向量。计时使用预加载输入，每塔每轮预热 10 次、记录 100 次，共两轮，不含照片解码、缩放、分词和数据库搜索。它不代表完整搜索延迟或持续索引性能。

混合候选保留敏感卷积为 FP32。**导出 ORT 或量化图像不会改变 App 当前选用的模型**；现有变体仍使用 FP32 图像推理。接入不同图像编码器后，需要重建对应照片索引。

## 检查与限制

```bash
./gradlew :app:testOnnxDebugUnitTest :app:testTfliteDebugUnitTest \
  :app:ktlintCheck :app:lintOnnxDebug :app:lintTfliteDebug
```

使用明确 `adb -s` 目标安装和运行设备测试的步骤见[模型指南](script/model-MobileCLIP2/README_zh.md#run-on-android)。[ktlint 基线](app/config/ktlint/baseline.xml)记录既有问题，不要自动重生成基线掩盖失败。

[精简测量数据](script/model-MobileCLIP2/results/measured-summary.json)保留聚合结果、模型哈希和协议，不包含原始 benchmark/构建日志及逐图输出；按模型指南在 `build/` 下生成新一轮本地结果。

- 已记录的设备验证使用 Pixel 8a、4 KB 内存页和 Debug APK。Release/R8、GPU/NPU、其他手机及整包 16 KB 页兼容性尚未验证；测试设备曾显示兼容提示。
- 类别标签评测和小相册演示不能代表个人相册、自由描述、中文翻译或 OCR 的整体效果。
- 主机和手机使用不同 CPU 内核；历史记录也有不同运行库版本及协议，应在各自范围内比较。[报告索引](script/model-MobileCLIP2/results/README_zh.md)区分了这些实验。

## 历史模型

较早的 CLIP 和 MobileCLIP 模块保留为开发参考。原 App 的 MobileCLIP `vision_model.ort` / `text_model.ort` 文件缺失，因此 v1 S0/S2 评测使用官方 checkpoint 重新导出，不代表复现了无法确认的原始二进制。下载资料及评测范围见[历史模型资源](script/model-MobileCLIP2/README_zh.md#historical-models)。

## 贡献与致谢

欢迎提交 issue 和 pull request。请提供复现步骤、变体、运行库和模型版本及相关检查结果；模型或预处理变更应附数值和检索验证。

PicQuery 基于 OpenAI [CLIP](https://github.com/openai/CLIP) 与 Apple [MobileCLIP](https://github.com/apple-aiml-research/ml-mobileclip)。感谢 [@mazzzystar](https://github.com/mazzzystar) 和 [@Young-Flash](https://github.com/Young-Flash) 在开发中的帮助，相关交流见[原始讨论](https://github.com/mazzzystar/Queryable/issues/12)。

- [mazzzystar/Queryable](https://github.com/mazzzystar/Queryable)：本项目的灵感来源，也提供 [iOS 应用](https://apps.apple.com/us/app/queryable-find-photo-by-text/id1661598353)。
- [IacobIonut01/Gallery](https://github.com/IacobIonut01/Gallery)。

## 许可证

本项目基于 [MIT 协议](LICENSE)开源，保留所有权利。模型资产仍遵循其原始条款，参见 [Apple 模型许可](https://github.com/apple-aiml-research/ml-mobileclip/blob/main/LICENSE_MODELS)。
