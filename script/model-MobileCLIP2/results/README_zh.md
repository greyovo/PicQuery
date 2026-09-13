# 测量结果归档

[English](README.md) · [模型导出与复现](../README_zh.md)

这里记录的是 2026-09-13 的不同实验。每个数值都应保留对应的运行库、平台、精度和样本范围。下列表格由归档 JSON 校验，不是本次文档整理重新测量的结果。

## 当前图像量化实验

两种配置均使用 MobileCLIP2 S0 ORT 文件和**同一个动态 INT8 文本塔**。图像候选为**混合精度**：46 个 Conv、6 个 MatMul 和 3 个 Gemm 量化，49 个敏感 Conv 保留 FP32；并非全 INT8 图像模型。校准使用 400 张训练图片，与评测集分离。

准确率覆盖 CIFAR-100 test 的 2,000 张图片（每类 20 张）和 Imagenette 完整 val 的 3,925 张图片，使用固定英文类别提示词。完整报告包含 Top1/Top5、类别查询 P@10/mAP、逐类结果、成对 95% 区间及预测翻转。类别查询检索不等于自由描述检索。

<!-- archive-table: current-accuracy -->
| 平台 / 运行库 | 图像配置 | CIFAR-100 Top1（正确数 / 总数） | Imagenette Top1（正确数 / 总数） |
|---|---|---:|---:|
| 主机 x86 CPU / ORT 1.29.0 | FP32 | 75.30% (1506 / 2000) | 98.11% (3851 / 3925) |
| 主机 x86 CPU / ORT 1.29.0 | 混合 INT8 | 73.60% (1472 / 2000) | 98.06% (3849 / 3925) |
| Pixel 8a ARM64 CPU / ORT 1.29.0 | FP32 | 74.60% (1492 / 2000) | 98.04% (3848 / 3925) |
| Pixel 8a ARM64 CPU / ORT 1.29.0 | 混合 INT8 | 73.90% (1478 / 2000) | 97.96% (3845 / 3925) |

所有会话使用 4 个 CPU 线程。手机在 Android API 37 上完整导出所有图像与类别文本特征，主机随后计算归一化、排序和指标；两种图像配置的手机文本输出逐字节一致。同一量化模型在 x86 与 ARM 上可能存在不同数值行为，因此主机结果单独列示。

[主机准确率、算子覆盖与真机时延](image-int8/README_zh.md) · [手机完整准确率](image-int8/pixel8a-accuracy/README_zh.md) · [实验过程与初始失败候选](image-int8/experiment.md)。

独立短时 benchmark 使用 Pixel 8a / Tensor G3 / Android 17 / ORT 1.29.0 / CPU 4 线程：两轮反向顺序，每塔每轮预热 10 次、计时 100 次，三个模型共 1,200 条时延；输入循环使用五张图片和 13 条文本，不包含完整准确率特征导出时长。

<!-- archive-table: current-speed -->
| 模型 | 图像 P50 ms | 文本 P50 ms | 图像文件 MiB |
|---|---:|---:|---:|
| MobileCLIP2 S0 FP32 图像 + 动态 INT8 文本 | 67.58 | 20.88 | 43.54 |
| MobileCLIP2 S0 混合 INT8 图像 + 同一文本 | 53.46 | 20.66 | 13.52 |
| 历史 CLIP INT8 图像 + INT8 文本 | 26.75 | 21.88 | 91.44 |

原始时延、P95、首次执行、加载与温度观察保留在链接报告中。短时 CPU 测量不能证明持续吞吐量、App 峰值内存、GPU/NPU 性能或端到端相册搜索耗时。

## ORT 格式与 v1 对比

[双语 ORT/v1 报告](ort-comparison/README_zh.md)：在同一 Pixel 8a 上使用 ORT 1.29.0 重新测量官方 v1-S0/v1-S2 重导出、v2-S0 ONNX、v2-S0 ARM ORT 与历史 CLIP ORT，共五个模型。MobileCLIP 行使用 FP32 图像 + 动态 INT8 文本，历史 CLIP 为双塔 INT8。两轮共 2,000 条原始时延。本轮 ORT 缩短了会话加载时间，预热后的 ONNX/ORT 推理耗时接近。

配套 x86 主机对比使用三轮，每塔共 300 次调用。Android ARM 与单独导出的 amd64 ORT 是不同目标产物，不能将 amd64 文件部署到 Android，也不能将主机加速比套用到手机。

**原 App MobileCLIP v1 二进制不可得。** v1 行是官方 checkpoint 按部署精度重新导出，并非原 App 二进制的精确复现。历史 CLIP 行使用仓库已有 ORT 文件与原来的 stretch224/归一化路径，其结构与预处理不同，仅作参考。

## 历史 ONNX/TFLite 准确率

[原始报告](识别精度对比.md) · [原始指标](accuracy-results.json) · [样本清单](accuracy-results-manifest.json)。

这是**主机 CPU** 实验，使用 ONNX Runtime **1.25.0** 和 Python LiteRT **2.1.4**、4 线程，同样的 2,000/3,925 张评测样本与固定英文类别提示词。下表为五个部署行；原始文件共九行，另外包含 FP32 文本塔的量化损失参照。MobileCLIP 图像塔为 FP32、文本权重为动态 INT8，历史 CLIP 为双塔 INT8。

<!-- archive-table: historical-accuracy -->
| 模型 / 格式 | CIFAR-100 Top1 | Imagenette Top1 |
|---|---:|---:|
| MobileCLIP v1-S0 / ONNX | 70.95% | 98.17% |
| MobileCLIP v1-S2 / ONNX | 80.45% | 99.31% |
| MobileCLIP2-S0 / ONNX | 75.05% | 98.04% |
| MobileCLIP2-S0 / TFLite | 77.45% | 99.41% |
| 历史 CLIP / ORT | 53.75% | 83.62% |

这些历史主机结果不能代表手机准确率，也不能与当前 ORT 1.29.0 结果混合统计。某个小数据集上的准确率更高，不代表格式具有普遍优势。

## 其他归档证据

- [原始主机速度对比](speed-comparison.md)：保留原始语言及对应运行库、主机配置。
- [最初 Pixel 8a 速度报告](pixel8a-speed/真机速度对比.md)与 [TFLite 线程诊断](pixel8a-tflite-diagnostics/诊断报告.md)：保留中文原始记录。最初 LiteRT 1.4.1 的 lazy XNNPACK delegate 未应用请求的工作线程数；判断多线程公平性前，应先阅读诊断与显式 native delegate 对照。
- [本轮 Android 构建/功能验证](submission/android-validation.json)与[本轮脚本/归档验证](submission/code-validation.json)：提交整理的检查证据，与历史准确率、时延实验分开。

## 新克隆能复现什么

JSON 指标、逐图预测、配对统计、运行回执、清单与 SHA256 记录已归档。大型模型、预处理输入张量和原始 `.f32` 特征在本地生成，不纳入 Git。[离线测试](../tests/README.md)不加载模型二进制，可审计归档正确数、报告数值、双语表格和合成错误输入；缺少原始特征时，明确跳过原始数组检查。

完整余弦排序或推理重算须按[复现命令](../README_zh.md)先生成模型、输入及特征产物。JSON 是归档证据，不表示新克隆已包含全量复跑所需的所有字节。历史哈希标识各次实验的确切实现；维护期间当前源文件哈希可以改变，但不能改写归档测量值。
