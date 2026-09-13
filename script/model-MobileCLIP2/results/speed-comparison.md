# v1 / v2 主机速度对比

CPU：13th Gen Intel(R) Core(TM) i9-13900K；4 线程，batch=1。
每轮先预热 10 次，再记录 100 次，共 3 轮；模型顺序按固定种子打乱，同一时间只跑一个模型塔。

以下均为纯推理毫秒数，包含运行库输入输出拷贝，不含读图、缩放、分词、数据库和相似度搜索。并非手机端速度。

| 模型 | 图像中位数 / P95 ms | 文本中位数 / P95 ms |
|---|---:|---:|
| MobileCLIP-S0 v1 ONNX / FP32 image + INT8 text | 12.57 / 14.28 | 3.42 / 5.25 |
| MobileCLIP-S2 v1 ONNX / FP32 image + INT8 text | 35.73 / 43.33 | 6.16 / 7.92 |
| MobileCLIP2-S0 ONNX / FP32 image + INT8 text | 12.75 / 15.52 | 6.49 / 7.88 |
| MobileCLIP2-S0 TFLite / FP32 image + dynamic INT8 text | 19.37 / 20.73 | 6.40 / 6.84 |
| Historical app CLIP ORT / INT8 image + INT8 text / stretch224 | 10.84 / 13.11 | 8.33 / 11.48 |

主机运行库与 Android 运行库、CPU 指令集不同，不能把这些比例直接套用到真机。
Android x86_64 模拟器的既有少量样本记录另见 [ONNX](emulator-onnx.json)、[TFLite](emulator-tflite.json)；本测试不包含 Android 测量。

[原始耗时、模型哈希和运行环境](speed-comparison.json)。
