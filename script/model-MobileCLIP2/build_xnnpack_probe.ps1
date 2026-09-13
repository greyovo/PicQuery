param(
    [Parameter(Mandatory = $true)][string]$NdkDirectory,
    [string]$OutputDirectory = 'build/mobileclip-tflite-diagnostics'
)

$ErrorActionPreference = 'Stop'
$taskCatalog = Join-Path $PSScriptRoot '../../gradle/libs.versions.toml'
if ((Get-Content -LiteralPath $taskCatalog -Raw) -notmatch '(?m)^litert\s*=\s*"1\.4\.1"\s*$') {
    throw 'This historical probe requires LiteRT 1.4.1. Use MobileCLIP2InstrumentedTest for the current runtime.'
}
# Diagnostic ARM64 shim only: uses the existing LiteRT 1.4.1 library in the APK.
$taskHeaders = @(
    @('tflite/delegates/xnnpack/xnnpack_delegate.h', 'https://raw.githubusercontent.com/google-ai-edge/LiteRT/v1.4.1/tflite/delegates/xnnpack/xnnpack_delegate.h', 'D01B38BC1AE87422C9FE96F5F0DAEE0EF284272A6020F69AAB75A0B1CAB07AED'),
    @('tflite/core/c/common.h', 'https://raw.githubusercontent.com/google-ai-edge/LiteRT/v1.4.1/tflite/core/c/common.h', 'A470828B9553280E25043D2F115305AA99AA3D6EC76E7C5103CFC2972A38217A'),
    @('tflite/core/c/c_api_types.h', 'https://raw.githubusercontent.com/google-ai-edge/LiteRT/v1.4.1/tflite/core/c/c_api_types.h', '5C61F7E658E5E181AADA8999BF8482FD1D0A37E6067A796CCEDE00A2FD4AC8FE'),
    @('tensorflow/compiler/mlir/lite/core/c/tflite_types.h', 'https://raw.githubusercontent.com/tensorflow/tensorflow/v2.20.0/tensorflow/compiler/mlir/lite/core/c/tflite_types.h', '11B64F1EC5E013E39953FFDAE21A6CC139BA1CA500A302EBFFF76DF2681EA4B7')
)
$taskInclude = Join-Path $OutputDirectory 'include'
foreach ($taskHeader in $taskHeaders) {
    $taskPath = Join-Path $taskInclude $taskHeader[0]
    New-Item -ItemType Directory -Force -Path (Split-Path $taskPath) | Out-Null
    if (-not (Test-Path -LiteralPath $taskPath)) {
        Invoke-WebRequest -Uri $taskHeader[1] -OutFile $taskPath
    }
    if ((Get-FileHash -LiteralPath $taskPath -Algorithm SHA256).Hash -ne $taskHeader[2]) {
        throw "Pinned header checksum mismatch: $taskPath"
    }
}
$taskCompiler = Join-Path $NdkDirectory 'toolchains/llvm/prebuilt/windows-x86_64/bin/clang++.exe'
if (-not (Test-Path -LiteralPath $taskCompiler)) { throw 'An installed Windows Android NDK is required.' }
$taskLibrary = Join-Path $OutputDirectory 'libpicquery_xnnpack_probe.so'
& $taskCompiler --target=aarch64-linux-android29 -std=c++17 -fPIC -shared -O2 -Wall -Wextra -Werror -static-libstdc++ '-Wl,-z,max-page-size=16384' -I $taskInclude app/src/androidTest/cpp/xnnpack_thread_probe.cpp -o $taskLibrary -ldl
if ($LASTEXITCODE -ne 0) { throw 'Native probe compilation failed.' }
ConvertTo-Json -InputObject @(@{source = $taskLibrary; destination = 'libpicquery_xnnpack_probe.so'}) |
    Set-Content -LiteralPath (Join-Path $OutputDirectory 'probe-upload.json') -Encoding utf8NoBOM
Write-Output "Built diagnostic probe: $taskLibrary"
