#!/usr/bin/env python3
"""Dynamic weight quantization for exported MobileCLIP2 text TFLite assets."""

from __future__ import annotations

import argparse
from pathlib import Path

from ai_edge_quantizer import Quantizer, recipe


DEFAULT_ASSET_DIR = Path("app/src/main/assets")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Quantize MobileCLIP2 TFLite assets with int8 weights."
    )
    parser.add_argument(
        "--asset-dir",
        type=Path,
        default=DEFAULT_ASSET_DIR,
        help="Directory containing image_model.tflite and text_model.tflite.",
    )
    parser.add_argument(
        "--image-input",
        default="image_model.tflite",
        help="Float image tower TFLite filename for experimental image quantization.",
    )
    parser.add_argument(
        "--text-input",
        default="text_model.tflite",
        help="Float text tower TFLite filename.",
    )
    parser.add_argument(
        "--image-output",
        default="image_model_dynamic_wi8.tflite",
        help="Experimental quantized image tower TFLite filename.",
    )
    parser.add_argument(
        "--text-output",
        default="text_model_dynamic_wi8.tflite",
        help="Quantized text tower TFLite filename.",
    )
    parser.add_argument(
        "--include-image-experimental",
        action="store_true",
        help=(
            "Also quantize the image tower. This may hurt embedding quality; "
            "validate retrieval quality before using it."
        ),
    )
    return parser.parse_args()


def quantize_model(input_path: Path, output_path: Path) -> None:
    quantizer = Quantizer(input_path, recipe.dynamic_wi8_afp32())
    quantizer.quantize(serialize_to_path=output_path)
    print(f"{input_path} -> {output_path} ({output_path.stat().st_size} bytes)")


def main() -> int:
    args = parse_args()
    quantize_model(args.asset_dir / args.text_input, args.asset_dir / args.text_output)
    if args.include_image_experimental:
        quantize_model(args.asset_dir / args.image_input, args.asset_dir / args.image_output)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
