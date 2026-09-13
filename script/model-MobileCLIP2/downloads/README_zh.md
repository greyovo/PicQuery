# 模型下载包

[English](README.md) | 中文 · [模型指南](../README_zh.md)

下载目录：[Google Drive — PicQuery 模型下载包](https://drive.google.com/drive/folders/1eWHZ08c7TmVJU9ReQAe7z8o-EE9R297g?usp=sharing)。

**已于 2026 年 9 月 13 日上传。** 以下四个包共用此下载目录。校验值对应本地已验证的文件，使用前请核验下载 ZIP 的 SHA-256。也可以按[源码导出步骤](../README_zh.md#reproduce-the-models)自行生成。

## 选择下载包

| ZIP | ZIP 大小 | 内容 | 用途 |
|---|---:|---|---|
| `picquery-mobileclip2-app-20260913.zip` | 188.19 MiB | 当前 ONNX/TFLite 的四个模型资产 | 构建两个 Android 变体 |
| `picquery-mobileclip2-ort-benchmark-20260913.zip` | 115.07 MiB | FP32 图像 ORT、动态 INT8 文本 ORT、混合 INT8 图像 ONNX 和 ORT | 独立的 v2 格式/量化实验 |
| `picquery-mobileclip-v1-benchmark-20260913.zip` | 258.02 MiB | v1 S0/S2 的 FP32 图像与动态 INT8 文本 ONNX 模型对 | 复现 v1 对比输入 |
| `picquery-legacy-clip-20260913.zip` | 100.70 MiB | 历史 CLIP INT8 图像/文本 ORT 模型对 | 旧 CLIP 对比及匹配的历史发布版本 |

v1 文件是官方 checkpoint 重新导出的产物，不是缺失的原 App MobileCLIP ORT 模型对。混合 INT8 图像候选保留敏感算子为 FP32，不会自动替换 App 的 FP32 图像模型。下载包不包含完整 FP32 文本参照、中间/失败量化候选、数据集和生成的特征向量。

每个 ZIP 的根目录包含模型文件、中英 README 和包内 `SHA256SUMS`。本目录的 [SHA256SUMS](SHA256SUMS) 校验 **ZIP 文件**；[manifest.json](manifest.json)记录各模型的字节数、SHA-256、精度及还原位置。ZIP 压缩改变文件体积，不改变模型字节。

## 校验并安装 App 模型

构建当前 App 只需 App 包，其余三个包用于对比。打开 [Google Drive 文件夹](https://drive.google.com/drive/folders/1eWHZ08c7TmVJU9ReQAe7z8o-EE9R297g?usp=sharing)，将原始 `picquery-mobileclip2-app-20260913.zip` 下载至 `build/model-downloads/`（目录不存在时先创建）。应单独下载该文件；Drive 的文件夹下载功能重新生成的 ZIP 会有不同的校验值。以下命令从仓库根目录执行。

1. 使用仓库内的 ZIP 校验清单核验压缩包。PowerShell：

   ```powershell
   $archiveName = 'picquery-mobileclip2-app-20260913.zip'
   $archivePath = Join-Path 'build/model-downloads' $archiveName
   $checksumLines = @(Get-Content 'script/model-MobileCLIP2/downloads/SHA256SUMS' | Where-Object { $_.EndsWith("  $archiveName") })
   if ($checksumLines.Count -ne 1) { throw 'Expected one pinned ZIP checksum' }
   $expected = ($checksumLines[0] -split '  ', 2)[0]
   if ((Get-FileHash -LiteralPath $archivePath -Algorithm SHA256).Hash -ne $expected) { throw 'ZIP SHA-256 mismatch' }
   ```

   Linux 下在 `build/model-downloads/` 目录执行 `sha256sum --ignore-missing -c ../../script/model-MobileCLIP2/downloads/SHA256SUMS`。至少 App ZIP 必须显示 `OK`。

2. 解压到暂存目录，不要覆盖仓库根目录：

   ```powershell
   Expand-Archive -LiteralPath $archivePath -DestinationPath 'build/model-downloads/extracted-app'
   ```

   重复操作时使用新的暂存目录。Linux 下对应命令为 `unzip build/model-downloads/picquery-mobileclip2-app-20260913.zip -d build/model-downloads/extracted-app`。

3. 仅将以下四个模型复制到 `app/src/main/assets/`：

   ```powershell
   $modelNames = @('mobileclip2_s0_image.onnx', 'mobileclip2_s0_text_int8.onnx', 'image_model.tflite', 'text_model_dynamic_wi8.tflite')
   foreach ($name in $modelNames) {
       Copy-Item -LiteralPath (Join-Path 'build/model-downloads/extracted-app' $name) -Destination (Join-Path 'app/src/main/assets' $name)
   }
   ```

   Linux 使用 `cp build/model-downloads/extracted-app/*.onnx build/model-downloads/extracted-app/*.tflite app/src/main/assets/`。保留 `bpe_vocab_gz`、`mlkit/` 和已跟踪的 metadata/config 文件。不要把离线参照 `text_model.tflite` 重命名为动态 INT8 文件名。

4. 按[Android 运行步骤](../README_zh.md#run-on-android)构建。仅安装 ONNX 或 TFLite 模型对时，可构建其对应变体；同时构建两个变体需要全部四个文件。

## 还原 benchmark 输入

按同一校验流程检查所需 ZIP，再解压至独立暂存目录。依据 [manifest.json](manifest.json)将模型复制到对应位置：

| 文件 | 目标目录 |
|---|---|
| `mobileclip2_s0_image.ort`、`mobileclip2_s0_text_int8.ort` | `app/src/main/assets/` |
| `mobileclip2_s0_image_int8.onnx`、`mobileclip2_s0_image_int8.ort` | `build/mobileclip2-image-int8/` |
| `mobileclip_s0_image_fp32.onnx`、`mobileclip_s0_text_int8.onnx` | `build/mobileclip-accuracy-models/s0/` |
| `mobileclip_s2_image_fp32.onnx`、`mobileclip_s2_text_int8.onnx` | `build/mobileclip-accuracy-models/s2/` |
| `clip-image-int8.ort`、`clip-text-int8.ort` | `app/src/main/assets/` |

完整 [ORT 比较矩阵](../ort_benchmark_models.json)需要 App ONNX 模型对、v2 ORT 模型对、v1 模型对和旧 CLIP 模型对。准备模型文件不等于还原评测图片或原始特征。请遵循[benchmark 与精度协议](../README_zh.md#historical-models)，并记录实际运行库版本。打包这些文件不构成新一轮精度或速度测量。

## 发布与 CI

用于 CI 前，应验证无登录即可下载原始 ZIP，且字节与固定校验值一致。共享文件夹是手动下载入口，不要将其地址填入 CI 的 `MODELS_URL`。分享/预览页面或 HTML 下载确认响应不是模型 ZIP 直链。CI 需要无登录即可返回 ZIP 字节的下载地址及对应压缩包 SHA-256。[manifest.json](manifest.json)中的单个 ZIP 下载地址在验证前保持未设置。

已发布下载包应保持不变；模型字节变更时生成新包和校验值。模型遵循其上游条款：[Apple 模型许可](https://github.com/apple-aiml-research/ml-mobileclip/blob/main/LICENSE_MODELS)和 [OpenAI CLIP 许可](https://github.com/openai/CLIP/blob/main/LICENSE)。
