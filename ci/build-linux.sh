#!/usr/bin/env bash

set -euo pipefail

version="${1:?Usage: ci/build-linux.sh <version> <build-number>}"
build_number="${2:?Usage: ci/build-linux.sh <version> <build-number>}"
build_name="${BUILD_NAME:?BUILD_NAME must be set}"

flutter build linux --release \
  --build-name="$build_name" \
  --build-number="$build_number"

tar -C build/linux/x64/release -czf \
  "PicQuery-$version-linux-x64.tar.gz" bundle
