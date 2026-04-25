#!/usr/bin/env python3
"""Export Apple MobileCLIP / MobileCLIP2 checkpoints to LiteRT/TFLite.

The script emits two Android-friendly assets:

* image_model.tflite: input float32 [1, 3, image_size, image_size]
* text_model.tflite: input int32 [1, context_length]

Default settings target MobileCLIP2-S0 because it is the lightest official
MobileCLIP2 variant and is suitable for first-pass Android validation.
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

import torch
from torch import nn


DEFAULT_OUTPUT_DIR = Path("app/src/main/assets")


class ImageTower(nn.Module):
    def __init__(self, model: nn.Module, normalize: bool) -> None:
        super().__init__()
        self.model = model
        self.normalize = normalize

    def forward(self, image: torch.Tensor) -> torch.Tensor:
        features = self.model.encode_image(image)
        if self.normalize:
            features = features / features.norm(dim=-1, keepdim=True)
        return features


class TextTower(nn.Module):
    def __init__(self, model: nn.Module, normalize: bool) -> None:
        super().__init__()
        self.model = model
        self.normalize = normalize

    def forward(self, text: torch.Tensor) -> torch.Tensor:
        features = self.model.encode_text(text)
        if self.normalize:
            features = features / features.norm(dim=-1, keepdim=True)
        return features


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Export official Apple MobileCLIP2 PyTorch checkpoints to TFLite."
    )
    parser.add_argument(
        "--model",
        default="MobileCLIP2-S0",
        help="OpenCLIP model name, for example MobileCLIP2-S0 or MobileCLIP2-S2.",
    )
    parser.add_argument(
        "--pretrained",
        default="dfndr2b",
        help=(
            "OpenCLIP pretrained tag or local checkpoint path. "
            "Use dfndr2b for official MobileCLIP2 checkpoints."
        ),
    )
    parser.add_argument(
        "--output-dir",
        type=Path,
        default=DEFAULT_OUTPUT_DIR,
        help="Directory for generated TFLite files.",
    )
    parser.add_argument(
        "--image-output",
        default="image_model.tflite",
        help="Generated image encoder filename.",
    )
    parser.add_argument(
        "--text-output",
        default="text_model.tflite",
        help="Generated text encoder filename.",
    )
    parser.add_argument(
        "--image-size",
        type=int,
        default=256,
        help="Image encoder input size. MobileCLIP2-S0/S2 use 256.",
    )
    parser.add_argument(
        "--context-length",
        type=int,
        default=77,
        help="Text token context length.",
    )
    parser.add_argument(
        "--no-normalize",
        action="store_true",
        help="Do not L2-normalize embeddings inside the exported towers.",
    )
    parser.add_argument(
        "--metadata-output",
        default="mobileclip2_tflite_metadata.json",
        help="Small JSON file describing exported tensors.",
    )
    return parser.parse_args()


def require_imports():
    try:
        try:
            import litert_torch as edge_torch
        except ImportError:
            import ai_edge_torch as edge_torch
        import open_clip
        from mobileclip.modules.common.mobileone import reparameterize_model
    except ImportError as exc:
        raise SystemExit(
            "Missing export dependencies. Install them with:\n"
            "  python3 -m venv .venv-mobileclip-export\n"
            "  . .venv-mobileclip-export/bin/activate\n"
            "  pip install -U pip\n"
            "  pip install --index-url https://download.pytorch.org/whl/cpu "
            "torch==2.11.0+cpu torchvision==0.26.0+cpu\n"
            "  pip install litert-torch open-clip-torch "
            "git+https://github.com/apple/ml-mobileclip.git "
            "git+https://github.com/huggingface/pytorch-image-models\n"
        ) from exc
    return edge_torch, open_clip, reparameterize_model


def create_model(model_name: str, pretrained: str) -> nn.Module:
    _, open_clip, reparameterize_model = require_imports()

    model_kwargs = {}
    if not (
        model_name in {"MobileCLIP2-S3", "MobileCLIP2-S4"}
        or model_name.endswith("L-14")
    ):
        model_kwargs = {"image_mean": (0, 0, 0), "image_std": (1, 1, 1)}

    model, _, _ = open_clip.create_model_and_transforms(
        model_name,
        pretrained=pretrained,
        **model_kwargs,
    )
    model.eval()
    return reparameterize_model(model).eval()


def export_tflite(model: nn.Module, args: argparse.Namespace) -> None:
    ai_edge_torch, _, _ = require_imports()

    args.output_dir.mkdir(parents=True, exist_ok=True)
    image_path = args.output_dir / args.image_output
    text_path = args.output_dir / args.text_output
    normalize = not args.no_normalize

    with torch.no_grad():
        image_input = (
            torch.rand(1, 3, args.image_size, args.image_size, dtype=torch.float32),
        )
        text_input = (
            torch.zeros(1, args.context_length, dtype=torch.int32),
        )

        image_model = ai_edge_torch.convert(ImageTower(model, normalize).eval(), image_input)
        image_model.export(str(image_path))

        text_model = ai_edge_torch.convert(TextTower(model, normalize).eval(), text_input)
        text_model.export(str(text_path))

    metadata = {
        "model": args.model,
        "pretrained": args.pretrained,
        "normalized_outputs": normalize,
        "image": {
            "file": args.image_output,
            "input_dtype": "float32",
            "input_shape": [1, 3, args.image_size, args.image_size],
        },
        "text": {
            "file": args.text_output,
            "input_dtype": "int32",
            "input_shape": [1, args.context_length],
        },
    }
    (args.output_dir / args.metadata_output).write_text(
        json.dumps(metadata, indent=2) + "\n",
        encoding="utf-8",
    )

    print(f"Exported image tower: {image_path}")
    print(f"Exported text tower: {text_path}")
    print(f"Wrote metadata: {args.output_dir / args.metadata_output}")


def main() -> int:
    args = parse_args()
    torch.set_grad_enabled(False)
    model = create_model(args.model, args.pretrained)
    export_tflite(model, args)
    return 0


if __name__ == "__main__":
    sys.exit(main())
