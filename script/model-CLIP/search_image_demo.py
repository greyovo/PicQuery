import clip
import numpy
import onnxruntime as ort
from typing import Tuple, List

from PIL import Image
from numpy import ndarray
from torch.ao.ns.fx.utils import compute_cosine_similarity
from tqdm import tqdm
from torch import Tensor
import os
from torchvision.datasets import CIFAR100

vit_model_uint8 = "clip-image-encoder-quant-uint8.onnx"
vit_model_int8 = "clip-image-encoder-quant-int8.onnx"
vit_model_fp16 = "clip-image-encoder-fp16.onnx"
vit_model_fp32 = "clip-image-encoder.onnx"
text_model_uint8 = "clip-text-encoder-quant-uint8.onnx"
text_model_int8 = "clip-text-encoder-quant-int8.onnx"
text_model_fp16 = "clip-text-encoder-fp16.onnx"
text_model_fp32 = "clip-text-encoder.onnx"

image_encoder: ort.InferenceSession
text_encoder: ort.InferenceSession

_, preprocess = clip.load('ViT-B/32')

image_embeddings: list[Tuple[str, Tensor]] = []  # (image_file_name, image_feat)

LIMIT = 100


def to_numpy(tensor: Tensor, dtype=None):
    r: ndarray = tensor.detach().cpu().numpy() if tensor.requires_grad else tensor.cpu().numpy()
    r.astype(dtype=dtype)
    return r


def build_image_embedding(images: List[str]):
    print("images on disk:")
    for i in tqdm(range(len(images)), unit=" images", postfix="build_image_embedding..."):
        image = Image.open("images/" + images[i])
        image_input = preprocess(image).unsqueeze(0)
        image_features = image_encoder.run(None, {image_encoder.get_inputs()[0].name: to_numpy(image_input)})[0]

        image_features = Tensor(numpy.array(image_features))
        image_embeddings.append((images[i], image_features))


def search_by_input(input_text: str) -> Tuple[str, Tensor] or None:
    text_input = clip.tokenize(input_text)
    print("Token:", text_input, len(text_input[0]))
    print("InputNames", text_encoder.get_inputs()[0].name)
    arr = text_encoder.run(None, {text_encoder.get_inputs()[0].name: to_numpy(text_input, dtype=int)})[0]
    text_features = Tensor(arr)
    print("text_features", text_features)
    MAX_SIM = -999
    result: Tuple[str, Tensor] or None = None
    for img_ebd in image_embeddings:
        sim = compute_cosine_similarity(img_ebd[1], text_features)
        if sim > MAX_SIM:
            MAX_SIM = sim
            result = img_ebd
    return result[0], MAX_SIM


def image_search_test():
    images = load_images()
    build_image_embedding(images)
    while True:
        prompt = input("Search photo:")
        label, score = search_by_input(prompt)
        print("Result:", label, score)
        Image.open("images/" + label).show()


def load_images() -> List[str]:
    return os.listdir("images")


if __name__ == '__main__':
    image_encoder = ort.InferenceSession(vit_model_int8)
    text_encoder = ort.InferenceSession(text_model_int8)
    image_search_test()
