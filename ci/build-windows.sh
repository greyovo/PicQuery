#!/usr/bin/env bash

set -euo pipefail

version="${1:?Usage: ci/build-windows.sh <version> <build-number>}"
build_number="${2:?Usage: ci/build-windows.sh <version> <build-number>}"
build_name="${BUILD_NAME:?BUILD_NAME must be set}"

flutter build windows --release \
  --build-name="$build_name" \
  --build-number="$build_number"

PICQUERY_VERSION="$version" PICQUERY_FILE_VERSION="$build_name" powershell.exe -NoProfile -Command '
  $iscc = (Get-Command ISCC.exe -ErrorAction SilentlyContinue).Source
  if (-not $iscc) {
    $iscc = Join-Path ${env:ProgramFiles(x86)} "Inno Setup 6\ISCC.exe"
  }
  if (-not (Test-Path $iscc)) {
    throw "Inno Setup 6 was not found. Install it from https://jrsoftware.org/isinfo.php"
  }

  & $iscc "/DAppVersion=$env:PICQUERY_VERSION" "/DAppFileVersion=$env:PICQUERY_FILE_VERSION" "windows\installer\picquery.iss"
  if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
  }
'
