# Native runtime upgrade audit — 2026-09-13

Read-only inspection of official AARs and the existing pre-upgrade debug APKs.
No Gradle build, compiler invocation, or device action was performed by this audit.

| Runtime artifact | Prior → selected | Packaged native ABIs | LOAD alignment |
|---|---|---|---|
| ONNX Runtime Android | 1.23.2 → 1.29.0 | arm64-v8a, armeabi-v7a, x86, x86_64 | All segments 16 KB in both versions |
| LiteRT | 1.4.1 → 1.4.2 | Same four | All segments 16 KB in both versions |
| LiteRT GPU | 1.4.1 → 1.4.2 | Same four | All segments 16 KB in both versions |
| ObjectBox | 5.1.0 → 5.4.2 | Same four | 64-bit 16 KB; 32-bit 4 KB in both versions |
| DataStore core Android | 1.2.0 → 1.2.1 | Same four | All segments 16 KB in both versions |

The selected AARs retain the app's default armv7 and arm64 targets. There is no
new ELF alignment regression among these artifacts. This does not establish
16 KB device compatibility or verify the final APK's ZIP alignment.

## ONNX API and packaging

The official
[1.29.0 AAR](https://repo.maven.apache.org/maven2/com/microsoft/onnxruntime/onnxruntime-android/1.29.0/onnxruntime-android-1.29.0.aar)
contains both `libonnxruntime.so` and `libonnxruntime4j_jni.so` for all four ABIs.
`javap -public` compared nine classes used by the app against 1.23.2:
`OrtEnvironment`, `OrtSession`, `OrtSession.SessionOptions`, `OrtSession.Result`,
`OnnxTensor`, `TensorInfo`, `OnnxJavaType`, `NodeInfo`, and `OnnxValue`.
The only removed member found is `SessionOptions.addArmNN(boolean)`, which has
no call site in `app/src`. The app's session creation, thread setters, config
entries, metadata, run/result/close, and FloatBuffer/IntBuffer/LongBuffer tensor
APIs are retained. This is static API evidence; model execution remains a
separate integration check.

## ObjectBox packaging change

In 5.4.2, `objectbox-android` and `objectbox-android-objectbrowser` no longer
contain native libraries themselves. Their official POM and Gradle metadata
declare runtime dependencies on `objectbox-android-db:5.4.2` and
`objectbox-android-db-admin:5.4.2`, respectively. Both transitive AARs were
downloaded and inspected. They contain `libobjectbox-jni.so` for all four ABIs.
Their 32-bit LOAD alignment remains 4 KB, as in the prior 5.1.0 AAR.

## Existing APK baseline

The existing debug APKs have timestamps 2026-09-13 07:10:10/11 UTC. Their
ObjectBox native hashes exactly match the old 5.1.0 AAR, so they are recorded
as the pre-upgrade baseline, not as the newly built APKs. Both contain two
non-16-KB-aligned libraries, on armeabi-v7a only:

- `libobjectbox-jni.so`: 4 KB, as confirmed above.
- `libtranslate_jni.so`: 4 KB; the ML Kit translate dependency is unchanged.

All inspected arm64 libraries in those baseline APKs have 16 KB ELF alignment.
The final upgraded APK still needs its own packaging and device verification.

## Native XNNPACK guard review

The pinned official header has natural, unpacked layout. The bridge assertions
correctly require a 36-byte struct with 4-byte alignment on armv7/x86 and a
64-byte struct with 8-byte alignment on arm64/x86_64. Member offsets are checked
individually. In the actual 1.4.2 armeabi-v7a binary, OptionsDefault passes
`0x24` (36) to its memory-zero routine, sets `flags` at `0x8`, and the descriptor
at `0x18` (24). The x86 binary clears the first 32 bytes and the final 4 bytes.
The 64-bit binaries clear 64 bytes and set the descriptor at offset 40. These
agree with the vendored header and static assertions. No compilation or runtime
test is implied by this read-only review.

## Evidence files

- `native-abi-alignment.json`: coordinates, official URLs, AAR and library SHA256,
  every LOAD segment's offset/address/alignment, and 16 KB alignment result.
- `objectbox-transitive-abi-alignment.json`: split ObjectBox JNI artifacts.
- `existing-apk-abi-alignment.json`: baseline APK paths/times, native hashes and
  exact artifact matches.
- `onnx-java-api-diff.json`, `onnx-java-*.txt`: full public API evidence.
- `*.readelf.txt`: NDK llvm-readelf cross-checks for representative ELF files.
- `objectbox-*.pom` and `objectbox-*.module`: official transitive dependency proof.

The ELF check follows the
[Android page-size guidance](https://developer.android.com/guide/practices/page-sizes#elf-alignment):
all LOAD alignments must be at least 16 KB; this audit also checks file offset
and virtual address congruence modulo 16 KB.
