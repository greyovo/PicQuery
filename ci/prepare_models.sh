#!/usr/bin/env bash

# Export model assets, or import a bundle already exported by CI.
set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
assets_dir="$project_root/assets/models"
models_dir="${PICQUERY_MODELS_DIR:-$project_root/../picquery-models}"

fail() {
  echo "Model preparation failed: $*" >&2
  exit 1
}

if [[ $# -gt 0 ]]; then
  [[ $# -eq 2 && "$1" == "--import" ]] || fail "Usage: ci/prepare_models.sh [--import <directory>]"
  source_dir="$2"
  [[ -d "$source_dir" ]] || fail "Model bundle does not exist: $source_dir"
else
  if [[ ! -e "$models_dir" ]]; then
    git clone https://github.com/greyovo/picquery-models.git "$models_dir"
  fi
  [[ -f "$models_dir/export.sh" ]] || fail "Missing $models_dir/export.sh; ensure picquery-models contains the exporter."
  [[ -f "$models_dir/pyproject.toml" ]] || fail "Missing $models_dir/pyproject.toml for uv environment setup."
  command -v uv >/dev/null 2>&1 || fail "Install uv (https://docs.astral.sh/uv/getting-started/installation/) and retry."
  (
    cd "$models_dir"
    uv sync --locked
    bash ./export.sh
  )
  source_dir="${PICQUERY_MODELS_OUTPUT_DIR:-$models_dir/outputs}"
  [[ -d "$source_dir" ]] || fail "Export output directory does not exist: $source_dir"
fi

# The exporter writes runtime assets directly into outputs/.
asset_names=(
  mobileclip2_s0_visual.onnx
  mobileclip2_s0_text.onnx
  mt_zho-eng.fp32.quantized.onnx
  clip-merges.txt
  clip-vocab.json
  source_tokenizer.json
  target_tokenizer.json
)
sources=()
for name in "${asset_names[@]}"; do
  source="$source_dir/$name"
  if [[ -f "$source" ]]; then
    [[ -s "$source" ]] || fail "Empty exported asset: $source"
    sources+=("$source")
  else
    # Translation and tokenizer assets remain available from the app checkout
    # when the exporter only produces the two MobileCLIP models.
    case "$name" in
      mobileclip2_s0_*.onnx) fail "Exporter did not produce $name in $source_dir" ;;
      *) [[ -s "$assets_dir/$name" ]] || fail "Missing runtime asset: $name" ;;
    esac
    sources+=("")
  fi
done

# Validate the whole bundle before replacing any app assets.
mkdir -p "$assets_dir"
for index in "${!asset_names[@]}"; do
  source="${sources[$index]}"
  [[ -n "$source" ]] || continue
  destination="$assets_dir/${asset_names[$index]}"
  # Common CI setup may already have imported this exact directory.
  [[ "$source" -ef "$destination" ]] || cp "$source" "$destination"
done
echo "Model assets prepared in $assets_dir"
