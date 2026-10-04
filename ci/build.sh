#!/usr/bin/env bash

set -euo pipefail

platform="${1:?Usage: ci/build.sh <platform> <version> <versionCode>}"
raw_version="${2:?Usage: ci/build.sh <platform> <version> <versionCode>}"
version_code="${3:?Usage: ci/build.sh <platform> <version> <versionCode>}"
version="${raw_version#v}"
build_date="${BUILD_DATE:-$(TZ=Asia/Shanghai date '+%Y-%m-%dT%H:%M:%S+08:00')}"

if [[ ! "$platform" =~ ^(android|linux|macos|windows)$ ]]; then
  echo "Unsupported platform: $platform (expected android, linux, macos, or windows)" >&2
  exit 1
fi
if [[ ! "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+([+-][0-9A-Za-z.-]+)?$ ]]; then
  echo "Version must look like 1.2.3 or 1.2.3-beta.1: $raw_version" >&2
  exit 1
fi
if [[ ! "$version_code" =~ ^[0-9]+$ ]]; then
  echo "versionCode must contain digits only: $version_code" >&2
  exit 1
fi
if [[ ! "$build_date" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}\+08:00$ ]]; then
  echo "BUILD_DATE must use YYYY-MM-DDTHH:MM:SS+08:00 format: $build_date" >&2
  exit 1
fi

build_name="$(sed -E 's/^([0-9]+\.[0-9]+\.[0-9]+).*/\1/' <<< "$version")"
flutter_args=(
  --release
  "--build-name=$build_name"
  "--build-number=$version_code"
  "--dart-define=BUILD_DATE=$build_date"
)

if command -v flutter >/dev/null 2>&1; then
  flutter_command=(flutter)
elif command -v fvm >/dev/null 2>&1; then
  flutter_command=(fvm flutter)
elif [[ "$platform" == "windows" && -n "${FLUTTER_ROOT:-}" ]]; then
  flutter_root="$FLUTTER_ROOT"
  if command -v cygpath >/dev/null 2>&1; then
    flutter_root="$(cygpath -u "$flutter_root")"
  fi
  flutter_command=("$flutter_root/bin/flutter.bat")
else
  echo "Flutter was not found. Install Flutter or FVM, or set FLUTTER_ROOT on Windows." >&2
  exit 127
fi

case "$platform" in
  android)
    "${flutter_command[@]}" build apk "${flutter_args[@]}" --target-platform=android-arm64
    "${flutter_command[@]}" build appbundle "${flutter_args[@]}"
    mv build/app/outputs/flutter-apk/app-release.apk \
      "PicQuery-$version-android-arm64.apk"
    mv build/app/outputs/bundle/release/app-release.aab \
      "PicQuery-$version-android.aab"
    ;;
  linux)
    "${flutter_command[@]}" build linux "${flutter_args[@]}"
    tar -C build/linux/x64/release -czf \
      "PicQuery-$version-linux-x64.tar.gz" bundle
    ;;
  macos)
    "${flutter_command[@]}" build macos "${flutter_args[@]}"

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
    ;;
  windows)
    "${flutter_command[@]}" build windows "${flutter_args[@]}"
    PICQUERY_VERSION="$version" PICQUERY_FILE_VERSION="$build_name" \
      powershell.exe -NoProfile -Command '
        $languageFile = Join-Path $PWD "windows\installer\ChineseSimplified.isl"
        Invoke-WebRequest `
          -UseBasicParsing `
          -Uri "https://raw.githubusercontent.com/jrsoftware/issrc/28eab5faa5d08478a2447cec3d42c3dd806c8274/Files/Languages/ChineseSimplified.isl" `
          -OutFile $languageFile

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
    ;;
esac
