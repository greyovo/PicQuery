# LiteRT 1.4.2 的 XNNPACK C API 头文件

[English](README.md) | 中文

这些未经修改、使用 Apache-2.0 许可的头文件固定用于
`com.google.ai.edge.litert:litert:1.4.2`。PicQuery 打开现有
`libtensorflowlite_jni.so`，取得官方默认选项后仅修改 `num_threads`。
该库保持加载直至进程退出，不编译或分发第二份 LiteRT 或 XNNPACK 实现。

## 来源

三个依赖的 C 头文件直接来自官方
[LiteRT 1.4.2 AAR](https://dl.google.com/dl/android/maven2/com/google/ai/edge/litert/litert/1.4.2/litert-1.4.2.aar)
的 `headers/` 目录。AAR 的 SHA256 为
`9b02cdf9db0a698b341b59e75c54ab8221d6304577cc43830d01024caf1fca74`。
该 AAR 不包含 `xnnpack_delegate.h`，此文件取自官方
[commit d029b6bac0103d60de60b4fe4a7b72875c8a9c06](https://github.com/google-ai-edge/LiteRT/commit/d029b6bac0103d60de60b4fe4a7b72875c8a9c06)，
其中新增了末尾字段 `weight_cache_lock_memory`。
该头文件的结构体布局已按下文方法与 1.4.2 二进制核对。
这是经过验证的头文件来源，**不表示 Maven 产物由此 commit 构建**。
验证时未找到官方 `v1.4.2` Git tag 或 GitHub release。

| `include/` 下的头文件 | 来源 | SHA256 |
|---|---|---|
| `tflite/delegates/xnnpack/xnnpack_delegate.h` | [官方固定 commit](https://github.com/google-ai-edge/LiteRT/blob/d029b6bac0103d60de60b4fe4a7b72875c8a9c06/tflite/delegates/xnnpack/xnnpack_delegate.h) | `f8d46497a2f502642e0b4095296c82f328ec92831846d782f3869ad7ab190ee3` |
| `tflite/core/c/common.h` | 1.4.2 AAR | `02971b2fd9ac651bee055a803751c744c89eb86095d5fac13fb1b64666a6931b` |
| `tflite/core/c/c_api_types.h` | 1.4.2 AAR | `4fa7782fefa01473f45ff19316640c0e6d8334dcca630eaebcf358790502f4c6` |
| `tflite/converter/core/c/tflite_types.h` | 1.4.2 AAR | `76837feb95e43f7585ecb5980df7895bccd53491d642d7b066d7595e20aa798b` |

## ABI 检查

2026-09-13，使用 `llvm-readelf --dyn-syms` 确认 1.4.2 AAR 的
**armeabi-v7a、arm64-v8a、x86、x86_64** 四种 ABI 均导出
`TfLiteXNNPackDelegateOptionsDefault`、`TfLiteXNNPackDelegateCreate`、
`TfLiteXNNPackDelegateDelete` 和 `TfLiteXNNPackDelegateGetThreadPool`。

`OptionsDefault` 按值返回选项结构体，因此其大小必须与原生二进制匹配。
1.4.2 函数的反汇编显示，arm64-v8a/x86_64 初始化 64 字节，
armeabi-v7a/x86 初始化 36 字节；默认 flags 写入偏移 8，
文件描述符分别写入偏移 40/24。官方头文件产生以下布局，
`xnnpack_delegate.cpp` 对每个编译 ABI 使用 `static_assert` 强制核验：

| 大小 / 成员偏移（字节） | 64 位 | 32 位 |
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

旧 v1.4.1 头文件描述的大小仅为 56/32 字节。将它与 1.4.2 的默认选项函数
一起使用可能覆盖调用者的栈。不要在升级后的运行库中复用旧头文件编译的探针 `.so`。
编译期布局检查保护此固定源码，但不能检测独立替换的运行库二进制。
今后每次 LiteRT 升级都需要重新进行符号、布局、构建和设备数值一致性检查。

LiteRT 2.2.0 需要独立迁移：其 Java API 移除了 `Delegate` 和
`Interpreter.Options.addDelegate`，且 JNI 库名称发生变化。
在保留当前自定义 delegate 和 GPU API 路径的情况下，不能直接替换 1.4.2。

保留版权和许可声明。Android Gradle 使用 NDK 29、CMake 3.22.1、静态 libc++
和 16 KB ELF 对齐构建此桥接库，构建时无需联网下载这些头文件。
升级调查的符号列表与反汇编产物位于 `build/dependency-upgrade/`；
设备验证与这些静态检查分别进行。
