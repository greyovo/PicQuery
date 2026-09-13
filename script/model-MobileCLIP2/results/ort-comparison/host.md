# v1 / v2 主机速度对比

CPU：13th Gen Intel(R) Core(TM) i9-13900K；4 线程，batch=1。
每轮先预热 10 次，再记录 100 次，共 3 轮；模型顺序按固定种子打乱，同一时间只跑一个模型塔。

以下均为纯推理毫秒数，包含运行库输入输出拷贝，不含读图、缩放、分词、数据库和相似度搜索。并非手机端速度。

| 模型 | 图像 / 文本 MiB | 图像中位数 / P95 ms | 文本中位数 / P95 ms |
|---|---:|---:|---:|
| MobileCLIP-S0 v1 ONNX / FP32 image + INT8 text | 43.45 / 52.93 | 11.77 / 13.76 | 3.56 / 4.40 |
| MobileCLIP-S2 v1 ONNX / FP32 image + INT8 text | 136.43 / 61.34 | 35.33 / 39.77 | 7.08 / 9.04 |
| MobileCLIP2-S0 ONNX / FP32 image + INT8 text | 43.45 / 61.44 | 12.38 / 15.15 | 6.87 / 8.99 |
| MobileCLIP2-S0 ORT / FP32 image + INT8 text | 43.54 / 61.50 | 18.34 / 20.89 | 6.67 / 7.85 |
| Historical app CLIP ORT / INT8 image + INT8 text / stretch224 | 91.44 / 61.56 | 10.18 / 11.79 | 7.96 / 9.40 |

加载与首次执行：各轮中位数，单位 ms；文件缓存未清空，不代表冷磁盘或冷 App 启动。

| 模型 | 图像加载 | 图像首次执行 | 文本加载 | 文本首次执行 |
|---|---:|---:|---:|---:|
| MobileCLIP-S0 v1 ONNX / FP32 image + INT8 text | 175.13 | 12.01 | 191.31 | 3.82 |
| MobileCLIP-S2 v1 ONNX / FP32 image + INT8 text | 586.29 | 34.87 | 248.79 | 7.35 |
| MobileCLIP2-S0 ONNX / FP32 image + INT8 text | 178.62 | 12.63 | 278.94 | 7.36 |
| MobileCLIP2-S0 ORT / FP32 image + INT8 text | 164.70 | 20.31 | 270.84 | 7.51 |
| Historical app CLIP ORT / INT8 image + INT8 text / stretch224 | 380.42 | 10.48 | 244.22 | 8.78 |

主机运行库与 Android 运行库、CPU 指令集不同，不能把这些比例直接套用到真机。
本测试不包含 Android 测量；同代 ONNX/ORT 比较与跨代模型比较应分别解释。

[原始耗时、模型哈希和运行环境](host.json)。
