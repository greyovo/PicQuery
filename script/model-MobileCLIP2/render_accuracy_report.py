#!/usr/bin/env python3
"""Render measured metrics and deterministically selected disagreements."""
import argparse
import json
import textwrap
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont, ImageOps

LABELS = {
    "v1_s0_onnx_int8": "v1 S0 · ONNX INT8 文本",
    "v1_s2_onnx_int8": "v1 S2 · ONNX INT8 文本",
    "v2_s0_onnx_int8": "v2 S0 · ONNX INT8 文本",
    "v2_s0_tflite_int8": "v2 S0 · TFLite INT8 文本",
    "legacy_clip_int8": "旧 App CLIP · 双塔 INT8",
    "v1_s0_fp32_control": "v1 S0 · 离线 FP32 参照",
    "v1_s2_fp32_control": "v1 S2 · 离线 FP32 参照",
    "v2_s0_fp32_control": "v2 S0 · 离线 FP32 参照",
}


def report(data, manifest, output):
    cifar = data["datasets"]["cifar100"]["metrics"]
    imagenette = data["datasets"]["imagenette"]["metrics"]
    difference = lambda rows, a, b: (rows[b]["top1"] - rows[a]["top1"]) * 100
    lines = ["# MobileCLIP v1 / v2 识别精度实测", "", "日期：2026-09-13。CPU 4 线程，模型不训练、不微调。", "",
             f"同 S0 规模比较，v2 TFLite 相对 v1 ONNX 的 Top1 差值：CIFAR-100 {difference(cifar, 'v1_s0_onnx_int8', 'v2_s0_tflite_int8'):+.2f} pp，"
             f"Imagenette {difference(imagenette, 'v1_s0_onnx_int8', 'v2_s0_tflite_int8'):+.2f} pp。"
             f"更大的 v1 S2 在 CIFAR-100 相对 v2 S0 TFLite 为 {difference(cifar, 'v2_s0_tflite_int8', 'v1_s2_onnx_int8'):+.2f} pp。", "",
             f"去掉文本量化差异后，v1 S0 → v2 S0 的 CIFAR-100 FP32 参照差值为 {difference(cifar, 'v1_s0_fp32_control', 'v2_s0_fp32_control'):+.2f} pp。"
             "部署版本的收益同时包含模型差异和量化损失差异，不能全部归因于代际升级。",
             "", "## 端侧精度配置", "",
             "MobileCLIP 各版本使用 FP32 图像塔和动态 INT8 文本；旧 App CLIP 使用现存双塔 INT8 ORT。"
             "v1 S0 的标准量化保留文本塔 Conv 为 FP32，其余可量化 MatMul/Gemm/Gather 使用 INT8。"
             "FP32 文本参照仅用于离线度量量化损失，没有更改打包资产。", "",
             "| 模型 | 两塔大小 MiB | CIFAR-100 Top1 | Imagenette Top1 |", "|---|---:|---:|---:|"]
    for key in LABELS:
        if key not in data["models"] or "control" in key:
            continue
        values = [data["datasets"][ds]["metrics"][key]["top1"] * 100 for ds in ("cifar100", "imagenette")]
        lines.append(f"| {LABELS[key]} | {data['models'][key]['total_bytes'] / 2**20:.1f} | {values[0]:.2f}% | {values[1]:.2f}% |")
    lines += ["", "Top1 是图片在全部候选类别中排名第一且与真实标签相同的比例。", "", "## 类别搜图", "",
              "每个类别用同一句英文查询搜索全部评测图片。P@10 是前 10 张中的同类比例；mAP 衡量全部排名中的同类检索质量。"
              "它们不是自然语言 caption 检索 Recall@K。", "",
              "| 模型 | CIFAR P@10 / mAP | Imagenette P@10 / mAP |", "|---|---:|---:|"]
    for key in LABELS:
        if key not in data["models"] or "control" in key:
            continue
        cells = []
        for ds in ("cifar100", "imagenette"):
            m = data["datasets"][ds]["metrics"][key]
            cells.append(f"{m['class_query_p_at_10'] * 100:.2f}% / {m['class_query_map'] * 100:.2f}%")
        lines.append(f"| {LABELS[key]} | {' | '.join(cells)} |")
    lines += ["", "## 文本量化影响", "",
              "固定同一图像塔和图像特征，仅替换文本塔。负差值表示量化后准确率下降。", "",
              "| 文本配置 | CIFAR Top1：FP32 → INT8（差值） | Imagenette Top1：FP32 → INT8（差值） |", "|---|---:|---:|"]
    for key, control in [("v1_s0_onnx_int8", "v1_s0_fp32_control"), ("v1_s2_onnx_int8", "v1_s2_fp32_control"),
                         ("v2_s0_onnx_int8", "v2_s0_fp32_control"), ("v2_s0_tflite_int8", "v2_s0_tflite_fp32_control")]:
        cells = []
        for ds in ("cifar100", "imagenette"):
            rows = data["datasets"][ds]["metrics"]
            a, b = rows[control]["top1"] * 100, rows[key]["top1"] * 100
            cells.append(f"{a:.2f}% → {b:.2f}%（{b-a:+.2f} pp）")
        lines.append(f"| {LABELS[key]} | {' | '.join(cells)} |")
    lines += ["", "TFLite 对照固定同一 TFLite 图像特征，仅把 TFLite INT8 文本换成 ONNX FP32 文本参照。",
              "", "## 成对差值与不确定性", "",
              "同一批图片进行 2,000 次成对 bootstrap。区间只描述当前数据分布的抽样不确定性，不涵盖其他数据集、提示词或训练重复的差异。", "",
              "| 数据集 | A → B | Top1 差值 pp | 95% 区间 pp | A 独对 / B 独对 |", "|---|---|---:|---:|---:|"]
    wanted = {(a, b) for a, b in [("v1_s0_onnx_int8", "v2_s0_onnx_int8"),
                                ("v1_s0_onnx_int8", "v2_s0_tflite_int8"),
                                ("v1_s2_onnx_int8", "v2_s0_tflite_int8"),
                                ("v2_s0_onnx_int8", "v2_s0_tflite_int8")]}
    for ds, bundle in data["datasets"].items():
        for c in bundle["comparisons"]:
            if (c["a"], c["b"]) in wanted:
                lo, hi = c["paired_bootstrap_95ci_pp"]
                lines.append(f"| {ds} | {LABELS[c['a']]} → {LABELS[c['b']]} | {c['b_minus_a_top1_pp']:+.2f} | [{lo:+.2f}, {hi:+.2f}] | {c['a_only_correct']} / {c['b_only_correct']} |")
    lines += ["", "## 方法与边界", "",
              "- CIFAR-100：官方 test split，固定 seed 20260913，每类抽 20 张，共 2,000 张；原图仅 32×32。",
              "- Imagenette：320px 版本的完整 val split，3,925 张、10 类；使用目录原始标签，没有使用随机噪声 CSV。它不是 ImageNet-1k。",
              "- 所有模型使用固定单模板 `a photo of a {class}`，CIFAR 类名去下划线，Imagenette 用 torchvision 的第一个英文别名；未根据结果改词。",
              "- MobileCLIP 使用官方 RGB [0,1]、bilinear 短边 256 + 中心裁剪；历史 CLIP 使用旧 App 的 stretch224 和 CLIP mean/std。历史行反映旧管线，不能把所有差异都归因于架构。",
              "- 旧 MobileCLIP 的 `vision_model.ort / text_model.ort` 已缺失，无法确认旧包型号。这里的 v1 S0/S2 是从 Apple 官方 checkpoint 重新导出的基线。",
              "- 未评估中文到英文翻译、OCR、复杂自然语言描述、真实个人相册，也未测试真机时延。不同模型的向量必须各自建索引。",
              "", "## 可复核文件", "",
              "- [原始指标、逐图预测、模型哈希和依赖版本](accuracy-results.json)",
              "- [v1 官方 checkpoint、转换误差、量化覆盖与 tokenizer 校验](accuracy-v1-models.json)",
              "- [样本 ID、标签、哈希和全部查询](accuracy-results-manifest.json)",
              "- [自动选择的互有胜负样例](accuracy-disagreements.jpg)",
              "", "来源：[Apple 官方模型](https://github.com/apple-aiml-research/ml-mobileclip)、"
              "[CIFAR-100](https://www.cs.toronto.edu/~kriz/cifar.html)、[Imagenette](https://github.com/fastai/imagenette)。", ""]
    output.write_text("\n".join(lines), encoding="utf-8")


def disagreements(data, manifest, root, target):
    m = manifest["imagenette"]
    rows = data["datasets"]["imagenette"]["metrics"]
    labels = np.array([s["label"] for s in m["samples"]])
    groups = [("v1_s0_onnx_int8", "v2_s0_tflite_int8"), ("v2_s0_tflite_int8", "v1_s0_onnx_int8"),
              ("v2_s0_onnx_int8", "v2_s0_tflite_int8"), ("v2_s0_tflite_int8", "v2_s0_onnx_int8")]
    font_file = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
    font = ImageFont.truetype(font_file, 15)
    heading = ImageFont.truetype(font_file, 20)
    sheet = Image.new("RGB", (1280, 1540), "#f5f6f7")
    draw = ImageDraw.Draw(sheet)
    draw.text((16, 12), "Imagenette: first 4 sample IDs per disagreement group (automatic selection)", font=heading, fill="#15191d")
    for row, (a, b) in enumerate(groups):
        pa, pb = np.array(rows[a]["predicted_class"]), np.array(rows[b]["predicted_class"])
        indices = np.flatnonzero((pa == labels) & (pb != labels))[:4]
        y = 54 + row * 370
        draw.text((16, y), f"Correct: {a}  |  Wrong: {b}", font=heading, fill="#15191d")
        for col, i in enumerate(indices):
            x = col * 320 + 10
            with Image.open(root / m["samples"][int(i)]["id"]) as source:
                photo = ImageOps.contain(source.convert("RGB"), (300, 240))
            sheet.paste(photo, (x + (300-photo.width)//2, y+32))
            captions = [f"True: {m['classes'][int(labels[i])]}", f"Other prediction: {m['classes'][int(pb[i])]}",
                        Path(m["samples"][int(i)]["id"]).name]
            line_y = y + 278
            for caption in captions:
                for line in textwrap.wrap(caption, 36):
                    draw.text((x, line_y), line, font=font, fill="#15191d")
                    line_y += 17
    sheet.save(target, quality=90)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--results", type=Path, default=Path("script/model-MobileCLIP2/results/accuracy-results.json"))
    parser.add_argument("--data-root", type=Path, default=Path("build/mobileclip-accuracy-data"))
    args = parser.parse_args()
    data = json.loads(args.results.read_text(encoding="utf-8"))
    manifest = json.loads(args.results.with_name(args.results.stem + "-manifest.json").read_text(encoding="utf-8"))
    report(data, manifest, args.results.with_name("识别精度对比.md"))
    disagreements(data, manifest, args.data_root, args.results.with_name("accuracy-disagreements.jpg"))


if __name__ == "__main__":
    main()
