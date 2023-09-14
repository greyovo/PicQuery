import numpy as np
import onnx
import onnxruntime as ort
from PIL import Image
from torchvision.transforms import Compose, Resize, CenterCrop, ToTensor, Normalize, InterpolationMode

# quantized_model = onnx.load('../encoder/quant-int8.onnx')
ort_session = ort.InferenceSession("clip-image-encoder-quant-int8.onnx")

# 获取模型的输入名称
input_name = ort_session.get_inputs()[0].name

i = Image.open("images/image@400px.jpg")


def _convert_image_to_rgb(image: Image):
    return image.convert("RGB")


def _transform(n_px):
    return Compose([
        Resize(n_px, interpolation=InterpolationMode.NEAREST),
        CenterCrop(n_px),
        # _convert_image_to_rgb,
        ToTensor(),
        Normalize((0.48145466, 0.4578275, 0.40821073), (0.26862954, 0.26130258, 0.27577711)),
    ])


preprocess = _transform(224)

image_orig = preprocess(i).unsqueeze(0)


# image_orig = preprocess(i)
# print(type(image_orig))
# image_orig.save("last.jpg")


def to_numpy(tensor):
    return tensor.detach().cpu().numpy() if tensor.requires_grad else tensor.cpu().numpy()


# 运行推理
outputs = ort_session.run(None, {input_name: to_numpy(image_orig)})

print(outputs)
