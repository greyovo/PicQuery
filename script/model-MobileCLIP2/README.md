# MobileCLIP2 TFLite Export

This folder exports Apple MobileCLIP / MobileCLIP2 PyTorch checkpoints to
LiteRT/TFLite assets used by the Android `modulesTF` module.

## Latest Model Choice

Use `MobileCLIP2-S0` first. It is the smallest official MobileCLIP2 model and
keeps Android iteration practical. Larger variants such as `MobileCLIP2-S2` can
be exported with the same script after the first path is validated.

## Setup

```bash
python3 -m venv .venv-mobileclip-export
. .venv-mobileclip-export/bin/activate
pip install -U pip
pip install --index-url https://download.pytorch.org/whl/cpu \
  torch==2.11.0+cpu torchvision==0.26.0+cpu
pip install litert-torch open-clip-torch \
  git+https://github.com/apple/ml-mobileclip.git \
  git+https://github.com/huggingface/pytorch-image-models
```

## Export

```bash
python script/model-MobileCLIP2/export_mobileclip2_tflite.py
```

Outputs:

- `app/src/main/assets/image_model.tflite`
- `app/src/main/assets/text_model.tflite`
- `app/src/main/assets/mobileclip2_tflite_metadata.json`

To export a larger model:

```bash
python script/model-MobileCLIP2/export_mobileclip2_tflite.py \
  --model MobileCLIP2-S2 \
  --pretrained dfndr2b
```

The exported image tower expects `float32` input shaped `[1, 3, 256, 256]` with
pixel values scaled to `[0, 1]`. The exported text tower expects `int32` CLIP
BPE token ids shaped `[1, 77]`.
