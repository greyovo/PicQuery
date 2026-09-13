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

完整两轮、加载/首次执行、PSS、温度、输出一致性和原始时延见[真机报告](pixel8a/真机速度对比.md)。

## 主机与导出目标

i9-13900K / WSL Ubuntu / CPU 4 线程，每塔三轮共 300 次计时。

<!-- archive-table: host-speed -->
| 比较 | ONNX 图像 / 文本 P50 ms | ORT 图像 / 文本 P50 ms |
|---|---:|---:|
| 与手机相同的 ARM 产物 | 12.38 / 6.87 | 18.34 / 6.67 |
| 独立导出的 amd64 产物 | 12.26 / 6.91 | 12.09 / 6.88 |

ARM 产物刻意禁用 x86 NCHWc，固定优化后的 ORT 在 x86 上不能按 ONNX 的默认加载路径重新选择这项优化。导出 amd64 版本后图像耗时回到接近 ONNX，支持此次主机落差与目标优化有关的解释。amd64 产物仅供主机对照，不能部署到 Android ARM。

[五模型主机报告](host.md) · [amd64 同源对照](host-amd64.md)。不要跨设备套用加速比。

## 模型与正确性

| Android 文件 | 大小 MiB | SHA256 |
|---|---:|---|
| `mobileclip2_s0_image.ort` | 43.54 | `c3050f9819b074cb05a2362393ba6c5dfc30f305ebfcd3e64508bc40710a1055` |
| `mobileclip2_s0_text_int8.ort` | 61.50 | `aa3ef129f2fb48e7af48bb2bcfc7928b4eac18d23cf9eaf31dcae92f618a783b` |

文件位于 `app/src/main/assets/`，二进制由 Git 忽略。两文件总大小略大于源 ONNX；格式转换没有再次量化，也不会自动缩小运行库。

ARM 和 amd64 两次导出均验证 8 组图像输入（5 张实际图片与黑/白/随机输入）、16 组文本（13 个检索词及 golden/空文本/满上下文），与同目标、同运行库的源 ONNX 最大绝对误差均为 **0**。形状、有限值、L2 归一化和 ORTM 签名均通过；ARM 产物不含 NCHWc。

真机每轮首张图片和首条文本另做 ONNX/ORT 数值比较，详见[最终验证](verification.json)。其余输入参与计时并检查有限 512 维输出，但没有逐条保存真机向量。这些检查不等于有标签准确率；[已有精度报告](../识别精度对比.md)保留其历史运行库和数据集口径，本次没有重跑。

v1-S0/S2 为官方 checkpoint 重新导出的部署精度模型；原 App MobileCLIP v1 的二进制不可得。历史 App CLIP ORT 则使用仓库已有文件。

## 复现与证据

Python 环境、导出和完整主机/真机命令见[模型说明](../../README_zh.md)。本轮实验新增文件只用于导出和 benchmark，未更改 Android App 的生产模型选择。

Windows 上传器改用 `adb push` 临时文件与 `run-as cp`，等待传输完成，并核对大小和 SHA256。实测 `exec-in dd` 曾在写入完成前返回，而 shell stdin 曾截断二进制；临时文件在复制后清理。

- [ARM 导出记录](export-arm.json)、[amd64 导出记录](export-amd64.json)：转换选项、依赖、模型哈希和每条输出误差。
- [脚本检查](script-validation.json)：32 项离线检查，含 29 项错误输入拒绝和旧汇总回归。
- [导出目录保护](export-target-guards.json)：默认 ARM、独立 amd64 目录正常；amd64 未指定目录或使用 ARM 资产目录均被拒绝。
- [最终验证](verification.json)：两目标哈希、主机/手机同产物、样本计数、数值一致性与脚本 SHA。
- [APK 复用与已安装文件核对](pixel8a/build-verification.json)：沿用依赖升级后的 APK，本次没有改 Android 代码。

归档 JSON 保留测量证据和哈希。模型二进制与生成的输入、特征文件不纳入 Git，新克隆须重新生成后才能复跑推理。历史脚本哈希标识当时实验所用实现，不要求与后续清理版本相同。

本次未验证 GPU/NPU、Release/R8、其他设备、持续发热或端到端相册搜索耗时。
