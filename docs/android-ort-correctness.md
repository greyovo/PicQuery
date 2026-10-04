# Android MobileCLIP non-finite embeddings

Reproduced on 2026-10-04 on Xiaomi 2206123SC (mayfly, ARM64), using the
bundled MobileCLIP2 S0 visual ONNX and ONNX Runtime Android 1.23.0.
The visual graph has FP32 input/output and 228 FP16 initializers.

## Isolation

A deterministic RGB CHW input `(index % 256) / 255` was sent through the
Flutter method channel. Reading the native input tensor back matched every
FP32 element exactly. With CPU and default graph optimization, inference
returned 512 NaNs. Keeping the same model, input, runtime and provider:

| Optimization | Output |
| --- | --- |
| Default | 512 NaNs |
| Disabled | 512 finite values |
| Basic | 512 finite values |

Both ordinary `OrtValue.fromList` and direct-buffer reusable input returned
identical embeddings within each safe configuration. This isolates the
failure to ORT's advanced graph optimization/execution path for this FP16
graph on the tested Android device. It is not an empty output, preprocessing
failure, or method-channel corruption. The exact offending transformer/kernel
has not been isolated; this does not establish that all Android devices or
all ORT versions are affected.

## Fix

The local plugin exposes `OrtGraphOptimizationLevel` for Android, preserving
ORT defaults when omitted. The engine requests `basic` for Android CLIP
sessions, retaining basic rewrites while avoiding the failing advanced path.
Translation and other platforms retain their previous options. The existing
finite/nonzero embedding checks remain in place.

Run the regression on a connected Android device:

```sh
fvm flutter test integration_test/android_ort_correctness_test.dart -d DEVICE_ID
```

It checks exact input round trips, reusable-buffer updates, finite 512-element
outputs, repeated engine image encoding and unit-normalized text/image
embeddings. The inference benchmark now rejects non-finite outputs instead of
reporting timings for invalid inference.

Reference: [ORT graph optimization levels](https://onnxruntime.ai/docs/performance/model-optimizations/graph-optimizations.html).

Final validation: the regression above passed on the connected Xiaomi device,
including the engine's BASIC option forwarded through the plugin. Static
analysis passed for the app, plugin Dart code and new tests; all 28 unit tests
passed. No runtime upgrade or model asset replacement was required.

## NNAPI comparison

On the same Xiaomi device (Android 14, Snapdragon SM8475), using BASIC
optimization and the unchanged bundled visual model, requesting
`[NNAPI, CPU]` produced finite embeddings exactly matching CPU for five
synthetic input variants. Each configuration had five warmups and twenty
timed calls, with reusable input storage. Timing covers `session.run`,
including its platform-channel round trip, and excludes preprocessing, input
updates and output extraction. Debug APK, default ORT thread count.

Two reverse-order rounds:

| Round | Provider order | Median ms | P95 ms |
| --- | --- | ---: | ---: |
| 1 | CPU | 93.527 | 103.527 |
| 1 | NNAPI, CPU | 94.401 | 101.543 |
| 2 | NNAPI, CPU | 92.411 | 97.371 |
| 2 | CPU | 93.762 | 100.119 |

ORT session diagnostics explicitly reported **0 NNAPI partitions and 0
supported nodes** (224-node original graph and 472-node rewritten graph).
Thus this is CPU fallback, not hardware NNAPI acceleration; the small timing
difference is not evidence of a speedup. Keep CPU as the app preference for
the current model/runtime. Earlier NaNs with NNAPI requested do not establish
an NNAPI kernel bug, because unsupported nodes execute on ORT CPU.

ORT 1.23's NNAPI base operator checker accepts FLOAT (FP32) by default, not
FLOAT16. A suitable FP32 export or supported quantized graph would need a new
partitioning, accuracy and latency evaluation before adopting NNAPI.
[Source](https://github.com/microsoft/onnxruntime/blob/v1.23.0/onnxruntime/core/providers/nnapi/nnapi_builtin/builders/impl/base_op_builder.cc).

Reproduce timing and correctness with
`fvm flutter test integration_test/android_nnapi_benchmark_test.dart -d DEVICE_ID`.
