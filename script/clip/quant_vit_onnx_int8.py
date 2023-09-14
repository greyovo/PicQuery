import os
from pathlib import Path

import onnxruntime as ort
from PIL import Image
from onnxruntime.quantization import quantize_dynamic, QuantType, quant_pre_process

from torchvision.transforms import Compose, Resize, CenterCrop, ToTensor, Normalize, InterpolationMode

model = "clip-image-encoder.onnx"
model_prep = "clip-image-encoder-quant-pre.onnx"
model_quant = "clip-image-encoder-quant-int8.onnx"


def quant():
    cur_path = Path(os.curdir)

    quant_pre_process(model, model_prep)  # preprocess for quantization
    quantize_dynamic(cur_path / model_prep, cur_path / model_quant, weight_type=QuantType.QInt8,
                     nodes_to_exclude=['/conv1/Conv'])


def test():
    ort_session = ort.InferenceSession(model_quant)

    input_name = ort_session.get_inputs()[0].name

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

    image_input = preprocess(Image.open("../../image.jpg")).unsqueeze(0).to("cpu")

    outputs = ort_session.run(None, {input_name: image_input.numpy()})

    print(outputs[0])
    return outputs[0]


if __name__ == '__main__':
    quant()
    # res = test()
# python -m onnxruntime.tools.check_onnx_model_mobile_usability clip-text-encoder-quant-int8.onnx
# python -m onnxruntime.tools.convert_onnx_models_to_ort clip-image-encoder-quant-int8.onnx
