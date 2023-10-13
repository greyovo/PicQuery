import onnxruntime as ort
import torch
from PIL import Image
from clip import clip
from numpy import ndarray

from onnxruntime.transformers import optimizer
from torch import Tensor
from torchvision.transforms import Compose, Resize, CenterCrop, ToTensor, Normalize, InterpolationMode

model = "clip-text-encoder.onnx"


def to_numpy(tensor: Tensor, dtype=None):
    r: ndarray = tensor.detach().cpu().numpy() if tensor.requires_grad else tensor.cpu().numpy()
    r.astype(dtype=dtype)
    return r


if __name__ == '__main__':
    input_text = "by the sea"
    text_encoder = ort.InferenceSession(model)
    text_input = clip.tokenize(input_text)
    print("Token:", text_input, len(text_input[0]))
    print("InputNames", text_encoder.get_inputs()[0].name)
    arr = text_encoder.run(None, {text_encoder.get_inputs()[0].name: to_numpy(text_input, dtype=int)})[0]

    print(arr)
