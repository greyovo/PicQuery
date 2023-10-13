import torch

from encoder.export_text_encoder import TextEncoder

# Export ImageEncoder of the CLIP to onnx model
if __name__ == '__main__':
    import clip

    device = "cpu"
    model, preprocess = clip.load("ViT-B/32", device=device)
    model.eval()

    text_encoder = TextEncoder(embed_dim=512, context_length=77, vocab_size=49408,
                               transformer_width=512, transformer_heads=8, transformer_layers=12)

    missing_keys, unexpected_keys = text_encoder.load_state_dict(model.state_dict(), strict=False)

    text_encoder.eval()

    input_tensor = clip.tokenize("a diagram").to(device)
    traced_model = torch.jit.trace(text_encoder, input_tensor)

    onnx_filename = 'clip-text-encoder.onnx'

    torch.onnx.export(text_encoder, input_tensor, onnx_filename)
    # python -m onnxsim clip-text-encoder.onnx clip-text-encoder-optimized.onnx
