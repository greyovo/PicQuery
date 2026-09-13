# MobileCLIP2-S0 host comparison

image FP32 / text dynamic INT8 weights, same as previous app

Host CPU, inference only, per sample; excludes preprocessing and tokenization. Not Android latency.
Small conversion/ranking smoke test; not an accuracy benchmark.

CPU threads: 4; warm-up: 3; repeats per sample: 5.

| Model | MiB | Median ms | P95 ms |
|---|---:|---:|---:|
| onnx_image | 43.45 | 13.53 | 18.78 |
| tflite_image | 43.50 | 20.39 | 21.37 |
| onnx_text_int8 | 61.44 | 7.57 | 9.36 |
| tflite_text_int8 | 62.23 | 7.60 | 10.82 |
| onnx_text_reference | 242.16 | 18.63 | 27.93 |
| tflite_text_reference | 242.15 | 19.45 | 25.56 |

| Embedding comparison | Minimum cosine | Maximum absolute error |
|---|---:|---:|
| image_onnx_vs_tflite | 1.00000000 | 0.00000051 |
| text_int8_onnx_vs_tflite | 0.92823893 | 0.06287685 |
| text_onnx_int8_vs_fp32 | 0.92781937 | 0.07000303 |
| text_tflite_int8_vs_fp32 | 0.99923670 | 0.00877221 |
| text_fp32_onnx_vs_tflite | 0.99999994 | 0.00000066 |

Image-to-text top-1 agreement: 100%; top-3 overlap: 93%.
Text-to-image top-1 agreement: 92%; top-3 overlap: 95%.

Input images: dog1.jpg, dog2.jpg, astronaut.jpg, leaning_tower.jpg, pottery.jpg.
