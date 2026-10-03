#!/usr/bin/env bash

set -euo pipefail

version="${1:?Usage: ci/build-android.sh <version> <build-number>}"
build_number="${2:?Usage: ci/build-android.sh <version> <build-number>}"
build_name="${BUILD_NAME:?BUILD_NAME must be set}"

flutter build apk --release \
  --target-platform=android-arm64 \
  --build-name="$build_name" \
  --build-number="$build_number"

flutter build appbundle --release \
  --build-name="$build_name" \
  --build-number="$build_number"

mv build/app/outputs/flutter-apk/app-release.apk \
  "PicQuery-$version-android-arm64.apk"
mv build/app/outputs/bundle/release/app-release.aab \
  "PicQuery-$version-android.aab"
