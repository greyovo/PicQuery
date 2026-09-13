# XNNPACK C API headers for LiteRT 1.4.2

English | [中文](README_zh.md)

These unmodified Apache-2.0 headers are pinned for
`com.google.ai.edge.litert:litert:1.4.2`. PicQuery opens its existing
`libtensorflowlite_jni.so`, obtains the official default options, and changes only
`num_threads`. The library remains loaded until the process exits. No second
LiteRT or XNNPACK implementation is compiled or shipped.

## Provenance

The three dependent C headers come directly from `headers/` in the official
[LiteRT 1.4.2 AAR](https://dl.google.com/dl/android/maven2/com/google/ai/edge/litert/litert/1.4.2/litert-1.4.2.aar).
Its SHA256 is `9b02cdf9db0a698b341b59e75c54ab8221d6304577cc43830d01024caf1fca74`.
That AAR does not include `xnnpack_delegate.h`. Its copy comes from official
[commit d029b6bac0103d60de60b4fe4a7b72875c8a9c06](https://github.com/google-ai-edge/LiteRT/commit/d029b6bac0103d60de60b4fe4a7b72875c8a9c06),
which adds the trailing `weight_cache_lock_memory` field. This header's layout
was checked against the 1.4.2 binaries as detailed below. It is a verified header
source, **not a claim that this commit built the Maven artifact**. No official
`v1.4.2` Git tag or GitHub release was available at verification time.

| Header under `include/` | Source | SHA256 |
|---|---|---|
| `tflite/delegates/xnnpack/xnnpack_delegate.h` | [Official pinned commit](https://github.com/google-ai-edge/LiteRT/blob/d029b6bac0103d60de60b4fe4a7b72875c8a9c06/tflite/delegates/xnnpack/xnnpack_delegate.h) | `f8d46497a2f502642e0b4095296c82f328ec92831846d782f3869ad7ab190ee3` |
| `tflite/core/c/common.h` | 1.4.2 AAR | `02971b2fd9ac651bee055a803751c744c89eb86095d5fac13fb1b64666a6931b` |
| `tflite/core/c/c_api_types.h` | 1.4.2 AAR | `4fa7782fefa01473f45ff19316640c0e6d8334dcca630eaebcf358790502f4c6` |
| `tflite/converter/core/c/tflite_types.h` | 1.4.2 AAR | `76837feb95e43f7585ecb5980df7895bccd53491d642d7b066d7595e20aa798b` |

## ABI checks

On 2026-09-13, `llvm-readelf --dyn-syms` confirmed exports of
`TfLiteXNNPackDelegateOptionsDefault`, `TfLiteXNNPackDelegateCreate`,
`TfLiteXNNPackDelegateDelete`, and `TfLiteXNNPackDelegateGetThreadPool` in the
1.4.2 AAR for **armeabi-v7a, arm64-v8a, x86, and x86_64**.

`OptionsDefault` returns the options structure by value, so its size must match
the native binary. Disassembly of the 1.4.2 function shows it initializes
64 bytes on arm64-v8a/x86_64 and 36 bytes on armeabi-v7a/x86. It writes default
flags at offset 8 and the file descriptor at offset 40/24, respectively. The
official header produces the following layout, enforced by `static_assert` in
`xnnpack_delegate.cpp` for each compiled ABI:

| Size / member offset in bytes | 64-bit | 32-bit |
|---|---:|---:|
| `sizeof(TfLiteXNNPackDelegateOptions)` | 64 | 36 |
| `alignof(TfLiteXNNPackDelegateOptions)` | 8 | 4 |
| `num_threads` | 0 | 0 |
| `runtime_flags` | 4 | 4 |
| `flags` | 8 | 8 |
| `weights_cache` | 16 | 12 |
| `handle_variable_ops` | 24 | 16 |
| `weight_cache_file_path` | 32 | 20 |
| `weight_cache_file_descriptor` | 40 | 24 |
| `weight_cache_provider` | 48 | 28 |
| `weight_cache_lock_memory` | 56 | 32 |

The old v1.4.1 header describes only 56/32 bytes. Using it with the 1.4.2
defaults function can overwrite the caller's stack. Do not reuse a historical
probe `.so` compiled from that header with the upgraded runtime. Compile-time
layout checks protect this pinned source; they cannot detect an independently
replaced runtime binary. Every future LiteRT upgrade requires renewed symbol,
layout, build, and device parity checks.

LiteRT 2.2.0 requires a separate migration: its Java API removes `Delegate` and
`Interpreter.Options.addDelegate`, and its JNI library names change. It cannot
replace 1.4.2 while preserving this custom delegate and existing GPU API path.

Preserve copyright/license notices. Android Gradle builds this shim using NDK 29
and CMake 3.22.1 with static libc++ and 16 KB ELF alignment. No network download
is needed for these headers at build time. Symbol listings and disassembly from
the upgrade investigation are generated under `build/dependency-upgrade/`;
device validation is separate from these static checks.
