import os
from pathlib import Path

import clip
import onnxruntime as ort
from onnxruntime.quantization import quantize_dynamic, QuantType
from onnxruntime.quantization.shape_inference import quant_pre_process
from torch import Tensor

model = "clip-text-encoder.onnx"
model_prep = "clip-text-encoder-quant-pre.onnx"
model_quant = "clip-text-encoder-quant-int8.onnx"


def quant():
    cur_path = Path(os.curdir)

    quant_pre_process(model, model_prep)  # preprocess for quantization
    quantize_dynamic(cur_path / model_prep, cur_path / model_quant, weight_type=QuantType.QInt8)


def test():
    ort_session = ort.InferenceSession(model_quant)
    input_name = ort_session.get_inputs()[0].name

    text = "a dog"
    token_input: Tensor = clip.tokenize(text)
    outputs = ort_session.run(None, {input_name: token_input.numpy()})
    print(outputs[0])
    return outputs[0]


if __name__ == '__main__':
    quant()
    # res = test()
    # python -m onnxruntime.tools.convert_onnx_models_to_ort clip-text-encoder-quant-int8.onnx
#     python -m onnxruntime.tools.check_onnx_model_mobile_usability clip-text-encoder-quant-int8.onnx
#     python -m onnxruntime.tools.check_onnx_model_mobile_usability clip-text-encoder-quant-int8.ort
