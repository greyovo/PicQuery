import clip
from PIL import Image
from torch import Tensor
import torch

# Export ImageEncoder of the CLIP to onnx model
if __name__ == '__main__':
    device = "cpu"
    # print(clip.available_models())
    model, preprocess = clip.load("ViT-B/32", device=device, jit=False)
    i = Image.open("../../image.jpg")
    input_tensor: Tensor = preprocess(i).unsqueeze(0).to(device)
    vit = model.visual
    vit.eval()

    onnx_filename = 'clip-image-encoder.onnx'
    torch.onnx.export(vit, input_tensor, onnx_filename)
    # python -m onnxsim clip-image-encoder.onnx clip-image-encoder-optimized.onnx


