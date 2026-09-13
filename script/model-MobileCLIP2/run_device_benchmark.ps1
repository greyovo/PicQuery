param(
    [Parameter(Mandatory = $true)][string]$DeviceSerial,
    [string]$AdbPath = 'adb',
    [string]$Package = 'me.grey.picquery.mobileclip2.onnx',
    [string]$OutputDirectory = 'build/mobileclip-device-results',
    [int]$SampleCount = 100,
    [int]$WarmupCount = 10,
    [int]$SustainSeconds = 60,
    [int]$CooldownSeconds = 30,
    [int]$RoundCount = 2,
    [string[]]$Models = @('v2_s0_onnx_int8', 'v2_s0_tflite_int8', 'v1_s0_onnx_int8', 'v1_s2_onnx_int8', 'legacy_clip_int8')
)

$ErrorActionPreference = 'Stop'
if ($RoundCount -lt 1 -or $SampleCount -lt 1 -or $WarmupCount -lt 0 -or $SustainSeconds -lt 0 -or $CooldownSeconds -lt 0) {
    throw 'Invalid benchmark counts or durations.'
}
if ($Models.Where({ $_ -notmatch '^[a-zA-Z0-9_-]+$' }).Count -gt 0) { throw 'Invalid model ID.' }

function Invoke-Device {
    param([string[]]$Arguments)
    $taskOutput = & $AdbPath -s $DeviceSerial @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) { throw "ADB failed: $($Arguments -join ' ')`n$($taskOutput -join "`n")" }
    return ($taskOutput -join "`n")
}

function Save-Json {
    param([string]$Path, $Value)
    $Value | ConvertTo-Json -Depth 60 | Set-Content -LiteralPath $Path -Encoding utf8
}

New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
$taskState = Invoke-Device -Arguments @('get-state')
if ($taskState.Trim() -ne 'device') { throw 'Selected Android device is not ready.' }
$taskMetadata = [ordered]@{
    device_serial = $DeviceSerial
    model = (Invoke-Device -Arguments @('shell', 'getprop', 'ro.product.model')).Trim()
    soc = (Invoke-Device -Arguments @('shell', 'getprop', 'ro.soc.model')).Trim()
    android = (Invoke-Device -Arguments @('shell', 'getprop', 'ro.build.version.release')).Trim()
    fingerprint = (Invoke-Device -Arguments @('shell', 'getprop', 'ro.build.fingerprint')).Trim()
    page_size = (Invoke-Device -Arguments @('shell', 'getconf', 'PAGESIZE')).Trim()
    package = $Package
    started_utc = [DateTime]::UtcNow.ToString('o')
    sample_count = $SampleCount
    warmup_count = $WarmupCount
    sustain_seconds_first_round = $SustainSeconds
    cooldown_seconds = $CooldownSeconds
    rounds = $RoundCount
    note = 'Fresh app process per model; fixed forward/reverse order. USB charging and screen state are not changed. PSS snapshots are not peak memory. No album indexing or personal media access.'
    runs = @()
}
Save-Json -Path (Join-Path $OutputDirectory 'run-metadata.json') -Value $taskMetadata

$taskRunIndex = 0
for ($taskRound = 1; $taskRound -le $RoundCount; $taskRound++) {
    $taskOrder = @($Models)
    if ($taskRound % 2 -eq 0) { [array]::Reverse($taskOrder) }
    foreach ($taskModel in $taskOrder) {
        if ($taskRunIndex -gt 0 -and $CooldownSeconds -gt 0) {
            Write-Output "Cooling for $CooldownSeconds seconds before $taskModel / round $taskRound"
            $taskRemaining = $CooldownSeconds
            while ($taskRemaining -gt 0) {
                $taskWait = [Math]::Min(30, $taskRemaining)
                Start-Sleep -Seconds $taskWait
                $taskRemaining -= $taskWait
            }
        }
        Invoke-Device -Arguments @('shell', 'am', 'force-stop', $Package) | Out-Null
        $taskDuration = if ($taskRound -eq 1) { $SustainSeconds } else { 0 }
        $taskPrefix = "$taskModel-r$taskRound"
        $taskBefore = [ordered]@{
            utc = [DateTime]::UtcNow.ToString('o')
            battery = Invoke-Device -Arguments @('shell', 'dumpsys', 'battery')
            thermal = Invoke-Device -Arguments @('shell', 'dumpsys', 'thermalservice')
        }
        Write-Output "START $taskPrefix, sustain ${taskDuration}s"
        $taskTimer = [Diagnostics.Stopwatch]::StartNew()
        $taskLog = Invoke-Device -Arguments @(
            'shell', 'am', 'instrument', '-w', '-r',
            '-e', 'class', 'me.grey.picquery.feature.MobileCLIPVersionsBenchmarkTest#benchmarkModel',
            '-e', 'modelId', $taskModel, '-e', 'roundId', "r$taskRound",
            '-e', 'sampleCount', "$SampleCount", '-e', 'warmupCount', "$WarmupCount",
            '-e', 'sustainSeconds', "$taskDuration",
            "$Package.test/androidx.test.runner.AndroidJUnitRunner"
        )
        $taskTimer.Stop()
        $taskLog | Set-Content -LiteralPath (Join-Path $OutputDirectory "$taskPrefix-instrumentation.log") -Encoding utf8
        $taskRaw = Invoke-Device -Arguments @('exec-out', 'run-as', $Package, 'cat', "files/mobileclip-benchmark-$taskModel-r$taskRound.json")
        $taskReport = $taskRaw | ConvertFrom-Json
        $taskRaw | Set-Content -LiteralPath (Join-Path $OutputDirectory "$taskPrefix.json") -Encoding utf8
        $taskAfter = [ordered]@{
            utc = [DateTime]::UtcNow.ToString('o')
            battery = Invoke-Device -Arguments @('shell', 'dumpsys', 'battery')
            thermal = Invoke-Device -Arguments @('shell', 'dumpsys', 'thermalservice')
        }
        Save-Json -Path (Join-Path $OutputDirectory "$taskPrefix-environment.json") -Value ([ordered]@{
            before = $taskBefore; after = $taskAfter; elapsed_wall_seconds = $taskTimer.Elapsed.TotalSeconds
        })
        if ($taskReport.status -ne 'passed' -or $taskReport.model_id -ne $taskModel -or $taskReport.round_id -ne "r$taskRound" -or $taskLog -notmatch 'OK \(1 test\)') {
            throw "Instrumentation failed for $taskPrefix; inspect saved report and log."
        }
        $taskMetadata.runs += [ordered]@{ model = $taskModel; round = $taskRound; report = "$taskPrefix.json"; started_utc = $taskBefore.utc; finished_utc = $taskAfter.utc }
        Save-Json -Path (Join-Path $OutputDirectory 'run-metadata.json') -Value $taskMetadata
        Write-Output ("PASS {0}: image P50 {1:N2} ms, text P50 {2:N2} ms" -f $taskPrefix, $taskReport.image.summary.p50_ms, $taskReport.text.summary.p50_ms)
        Invoke-Device -Arguments @('shell', 'am', 'force-stop', $Package) | Out-Null
        $taskRunIndex++
    }
}
$taskMetadata.completed_utc = [DateTime]::UtcNow.ToString('o')
$taskMetadata.status = 'complete'
Save-Json -Path (Join-Path $OutputDirectory 'run-metadata.json') -Value $taskMetadata
Write-Output "COMPLETE: $taskRunIndex model runs saved to $OutputDirectory"
