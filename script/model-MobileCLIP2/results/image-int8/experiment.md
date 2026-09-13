# 图像 INT8 实验记录

2026-09-13，MobileCLIP2-S0 / dfndr2b，Python 与 Android ONNX Runtime 1.29.0。
两个 v2 模型共用同一动态 INT8 文本 ORT，图像输入都为 RGB、256px center crop、[0,1]。
最终候选冻结后才运行完整评测，之后没有依据 test/val 结果继续调整量化参数。

## 两个候选

| 图像配置 | ORT MiB | 主机 CIFAR-100 Top1 | 主机 Imagenette Top1 |
|---|---:|---:|---:|
| FP32 基线 | 43.54 | 75.30% | 98.11% |
| 首次：所有 eligible Conv / MatMul / Gemm 量化 | 11.79 | 1.00% | 12.41% |
| 最终：敏感卷积保留 FP32，其他重算子 INT8 | 13.52 | 73.60% | 98.06% |

“所有 eligible 算子量化”也不表示整张图没有浮点算子；两次候选的输入和 512 维输出均为 FP32。
完整指标、预测翻转和配对置信区间见[主机准确率与真机速度报告](README.md)。
首次候选的[逐图评测](initial-accuracy.json)、[模型记录](initial-quantization-metadata.json)及
[训练校准清单](initial-calibration-manifest.json)保留用于复核失败，而不作为可部署模型。

## 为什么保留部分 FP32

首个方案使用 S8S8 QDQ、按通道权重量化和 MinMax 激活校准。
校准是 CIFAR-100 train 200 张加 Imagenette train 200 张，总计 400 张；全部校准块合并后使用，
与 5,925 张评测样本的原生内容及 RGB 像素哈希均无交集。

首方案的模型格式、输出形状、有限数值及 ONNX→ORT 转换一致性都通过，但对 FP32 的向量发生严重偏移。
[CPU 内核对照](initial-kernel-diagnostics.json)显示禁用图优化、执行浮点 Q/DQ 也出现误差；
[初次手机探针](initial-probe/verification.json)得到相同的坏向量，因此不能把失败只归因于 x86 执行 ARM ORT。

在一张**训练**图片的[逐层诊断](training-layer-diagnostics.json)中，stem 首层 cosine 为 0.999986，
后续 depthwise/pointwise 分别降到约 0.9406/0.4148，第一阶段 7×7 depthwise 后约 0.1403。
这支持对敏感重参数化、grouped/depthwise Conv 保留 FP32 的结构性修改。
最终量化 46 个 Conv、6 个 MatMul、3 个 Gemm，保留 49 个 Conv FP32。
实际 ARM ORT 包含 QLinearConv、QLinearMatMul、QGemm；[详细覆盖](quantization-metadata.json)可核对到节点名。

这是一次训练数据驱动的混合精度修正；没有做 QAT、参数搜索或使用 test/val 微调。
完整 test/val 用于分别记录两个已冻结候选，不能把有限数据集结果理解为所有照片分布的保证。

## 真机速度和数值差异

Pixel 8a / Tensor G3，CPU 4 线程，两轮正反顺序，各预热 10 次、计时 100 次。
合并图像 P50：FP32 **67.58 ms**、混合 INT8 **53.46 ms**，延迟降低约 **20.9%**，吞吐等效提升约 **26.4%**。
历史 App CLIP INT8 为 **26.75 ms**；v2 混合 INT8 仍约为它的两倍耗时。
各模型架构、算子、分辨率不同，量化到相同位宽并不代表相同工作量。
阶段采样进程 PSS 为 227.2/228.7 MiB，文件缩小没有在这项内存观测中变成同等比例下降。

六次短时测速完成后额外核对首输出：

| 图像 | 同一模型主机↔手机 cosine |
|---|---:|
| v2 FP32 | 0.999999999999 |
| v2 混合 INT8 | 0.987342 |
| 历史 CLIP INT8 | 0.995813 |

两种量化图像没有通过预先采用的严格 0.9999 向量一致性门槛，
[一致性报告](pixel8a/host-device-parity.json)保留 `failed`，没有调低门槛。
所有文本通过 0.995 门槛。该检查仅有首图/首条文本，cosine 本身不等于准确率；
因此追加相同 5,925 张图片在手机运行的[完整准确率评测](pixel8a-accuracy/README.md)。
完整手机评测不用于改动已经冻结的模型，耗时也不混入短时 benchmark。

完整手机评测已完成，每个模型实际计算 5,925 张图片和 110 条类别文本：

| 数据集 | 手机 FP32 图像 Top1 | 手机混合 INT8 图像 Top1 | 差值 pp |
|---|---:|---:|---:|
| CIFAR-100 | 74.60% | 73.90% | -0.70 |
| Imagenette | 98.04% | 97.96% | -0.08 |

两次手机运行的全部 110 条文本向量[逐字节一致](pixel8a-accuracy/shared-phone-text-verification.json)，
这张表的差异隔离到图像模型。配对 95% CI 分别为 [-1.80,+0.30] 和 [-0.31,+0.15] pp；
它们描述当前样本的不确定性，不能据此宣称量化无损。完整 Top5、类别检索 P@10/mAP、
逐图翻转与主机/手机差异见[手机报告](pixel8a-accuracy/README.md)。

本候选用少量实测准确率损失换取约 21% 的短时图像推理延迟下降，以及 69% 的图像 ORT 文件缩减。
模型对合计由 105.05 MiB 降到 75.03 MiB；文本塔仍为 61.50 MiB。

### FP32 图像基线的平台差异来自哪里

完整手机基线为 CIFAR-100 **1492/2000 = 74.60%**、Imagenette **3848/3925 = 98.04%**。
它仍使用动态 INT8 文本塔。“FP32 图像”不等于整个模型对都使用 FP32。
在全部输入上交叉替换图像/文本特征，得到以下正确数：

| 图像特征来源 | 文本特征来源 | CIFAR-100 | Imagenette |
|---|---|---:|---:|
| 主机 | 主机 | 1506 | 3851 |
| 手机 | 主机 | 1506 | 3851 |
| 主机 | 手机 | 1492 | 3848 |
| 手机 | 手机 | 1492 | 3848 |

5,925 张 FP32 图像的 host/phone cosine 最低为 0.999999999994，替换图像特征没有预测翻转。
110 条动态 INT8 文本的最低 cosine 为 0.9979067；换用手机文本后产生 51/8 个预测翻转，
完全复现两个数据集的主机/手机准确率差异。
因此这批基线差异可归因到文本塔输出，而不能仅凭该诊断定位具体底层 kernel。
[原始交叉诊断](pixel8a-accuracy/fp32-platform-diagnostics.json)记录缓存签名、输入/模型 SHA、
全部翻转样本和向量统计。手机 FP32 与图像 INT8 的主比较必须固定同一个手机文本塔。

## 复现与资产

[模型指南](../../README.md#image-int8-quantization)提供基线、校准、导出、评测和测速命令。
最终导出命令必须带 `--keep-sensitive-fp32`；省略此开关是失败的首次量化策略。

- [量化脚本](../../quantize_mobileclip2_image.py)
- [三模型配置](../../image_int8_models.json)
- [最终量化记录](quantization-metadata.json)与[400 张训练校准清单](calibration-manifest.json)
- [所需 ORT 算子](required_operators_and_types.config)
- [报告验证 45 项检查](report-validation-tests.json)与[CLI/来源检查](cli-verification.json)

最终模型位于 Git 忽略的 `build/mobileclip2-image-int8/`，导出器会记录完整哈希并生成 ONNX、ARM ORT 和算子配置：

```text
mobileclip2_s0_image_int8.ort
SHA256 d5b21e38a87cfeb802ffda26051cd7e59ba6e5ea9c6f10fd1d36780067afe031
bytes 14179240
```

`quantization-metadata.json` 是导出时的原样归档，里面的 `accuracy_not_validated` 描述导出时状态；
后续独立准确率报告描述质量评测结果。归档里引用的模型/校准文件原始路径在 `build/`，
复现导出后可由验证器重新核对，归档没有篡改为“全部验证通过”。
当前 App 的 FP32 图像资产和索引不被本实验替换；采用不同图像向量的模型时需要重建照片索引。
