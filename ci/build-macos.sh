#!/usr/bin/env bash

set -euo pipefail

version="${1:?Usage: ci/build-macos.sh <version> <build-number>}"
build_number="${2:?Usage: ci/build-macos.sh <version> <build-number>}"
build_name="${BUILD_NAME:?BUILD_NAME must be set}"

flutter build macos --release \
  --build-name="$build_name" \
  --build-number="$build_number"

app_path="$(find build/macos/Build/Products/Release -maxdepth 1 -name '*.app' -print -quit)"
if [[ -z "$app_path" ]]; then
  echo "No macOS .app bundle was produced" >&2
  exit 1
fi

dmg_staging_dir="$(mktemp -d "${TMPDIR:-/tmp}/picquery-dmg.XXXXXX")"
trap 'rm -rf "$dmg_staging_dir"' EXIT

ditto "$app_path" "$dmg_staging_dir/$(basename "$app_path")"
ln -s /Applications "$dmg_staging_dir/Applications"

hdiutil create \
  -volname "PicQuery" \
  -srcfolder "$dmg_staging_dir" \
  -ov \
  -format UDZO \
  "PicQuery-$version-macos.dmg"
