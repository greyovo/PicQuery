#!/usr/bin/env bash

set -euo pipefail

platform="${1:?Usage: ci/test_integration.sh <macos|windows|linux|android> [device] [correctness|benchmarks|all]}"
device="${2:-$platform}"
mode="${3:-correctness}"
case "$mode" in
  correctness|benchmarks|all) ;;
  *) echo "Unsupported integration test mode: $mode" >&2; exit 1 ;;
esac
case "$platform" in
  macos|windows|linux|android) ;;
  *) echo "Unsupported integration test platform: $platform" >&2; exit 1 ;;
esac

# Git Bash may expose only FLUTTER_ROOT rather than Flutter on PATH.
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

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$project_root"

# Run each suite in a fresh application process. Shared native sessions and
# IntegrationTestWidgetsFlutterBinding are not reused between test files.
for suite in integration_test/*_test.dart; do
  case "$(basename "$suite")" in
    *_benchmark_test.dart) [[ "$mode" != correctness ]] || continue ;;
    *) [[ "$mode" != benchmarks ]] || continue ;;
  esac
  case "$(basename "$suite")" in
    android_*) [[ "$platform" == android ]] || continue ;;
    model_inference_test.dart) ;;
    *) [[ "$platform" != android ]] || continue ;;
  esac
  echo "Running $suite on $device"
  "${flutter_command[@]}" test "$suite" --device-id "$device" --timeout 15m
done
