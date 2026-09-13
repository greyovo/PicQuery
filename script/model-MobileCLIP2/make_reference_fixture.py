#!/usr/bin/env python3
"""Make a square RGB image and official FP32 embeddings for Android parity checks."""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path

import numpy as np
import open_clip
import onnxruntime as ort
import torch
from ai_edge_litert.interpreter import Interpreter
from mobileclip.modules.common.mobileone import reparameterize_model
from PIL import Image, ImageOps


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--image", type=Path, required=True)
    parser.add_argument("--image-source", default="user supplied image")
    parser.add_argument("--text", default="a photo of a dog")
    parser.add_argument("--output-dir", type=Path, default=Path("build/mobileclip2-fixtures"))
    parser.add_argument("--asset-dir", type=Path, default=Path("app/src/main/assets"))
    args = parser.parse_args()
    torch.set_num_threads(4)
    torch.set_grad_enabled(False)
    model, _, preprocess = open_clip.create_model_and_transforms(
        "MobileCLIP2-S0", pretrained="dfndr2b", image_mean=(0, 0, 0), image_std=(1, 1, 1)
    )
    model = reparameterize_model(model.eval()).eval()
    with Image.open(args.image) as source:
        image = preprocess(ImageOps.exif_transpose(source).convert("RGB")).unsqueeze(0)
    tokens = open_clip.get_tokenizer("MobileCLIP2-S0")([args.text])
    with torch.inference_mode():
        image_features = model.encode_image(image)
        text_features = model.encode_text(tokens)
        image_features /= image_features.norm(dim=-1, keepdim=True)
        text_features /= text_features.norm(dim=-1, keepdim=True)
    args.output_dir.mkdir(parents=True, exist_ok=True)
    png_path = args.output_dir / "reference_dog_256.png"
    pixels = np.rint(image[0].permute(1, 2, 0).numpy() * 255).astype(np.uint8)
    Image.fromarray(pixels).save(png_path)
    options = ort.SessionOptions()
    options.intra_op_num_threads = 4
    options.inter_op_num_threads = 1
    onnx_path = args.asset_dir / "mobileclip2_s0_text_int8.onnx"
    tflite_path = args.asset_dir / "text_model_dynamic_wi8.tflite"
    session = ort.InferenceSession(str(onnx_path), options, providers=["CPUExecutionProvider"])
    token_array = tokens.numpy().astype(np.int32)
    onnx_embedding = session.run(None, {"text": token_array})[0][0]
    interpreter = Interpreter(model_path=str(tflite_path), num_threads=4)
    interpreter.allocate_tensors()
    interpreter.set_tensor(interpreter.get_input_details()[0]["index"], token_array)
    interpreter.invoke()
    tflite_embedding = interpreter.get_tensor(interpreter.get_output_details()[0]["index"])[0]
    payload = {
        "model": "MobileCLIP2-S0", "pretrained": "dfndr2b", "reference_precision": "float32",
        "image_file": png_path.name, "image_source": args.image_source,
        "image_sha256": hashlib.sha256(png_path.read_bytes()).hexdigest(),
        "input_shape": [1, 3, 256, 256], "pixel_range": [0, 1], "color": "RGB",
        "text": args.text, "token_ids": tokens[0].tolist(),
        "image_embedding": image_features[0].tolist(),
        "text_embedding": text_features[0].tolist(),
        "cosine": float((image_features * text_features).sum()),
        "quantized_text_embeddings": {"onnx": onnx_embedding.tolist(), "tflite": tflite_embedding.tolist()},
        "quantized_text_model_sha256": {
            "onnx": hashlib.sha256(onnx_path.read_bytes()).hexdigest(),
            "tflite": hashlib.sha256(tflite_path.read_bytes()).hexdigest(),
        },
        "expected_tolerances": {"image_cosine_min": 0.9999, "int8_text_cosine_min": 0.98,
                                "quantized_runtime_cosine_min": 0.995},
    }
    json_path = args.output_dir / "reference_dog_256.json"
    json_path.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")
    print(f"Wrote {png_path} and {json_path}")


if __name__ == "__main__":
    main()
