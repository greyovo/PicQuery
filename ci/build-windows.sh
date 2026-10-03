#!/usr/bin/env bash

set -euo pipefail

version="${1:?Usage: ci/build-windows.sh <version> <build-number>}"
build_number="${2:?Usage: ci/build-windows.sh <version> <build-number>}"
build_name="${BUILD_NAME:?BUILD_NAME must be set}"

flutter build windows --release \
  --build-name="$build_name" \
  --build-number="$build_number"

powershell.exe -NoProfile -Command \
  "Compress-Archive -Path 'build/windows/x64/runner/Release/*' -DestinationPath 'PicQuery-$version-windows-x64.zip'"
