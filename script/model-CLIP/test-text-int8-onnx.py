import numpy as np
import onnx
import onnxruntime as ort
from PIL import Image
from clip import clip
from numpy import ndarray
from onnxruntime import SessionOptions
from torch import Tensor
from torchvision.transforms import Compose, Resize, CenterCrop, ToTensor, Normalize, InterpolationMode


# quantized_model = onnx.load('../encoder/quant-int8.onnx')

def to_numpy(tensor: Tensor, dtype=None):
    r: ndarray = tensor.detach().cpu().numpy() if tensor.requires_grad else tensor.cpu().numpy()
    r.astype(dtype=dtype)
    return r


options = SessionOptions()
options.add_session_config_entry("session.load_model_format", "ORT")
ort_session = ort.InferenceSession("clip-text-encoder-quant-int8.with_runtime_opt.ort",
                                   sess_options=options, )

# 获取模型的输入名称
input_name = ort_session.get_inputs()[0].name

# image_orig = preprocess(i)
# print(type(image_orig))
# image_orig.save("last.jpg")
input_text = "by the sea"
text_input = clip.tokenize(input_text)
print(text_input)
# 运行推理
outputs = ort_session.run(None, {input_name: to_numpy(text_input)})

print(outputs)

print(ort_session.get_outputs()[0].name)
