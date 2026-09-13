# MobileCLIP2 ORT 导出与 v1 对比

[English](README.md) · [全部测量系列](../README_zh.md)

2026-09-13，本次 Python 与 Android 均使用 ONNX Runtime **1.29.0**。MobileCLIP 系列保持图像 FP32、文本动态 INT8；旧 App CLIP 的图像和文本均为 INT8。

已生成 Android ARM 的两份 ORT 模型，主机导出校验逐值一致。Pixel 8a 上的主要收益是会话加载缩短，预热后的推理耗时接近 ONNX。

## 真机：相同运行库重新测量旧模型

Pixel 8a / Tensor G3 / Android 17 / ARM64 / 4 KB 页，Debug APK，CPU intra-op=4、inter-op=1、batch=1。每塔每轮预热 10 次、计时 100 次，共两轮，第二轮反转模型顺序。模型之间冷却 30 秒，没有额外持续负载测试。两轮合并后按 nearest rank 计算分位数，所有模型与两塔共 2,000 条原始时延。

<!-- archive-table: device-speed -->
| 模型 | 图像 P50 / P95 ms | 文本 P50 / P95 ms | 两塔模型总 MiB |
|---|---:|---:|---:|
| MobileCLIP v1-S0 ONNX | 71.26 / 78.22 | 15.94 / 17.98 | 96.39 |
| MobileCLIP v1-S2 ONNX | 210.91 / 266.13 | 27.81 / 36.51 | 197.77 |
| MobileCLIP2-S0 ONNX | 69.69 / 76.44 | 21.54 / 24.05 | 104.89 |
| MobileCLIP2-S0 ORT (ARM) | 70.00 / 76.96 | 21.77 / 24.78 | 105.05 |
| 旧 App CLIP ORT | 28.19 / 31.20 | 24.13 / 26.66 | 152.99 |

加载时间为两轮中位数，已校验模型 SHA，因此文件缓存可能已热；图像包含首次 ORT 环境初始化，不能当成冷 App 启动或直接与文本加载对比。

<!-- archive-table: device-loading -->
| MobileCLIP2 格式 | 图像会话加载 ms | 文本会话加载 ms | 图像首次推理 ms | 文本首次推理 ms |
|---|---:|---:|---:|---:|
| ONNX | 216.03 | 298.15 | 171.82 | 106.50 |
| ORT / ARM | 157.39 | 181.60 | 123.84 | 107.18 |

本次 ORT 图像/文本加载分别减少约 **27% / 39%**。推理小幅差异不应解释为稳定加速。v1-S0 文本塔比 v2-S0 更轻；跨代速度还受模型结构、权重大小和旧 CLIP 的 224 像素预处理影响。

记录的电池温度为 33.1–35.5 °C，thermal status 为 [0]，USB 充电状态保持原样。电池温度不是 SoC 结温，固定冷却也不保证完全同温。v1-S2 文本 P50 两轮为 21.62 / 35.12 ms，波动明显，不能仅凭合并值评价稳定性能。PSS 是阶段采样、单塔进程值，不是双塔 App 峰值内存。

保留的聚合结果与协议见 [measured-summary.json](../measured-summary.json)，不包含逐次时延和设备日志。

## 主机与导出目标

i9-13900K / WSL Ubuntu / CPU 4 线程，每塔三轮共 300 次计时。

<!-- archive-table: host-speed -->
| 比较 | ONNX 图像 / 文本 P50 ms | ORT 图像 / 文本 P50 ms |
|---|---:|---:|
| 与手机相同的 ARM 产物 | 12.38 / 6.87 | 18.34 / 6.67 |
| 独立导出的 amd64 产物 | 12.26 / 6.91 | 12.09 / 6.88 |

ARM 产物刻意禁用 x86 NCHWc，固定优化后的 ORT 在 x86 上不能按 ONNX 的默认加载路径重新选择这项优化。导出 amd64 版本后图像耗时回到接近 ONNX，支持此次主机落差与目标优化有关的解释。amd64 产物仅供主机对照，不能部署到 Android ARM。

两组主机对比均保存在[精简测量摘要](../measured-summary.json)中，不要跨设备套用加速比。

## 模型与正确性

| Android 文件 | 大小 MiB | SHA256 |
|---|---:|---|
| `mobileclip2_s0_image.ort` | 43.54 | `c3050f9819b074cb05a2362393ba6c5dfc30f305ebfcd3e64508bc40710a1055` |
| `mobileclip2_s0_text_int8.ort` | 61.50 | `aa3ef129f2fb48e7af48bb2bcfc7928b4eac18d23cf9eaf31dcae92f618a783b` |

文件位于 `app/src/main/assets/`，二进制由 Git 忽略。两文件总大小略大于源 ONNX；格式转换没有再次量化，也不会自动缩小运行库。

ARM 和 amd64 两次导出均验证 8 组图像输入（5 张实际图片与黑/白/随机输入）、16 组文本（13 个检索词及 golden/空文本/满上下文），与同目标、同运行库的源 ONNX 最大绝对误差均为 **0**。形状、有限值、L2 归一化和 ORTM 签名均通过；ARM 产物不含 NCHWc。

真机每轮首张图片和首条文本另做了 ONNX/ORT 数值比较，其余输入参与计时并检查有限 512 维输出。这些检查不等于有标签准确率。[历史精度摘要](../README_zh.md#历史-onnxtflite-准确率)保留对应运行库和数据集口径，本次格式比较没有重跑准确率。

v1-S0/S2 为官方 checkpoint 重新导出的部署精度模型；原 App MobileCLIP v1 的二进制不可得。历史 App CLIP ORT 则使用仓库已有文件。

## 本地复现

Python 环境、导出与主机/真机命令见[模型指南](../../README_zh.md)。本轮实验未改变 App 的生产模型选择。

Git 保留 [measured-summary.json](../measured-summary.json) 与这些双语聚合表格，不包含导出/调试日志、逐输入误差、原始时延数组、环境转储或脚本验证回执。模型二进制及生成的输入/特征需要在本地准备。按文档命令将输出写入被忽略的 `build/` 目录，取得新一轮原始数据并在自己的运行库与设备上验证。

本次未验证 GPU/NPU、Release/R8、其他设备、持续发热或端到端相册搜索耗时。
