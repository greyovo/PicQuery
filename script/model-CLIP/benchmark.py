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

cifar100 = CIFAR100(root=os.path.expanduser("../../dataset"), download=True, train=False)

image_embeddings: list[Tuple[str, Tensor]] = []

LIMIT = 100


def to_numpy(tensor: Tensor, dtype=None):
    r: ndarray = tensor.detach().cpu().numpy() if tensor.requires_grad else tensor.cpu().numpy()
    r.astype(dtype=dtype)
    return r


def build_image_embedding():
    for i in tqdm(range(LIMIT), unit=" images", postfix="build_image_embedding..."):
        image, class_id = cifar100[i]
        label = cifar100.classes[class_id]
        image_input = preprocess(image).unsqueeze(0)
        image_features = image_encoder.run(None, {image_encoder.get_inputs()[0].name: to_numpy(image_input)})[0]

        image_features = Tensor(numpy.array(image_features))
        image_embeddings.append((label, image_features))


def search_by_input(input_text: str) -> Tuple[str, Tensor] or None:
    text_input = clip.tokenize(input_text)
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
    print(MAX_SIM)
    return result


def benchmark():
    build_image_embedding()
    correct = 0
    count = 0
    for i in tqdm(range(LIMIT), unit=" images", postfix="validating..."):
        image, class_id = cifar100[i]
        label = cifar100.classes[class_id]
        res = search_by_input(f"a photo of a {label}")
        if res[0] == label:
            correct += 1
        count += 1

    print("Accuracy:", correct / LIMIT)


def image_search_test():
    images = load_images()
    build_image_embedding_from_disk(images)
    while True:
        prompt = input("Search photo:")
        index, score = search_by_input(prompt)
        Image.open("images/" + images[index]).show()

    # for i in tqdm(range(len(images)), unit=" images", postfix="validating..."):
    #     image, class_id = cifar100[i]
    #     label = cifar100.classes[class_id]
    #     res = search_by_input(f"a photo of a {label}")
    #     if res[0] == label:
    #         correct += 1
    #     count += 1

    # print("Accuracy:", correct / LIMIT)


# Testing fp32 version...
# 100%|██████████| 5000/5000 [04:03<00:00, 20.57 images/s, build_image_embedding...]
# 100%|██████████| 5000/5000 [28:51<00:00,  2.89 images/s, validating...]
# Accuracy: 0.9404
# ==========================
# Testing fp32 version...
# 100%|██████████| 2000/2000 [01:29<00:00, 35.84 images/s, build_image_embedding...]
# 100%|██████████| 2000/2000 [03:21<00:00, 11.91 images/s, validating...]
# Accuracy: 0.871
# ==========================

# Testing uint8 version...
# 100%|██████████| 5000/5000 [02:29<00:00, 33.35 images/s, build_image_embedding...]
# 100%|██████████| 5000/5000 [15:10<00:00,  5.49 images/s, validating...]
# Accuracy: 0.7814
# ==========================
# Testing int8 version...
# 100%|██████████| 2000/2000 [00:55<00:00, 35.84 images/s, build_image_embedding...]
# 100%|██████████| 2000/2000 [02:47<00:00, 11.91 images/s, validating...]
# Accuracy: 0.825
# ==========================
# Testing int8 version...
# 100%|██████████| 5000/5000 [02:17<00:00, 36.47 images/s, build_image_embedding...]
# 100%|██████████| 5000/5000 [16:27<00:00,  5.06 images/s, validating...]
# Accuracy: 0.8304

def load_images() -> List[str]:
    return os.listdir("images")


def build_image_embedding_from_disk(images: List[str]):
    print("images on disk:")
    for i in tqdm(range(len(images)), unit=" images", postfix="build_image_embedding..."):
        image = Image.open("images/" + images[i])
        image_input = preprocess(image).unsqueeze(0)
        image_features = image_encoder.run(None, {image_encoder.get_inputs()[0].name: to_numpy(image_input)})[0]

        image_features = Tensor(numpy.array(image_features))
        image_embeddings.append((i, image_features))


if __name__ == '__main__':
    image_encoder = ort.InferenceSession(vit_model_int8)
    text_encoder = ort.InferenceSession(text_model_int8)
    image_search_test()
    # LIMIT = 100
    # print("Testing int8 version...")
    # benchmark()
    # print("==========================")
    # print("Testing fp32 version...")
    # # fp32(original), CIFAR ~2000, acc: 0.87
    # # fp16, CIFAR ~500, acc: 0.814
    # image_encoder = ort.InferenceSession(vit_model_fp32)
    # text_encoder = ort.InferenceSession(text_model_fp32)
    # benchmark()
