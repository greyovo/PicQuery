#!/usr/bin/env bash

set -euo pipefail

platform="${1:?Usage: ci/test_integration.sh <macos|android> [device]}"
device="${2:-$platform}"
case "$platform" in
  macos|android) ;;
  *) echo "Unsupported integration test platform: $platform" >&2; exit 1 ;;
esac

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$project_root"

# Run each suite in a fresh application process. Shared native sessions and
# IntegrationTestWidgetsFlutterBinding are not reused between test files.
for suite in integration_test/*_test.dart; do
  case "$(basename "$suite")" in
    android_*) [[ "$platform" == android ]] || continue ;;
    model_inference_test.dart) ;;
    *) [[ "$platform" == macos ]] || continue ;;
  esac
  echo "Running $suite on $device"
  flutter test "$suite" --device-id "$device" --timeout 15m
done
