#!/usr/bin/env bash
set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
raw_version="${VERSION_INPUT:-$(sed -n 's/^version: *\([^+]*\).*/\1/p' "$project_root/pubspec.yaml")}"
version="${raw_version#v}"
if [[ ! "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+([+-][0-9A-Za-z.-]+)?$ ]]; then
  echo "Invalid version: $raw_version" >&2
  exit 1
fi
build_number="${BUILD_NUMBER_INPUT:-$(TZ=Asia/Shanghai date +%y%m%d)}"
if [[ ! "$build_number" =~ ^[0-9]+$ ]]; then
  echo "Build number must contain digits only: $build_number" >&2
  exit 1
fi
printf 'version=%s\nbuild_number=%s\nbuild_date=%s\n' \
  "$version" "$build_number" "$(TZ=Asia/Shanghai date '+%Y-%m-%dT%H:%M:%S+08:00')"
