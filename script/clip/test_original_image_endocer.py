import onnxruntime as ort
import torch
from PIL import Image

from onnxruntime.transformers import optimizer
from torchvision.transforms import Compose, Resize, CenterCrop, ToTensor, Normalize, InterpolationMode

model = "clip-image-encoder.onnx"


def test_original():
    ort_session = ort.InferenceSession(model)

    # 获取模型的输入名称
    input_name = ort_session.get_inputs()[0].name

    i = Image.open("../../image.jpg")

    def _convert_image_to_rgb(image: Image):
        return image.convert("RGB")

    def _transform(n_px):
        return Compose([
            Resize(n_px, interpolation=InterpolationMode.NEAREST),
            CenterCrop(n_px),
            _convert_image_to_rgb,
            ToTensor(),
            Normalize((0.48145466, 0.4578275, 0.40821073), (0.26862954, 0.26130258, 0.27577711)),
        ])

    preprocess = _transform(224)

    image_orig = preprocess(i).unsqueeze(0).to("cpu")

    def to_numpy(tensor):
        return tensor.detach().cpu().numpy() if tensor.requires_grad else tensor.cpu().numpy()

    outputs = ort_session.run(None, {input_name: to_numpy(image_orig)})

    print(outputs)
    return outputs


if __name__ == '__main__':
    test_original()
