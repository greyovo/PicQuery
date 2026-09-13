# MobileCLIP2 图像 INT8 实测

[English](<README.md>)

主比较固定同一个动态 INT8 文本塔，仅替换 MobileCLIP2 S0 图像塔的量化配置；两组均使用 ORT 文件。本候选是混合精度，INT8 算子覆盖及保留的 FP32 节点在下文列明。历史 CLIP 双 INT8 作为辅助参考。

## 主机识别精度

本节准确率在主机 CPU 上评测，ONNX Runtime 1.29.0、4 线程；不能直接视作 Android 准确率。手机完整评测单独记录。
主机环境：Linux-6.6.87.2-microsoft-standard-WSL2-x86_64-with-glibc2.39。
CIFAR-100 使用 test 每类固定抽 20 张，共 2,000 张；Imagenette 使用完整 val，共 3,925 张。固定英文单模板 `a photo of a {class}`，不在评测集选择提示词或量化参数。

| 数据集 | 模型 | Top1 | Top5 | 类别查询 P@10 | 类别查询 mAP |
|---|---|---:|---:|---:|---:|
| cifar100 | MobileCLIP2 S0 · FP32 图像 | 75.30% | 93.90% | 84.70% | 75.73% |
| cifar100 | MobileCLIP2 S0 · 混合 INT8 图像 | 73.60% | 93.20% | 83.70% | 74.18% |
| cifar100 | 历史 CLIP · 双塔 INT8 | 53.75% | 80.90% | 60.60% | 47.57% |
| imagenette | MobileCLIP2 S0 · FP32 图像 | 98.11% | 99.90% | 100.00% | 99.30% |
| imagenette | MobileCLIP2 S0 · 混合 INT8 图像 | 98.06% | 99.90% | 100.00% | 99.24% |
| imagenette | 历史 CLIP · 双塔 INT8 | 83.62% | 99.41% | 81.00% | 74.16% |

P@10 和 mAP 使用类别文本查询检索同一数据集的全部图片，按类别平均；它们不是自然语言描述与图片配对的 Recall@K。CIFAR 原图仅 32×32；Imagenette 只有 10 类，不能代替 ImageNet-1k 或真实相册测试。

## 图像量化差值

差值为混合 INT8 − FP32，单位百分点（pp）。95% 区间使用相同图片的 2,000 次成对 bootstrap，固定 seed 20260913；仅反映当前数据分布的抽样不确定性。

| 数据集 | Top1 差值 pp | 配对 95% CI pp | FP32 独对 / INT8 独对 | 预测翻转 | 其中两者均错的翻转 |
|---|---:|---:|---:|---:|---:|
| cifar100 | -1.70 | [-2.70, -0.70] | 71 / 37 | 190 / 2000 (9.50%) | 82 |
| imagenette | -0.05 | [-0.28, +0.20] | 13 / 11 | 30 / 3925 (0.76%) | 6 |

预测翻转包括纠错、由对变错、以及两次均错但类别不同；翻转率和向量 cosine 都不能替代识别准确率。逐类结果见原始准确率 JSON。

## 校准与模型大小

静态量化使用预先固定的 MinMax 校准：CIFAR-100 train 每类 2 张、Imagenette train 每类 20 张，各 200 张，总计 400 张，seed 20260913。评测图片不参与校准，也未根据评测结果挑选参数。
报告核对模型与共享文本 SHA256、相同预处理、训练样本记录与评测清单；独立重查样本 ID/原生哈希不相交，全部 RGB 哈希去重检查由量化导出器记录并关联相同评测样本。

| 模型 | 图像 MiB | 文本 MiB | 合计 MiB |
|---|---:|---:|---:|
| MobileCLIP2 S0 · FP32 图像 | 43.54 | 61.50 | 105.05 |
| MobileCLIP2 S0 · 混合 INT8 图像 | 13.52 | 61.50 | 75.03 |
| 历史 CLIP · 双塔 INT8 | 91.44 | 61.56 | 152.99 |

QDQ: activation=QInt8, weight=QInt8, per_channel=True.
图像文件大小为 FP32 的 31.1%；具体算子覆盖、保留 FP32 的部分和向量诊断见量化 metadata。
metadata 中的 passed 表示模型格式、输出契约或转换一致性检查通过，不表示 INT8 识别质量达标；质量变化应以上面的实测准确率为依据。

量化 ONNX 图中的重算子覆盖（融合后的 ORT 算子清单另见 metadata）：

| 算子 | 总数 | 两输入均经 INT8 量化 | 保留浮点或部分量化 |
|---|---:|---:|---:|
| Conv | 95 | 46 | 49 |
| MatMul | 6 | 6 | 0 |
| Gemm | 3 | 3 | 0 |

本候选采用混合精度：49 个重算子保留 FP32，0 个重算子仅部分输入量化。

<details><summary>保留浮点或部分量化的重算子（49 个）</summary>

```text
Conv | INT8 inputs: 0 | /visual/trunk/stem/stem.0/reparam_conv/Conv
Conv | INT8 inputs: 0 | /visual/trunk/stem/stem.1/reparam_conv/Conv
Conv | INT8 inputs: 0 | /visual/trunk/stem/stem.2/reparam_conv/Conv
Conv | INT8 inputs: 0 | /visual/trunk/stages.0/blocks/blocks.0/token_mixer/reparam_conv/Conv
Conv | INT8 inputs: 0 | /visual/trunk/stages.0/blocks/blocks.0/mlp/conv/conv/Conv
Conv | INT8 inputs: 0 | /visual/trunk/stages.0/blocks/blocks.1/token_mixer/reparam_conv/Conv
Conv | INT8 inputs: 0 | /visual/trunk/stages.0/blocks/blocks.1/mlp/conv/conv/Conv
Conv | INT8 inputs: 0 | /visual/trunk/stages.1/downsample/proj/proj.0/reparam_conv/Conv
Conv | INT8 inputs: 0 | /visual/trunk/stages.1/downsample/proj/proj.1/reparam_conv/Conv
Conv | INT8 inputs: 0 | /visual/trunk/stages.1/blocks/blocks.0/token_mixer/reparam_conv/Conv
Conv | INT8 inputs: 0 | /visual/trunk/stages.1/blocks/blocks.0/mlp/conv/conv/Conv
Conv | INT8 inputs: 0 | /visual/trunk/stages.1/blocks/blocks.1/token_mixer/reparam_conv/Conv
Conv | INT8 inputs: 0 | /visual/trunk/stages.1/blocks/blocks.1/mlp/conv/conv/Conv
Conv | INT8 inputs: 0 | /visual/trunk/stages.1/blocks/blocks.2/token_mixer/reparam_conv/Conv
Conv | INT8 inputs: 0 | /visual/trunk/stages.1/blocks/blocks.2/mlp/conv/conv/Conv
Conv | INT8 inputs: 0 | /visual/trunk/stages.1/blocks/blocks.3/token_mixer/reparam_conv/Conv
Conv | INT8 inputs: 0 | /visual/trunk/stages.1/blocks/blocks.3/mlp/conv/conv/Conv
Conv | INT8 inputs: 0 | /visual/trunk/stages.1/blocks/blocks.4/token_mixer/reparam_conv/Conv
Conv | INT8 inputs: 0 | /visual/trunk/stages.1/blocks/blocks.4/mlp/conv/conv/Conv
Conv | INT8 inputs: 0 | /visual/trunk/stages.1/blocks/blocks.5/token_mixer/reparam_conv/Conv
Conv | INT8 inputs: 0 | /visual/trunk/stages.1/blocks/blocks.5/mlp/conv/conv/Conv
Conv | INT8 inputs: 0 | /visual/trunk/stages.2/downsample/proj/proj.0/reparam_conv/Conv
Conv | INT8 inputs: 0 | /visual/trunk/stages.2/downsample/proj/proj.1/reparam_conv/Conv
Conv | INT8 inputs: 0 | /visual/trunk/stages.2/blocks/blocks.0/token_mixer/reparam_conv/Conv
Conv | INT8 inputs: 0 | /visual/trunk/stages.2/blocks/blocks.0/mlp/conv/conv/Conv
Conv | INT8 inputs: 0 | /visual/trunk/stages.2/blocks/blocks.1/token_mixer/reparam_conv/Conv
Conv | INT8 inputs: 0 | /visual/trunk/stages.2/blocks/blocks.1/mlp/conv/conv/Conv
Conv | INT8 inputs: 0 | /visual/trunk/stages.2/blocks/blocks.2/token_mixer/reparam_conv/Conv
Conv | INT8 inputs: 0 | /visual/trunk/stages.2/blocks/blocks.2/mlp/conv/conv/Conv
Conv | INT8 inputs: 0 | /visual/trunk/stages.2/blocks/blocks.3/token_mixer/reparam_conv/Conv
Conv | INT8 inputs: 0 | /visual/trunk/stages.2/blocks/blocks.3/mlp/conv/conv/Conv
Conv | INT8 inputs: 0 | /visual/trunk/stages.2/blocks/blocks.4/token_mixer/reparam_conv/Conv
Conv | INT8 inputs: 0 | /visual/trunk/stages.2/blocks/blocks.4/mlp/conv/conv/Conv
Conv | INT8 inputs: 0 | /visual/trunk/stages.2/blocks/blocks.5/token_mixer/reparam_conv/Conv
Conv | INT8 inputs: 0 | /visual/trunk/stages.2/blocks/blocks.5/mlp/conv/conv/Conv
Conv | INT8 inputs: 0 | /visual/trunk/stages.2/blocks/blocks.6/token_mixer/reparam_conv/Conv
Conv | INT8 inputs: 0 | /visual/trunk/stages.2/blocks/blocks.6/mlp/conv/conv/Conv
Conv | INT8 inputs: 0 | /visual/trunk/stages.2/blocks/blocks.7/token_mixer/reparam_conv/Conv
Conv | INT8 inputs: 0 | /visual/trunk/stages.2/blocks/blocks.7/mlp/conv/conv/Conv
Conv | INT8 inputs: 0 | /visual/trunk/stages.2/blocks/blocks.8/token_mixer/reparam_conv/Conv
Conv | INT8 inputs: 0 | /visual/trunk/stages.2/blocks/blocks.8/mlp/conv/conv/Conv
Conv | INT8 inputs: 0 | /visual/trunk/stages.2/blocks/blocks.9/token_mixer/reparam_conv/Conv
Conv | INT8 inputs: 0 | /visual/trunk/stages.2/blocks/blocks.9/mlp/conv/conv/Conv
Conv | INT8 inputs: 0 | /visual/trunk/stages.3/downsample/proj/proj.0/reparam_conv/Conv
Conv | INT8 inputs: 0 | /visual/trunk/stages.3/downsample/proj/proj.1/reparam_conv/Conv
Conv | INT8 inputs: 0 | /visual/trunk/stages.3/pos_emb/reparam_conv/Conv
Conv | INT8 inputs: 0 | /visual/trunk/stages.3/blocks/blocks.0/mlp/conv/conv/Conv
Conv | INT8 inputs: 0 | /visual/trunk/stages.3/blocks/blocks.1/mlp/conv/conv/Conv
Conv | INT8 inputs: 0 | /visual/trunk/final_conv/reparam_conv/Conv
```

</details>

## Android 真机耗时

Pixel 8a / Tensor G3 / Android 17 / ONNX Runtime 1.29.0 / CPU 4 threads.
每模型两轮，每塔首次执行单列、预热 10 次、正式记录 100 次，总计 1,200 条时延；第二轮反向顺序。
运行区间 UTC：2026-09-13T08:21:11.9876784Z 至 2026-09-13T08:24:51.0366176Z。

| 模型 | 图片 P50 / P95 ms | 文本 P50 / P95 ms | 两轮图片 P50 | 两轮文本 P50 |
|---|---:|---:|---:|---:|
| MobileCLIP2 S0 · FP32 图像 | 67.58 / 72.04 | 20.88 / 23.40 | 67.29 / 67.92 | 20.77 / 20.95 |
| MobileCLIP2 S0 · 混合 INT8 图像 | 53.46 / 57.06 | 20.66 / 23.36 | 53.28 / 53.60 | 20.68 / 20.65 |
| 历史 CLIP · 双塔 INT8 | 26.75 / 29.44 | 21.88 / 24.52 | 26.53 / 26.90 | 21.93 / 21.78 |

FP32 / 混合 INT8 图片 P50 比值为 1.264；大于 1 表示 INT8 更快。两轮原始时延合并后按 nearest rank 计算分位数。

计时包含预加载输入拷贝、运行库执行和输出拷贝，不含读图、缩放、分词或相似度搜索。输入循环使用五张公开图片与 13 条文本；它与完整数据集的准确率评估范围不同。

| 模型 | 图片加载 ms（各轮） | 图片首次执行 ms | 文本加载 ms | 文本首次执行 ms | thermal status | 观测进程 PSS MiB |
|---|---:|---:|---:|---:|---|---:|
| MobileCLIP2 S0 · FP32 图像 | 149.74 / 177.82 | 140.77 / 171.07 | 179.41 / 175.47 | 89.65 / 99.13 | [0] | 227.2 |
| MobileCLIP2 S0 · 混合 INT8 图像 | 131.26 / 139.02 | 107.01 / 114.30 | 182.29 / 193.57 | 98.58 / 64.62 | [0] | 228.7 |
| 历史 CLIP · 双塔 INT8 | 411.34 / 408.26 | 107.92 / 101.38 | 211.84 / 205.92 | 100.11 / 100.26 | [0] | 259.8 |

每塔加载前已校验模型 SHA256，文件缓存可能已热，不能称为冷磁盘启动。PSS 是单塔运行时的阶段采样，不是峰值或 App 双塔同时驻留内存。首输出检查有限、非零和 512 维；历史 CLIP 输出保留原始范数。此处不要求 FP32 与量化模型向量相等。
第一轮额外持续测试设为 0 秒；短时双轮结果不能说明长时间相册索引的热稳态。测试未锁频或绑核，温度、USB 充电、系统调度仍可能影响耗时。

## 可复核文件

[归档实验过程与初始失败方案](<experiment.md>) · [归档量化 metadata](<quantization-metadata.json>) · [归档手机完整准确率](<pixel8a-accuracy/README_zh.md>)

[主机准确率、逐图预测及依赖版本](<accuracy.json>) · [评测样本清单](<accuracy-manifest.json>) · [机器验证结果](<README-validation.json>)

验证器重算 Top1、配对计数、翻转和 bootstrap CI；Top5 与检索指标验证导出值及逐类聚合。准确率文件没有导出完整排序，本报告未独立重算 Top5/检索排序。历史 CLIP 使用双 INT8、stretch224 与原 App 归一化，辅助行不能用于单独归因图像量化的收益。

JSON 测量值、逐图预测、清单和哈希均已归档。大型模型、输入文件和原始特征数组在本地生成，不纳入 Git。新克隆可审计归档计数与报告数值；完整推理或排序重算须先重新生成这些产物。

[真机执行元数据](<pixel8a/run-metadata.json>) · [真机模型与输入清单](<pixel8a/fixture-manifest.json>)。同目录各模型 JSON 保留原始时延和首输出。
