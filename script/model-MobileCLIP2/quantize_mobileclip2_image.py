#!/usr/bin/env python3
"""Calibrate a separate MobileCLIP2-S0 image S8S8 QDQ candidate on training data.

Uses 200 CIFAR-100 train and 200 Imagenette train images, with an explicit content
overlap check against the historical evaluation split. Does not evaluate accuracy
or modify production assets. Run with the ORT 1.29.0 environment.
"""
from __future__ import annotations

import argparse
from collections import Counter
from datetime import datetime, timezone
import gc
import hashlib
import inspect
import json
import os
from pathlib import Path
import sys
import tempfile
import time
from types import SimpleNamespace


ROOT = Path(__file__).resolve().parents[2]
SEED = 20260913
OPS = ["Conv", "MatMul", "Gemm"]
SPEC = {"size": 256, "resize": "center_crop", "interpolation": "bilinear",
        "mean": [0, 0, 0], "std": [1, 1, 1]}


def args_for(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, default=ROOT / "app/src/main/assets/mobileclip2_s0_image.onnx")
    parser.add_argument("--data-root", type=Path, default=ROOT / "build/mobileclip-accuracy-data")
    parser.add_argument("--output-dir", type=Path, default=ROOT / "build/mobileclip2-image-int8")
    parser.add_argument("--evaluation-manifest", type=Path,
                        default=ROOT / "script/model-MobileCLIP2/results/accuracy-results-manifest.json")
    parser.add_argument("--verification-manifest", type=Path,
                        default=ROOT / "build/mobileclip-device-benchmark/manifest.json")
    parser.add_argument("--threads", type=int, default=4)
    parser.add_argument("--keep-sensitive-fp32", action="store_true",
                        help="Mixed candidate: retain reparameterized and grouped/depthwise Conv nodes in FP32.")
    args = parser.parse_args(argv)
    if args.threads < 1:
        parser.error("--threads must be positive")
    args.output_dir = args.output_dir.resolve()
    assets = (ROOT / "app/src/main/assets").resolve()
    if args.output_dir == assets or assets in args.output_dir.parents:
        parser.error("Use a build output directory; this script must not write production assets")
    args.source = args.source.resolve()
    if args.output_dir in args.source.parents:
        parser.error("--source must be outside --output-dir so publication cannot overwrite the source")
    if os.environ.get("ORT_CONVERT_ONNX_MODELS_TO_ORT_OPTIMIZATION_LEVEL", "all") != "all":
        parser.error("Unset ORT_CONVERT_ONNX_MODELS_TO_ORT_OPTIMIZATION_LEVEL; this export requires Fixed/all")
    return args


def sha(path):
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def json_read(path):
    return json.loads(path.read_text(encoding="utf-8"))


def save(path, value):
    path.write_text(json.dumps(value, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def artifact(path, basename=False):
    return {"file": path.name if basename else str(path.resolve()), "sha256": sha(path), "bytes": path.stat().st_size}


def rgb_hash(image):
    return hashlib.sha256(image.convert("RGB").tobytes()).hexdigest()


def prepare_calibration(args, preprocess):
    import numpy as np
    from torchvision.datasets import CIFAR100, Imagenette
    import evaluate_accuracy

    historical = json_read(args.evaluation_manifest)
    evaluated = evaluate_accuracy.datasets(args.data_root, ["cifar100", "imagenette"], False, 20)
    eval_native, eval_rgb = set(), set()
    for name, (dataset, indices, _, manifest) in evaluated.items():
        old = [(row["id"], row["label"], row["sha256"]) for row in historical[name]["samples"]]
        current = [(row["id"], row["label"], row["sha256"]) for row in manifest["samples"]]
        if old != current:
            raise ValueError(f"Cached {name} evaluation inputs differ from the historical manifest")
        eval_native.update(row["sha256"] for row in manifest["samples"])
        for index in indices:
            if name == "cifar100":
                eval_rgb.add(hashlib.sha256(dataset.data[index].tobytes()).hexdigest())
            else:
                eval_rgb.add(rgb_hash(dataset[index][0]))
        print(f"Verified evaluation isolation reference: {name}, {len(indices)} samples", flush=True)
    del evaluated
    gc.collect()
    cifar = CIFAR100(args.data_root, train=True, download=False)
    imagenette = Imagenette(args.data_root, split="train", size="320px", download=False)
    imagenette._samples.sort(key=lambda sample: sample[0])
    if len(cifar) != 50000 or len(imagenette) != 9469:
        raise ValueError(f"Incomplete train cache: CIFAR100={len(cifar)}, Imagenette={len(imagenette)}")
    samples, items, overlap_native, overlap_rgb = [], [], [], []
    for name, dataset, per_class, classes in (("cifar100", cifar, 2, 100), ("imagenette", imagenette, 20, 10)):
        labels = np.asarray(dataset.targets if name == "cifar100" else [label for _, label in dataset._samples])
        rng = np.random.default_rng(SEED)
        indices = sorted(np.concatenate([rng.choice(np.flatnonzero(labels == label), per_class, replace=False)
                                         for label in range(classes)]).tolist())
        for index in indices:
            image, label = dataset[index]
            if name == "cifar100":
                sample_id = f"train/{index:05d}"
                sample_sha = hashlib.sha256(dataset.data[index].tobytes()).hexdigest()
                class_name = dataset.classes[label]
            else:
                path = Path(dataset._samples[index][0])
                sample_id = str(path.relative_to(args.data_root))
                sample_sha = sha(path)
                class_name = dataset.classes[label][0]
            pixel_sha = rgb_hash(image)
            value = np.ascontiguousarray(preprocess(image, SPEC), dtype=np.float32)
            row = {"dataset": name, "split": "train", "id": sample_id, "index": int(index),
                   "label": int(label), "class_name": class_name, "sha256": sample_sha,
                   "hash_format": "RGB uint8 array bytes" if name == "cifar100" else "original JPEG file",
                   "rgb_sha256": pixel_sha, "rgb_shape": [image.height, image.width, 3],
                   "preprocessed_sha256": hashlib.sha256(value.tobytes()).hexdigest()}
            if sample_sha in eval_native:
                overlap_native.append(row["id"])
            if pixel_sha in eval_rgb:
                overlap_rgb.append(row["id"])
            samples.append(row)
            items.append((dataset, index, row))
    if overlap_native or overlap_rgb:
        raise ValueError(f"Calibration/evaluation content overlap: native={overlap_native}, RGB={overlap_rgb}")
    if len(samples) != 400:
        raise ValueError("Expected exactly 400 calibration samples")
    leakage = {"status": "passed", "evaluation_manifest_file": str(args.evaluation_manifest.resolve()),
               "evaluation_manifest_sha256": sha(args.evaluation_manifest),
               "evaluation_counts": {"cifar100": 2000, "imagenette": 3925},
               "native_sha256_overlap": 0, "rgb_sha256_overlap": 0,
               "rgb_hash_format": "PIL RGB uint8 HWC bytes before resizing; all 5925 evaluation images decoded"}
    manifest = {
        "seed": SEED, "data_root": str(args.data_root.resolve()),
        "selection": "Independent NumPy default_rng(seed) per dataset; sorted selected train indices; no reselection",
        "sources": {"cifar100": "https://www.cs.toronto.edu/~kriz/cifar.html",
                    "imagenette": "https://github.com/fastai/imagenette"},
        "preprocess": {"spec": SPEC, "function": "evaluate_accuracy.preprocess",
                       "function_sha256": hashlib.sha256(inspect.getsource(preprocess).encode()).hexdigest(),
                       "source_file": str(Path(evaluate_accuracy.__file__).resolve()),
                       "source_file_sha256": sha(Path(evaluate_accuracy.__file__)),
                       "output": "float32 [1,3,256,256] RGB NCHW [0,1]"},
        "samples": samples, "leakage_check": leakage,
    }
    return items, manifest


def graph_summary(path):
    import numpy as np
    import onnx
    model = onnx.load(path)
    ops = Counter(node.op_type for node in model.graph.node)
    types = Counter(onnx.TensorProto.DataType.Name(t.data_type) for t in model.graph.initializer)
    elems = Counter()
    for tensor in model.graph.initializer:
        elems[onnx.TensorProto.DataType.Name(tensor.data_type)] += int(np.prod(tensor.dims))
    producers = {output: node for node in model.graph.node for output in node.output}
    initializers = {tensor.name: tensor for tensor in model.graph.initializer}
    rows = []
    for node in model.graph.node:
        if node.op_type not in OPS:
            continue
        quantized_inputs = 0
        for name in node.input[:2]:
            producer = producers.get(name)
            if producer is not None and producer.op_type == "DequantizeLinear" and len(producer.input) > 2:
                zero = initializers.get(producer.input[2])
                if zero is not None and zero.data_type == onnx.TensorProto.INT8:
                    quantized_inputs += 1
        rows.append({"name": node.name, "op_type": node.op_type, "int8_dequantized_inputs": quantized_inputs,
                     "both_inputs_quantized": quantized_inputs == 2})
    return {"nodes": len(model.graph.node), "operators": dict(sorted(ops.items())),
            "initializer_types": dict(sorted(types.items())), "initializer_elements": dict(sorted(elems.items())),
            "heavy_ops": rows}


def prepare_graph(source, stage, threads):
    import onnxruntime as ort
    from onnxruntime.quantization.shape_inference import quant_pre_process
    basic = stage / "mobileclip2_s0_image_fp32.basic.onnx"
    options = ort.SessionOptions()
    options.intra_op_num_threads, options.inter_op_num_threads = threads, 1
    options.graph_optimization_level = ort.GraphOptimizationLevel.ORT_ENABLE_BASIC
    options.optimized_model_filepath = str(basic)
    session = ort.InferenceSession(str(source), sess_options=options, providers=["CPUExecutionProvider"])
    del session
    prepared = stage / "mobileclip2_s0_image_fp32.preprocessed.onnx"
    quant_pre_process(basic, prepared, skip_optimization=True, skip_symbolic_shape=False,
                      skip_onnx_shape=False, auto_merge=True)
    basic.unlink()
    return prepared


def calibrate_and_quantize(prepared, output, items, preprocess, stage, threads, keep_sensitive_fp32):
    import numpy as np
    import onnx
    import onnxruntime as ort
    from onnxruntime.quantization import CalibrationDataReader, CalibrationMethod, QuantFormat, QuantType, quantize_static
    from onnxruntime.quantization.calibrate import MinMaxCalibrater, save_tensors_data

    class Reader(CalibrationDataReader):
        def __init__(self):
            self.rewind()

        def __len__(self):
            return len(items)

        def set_range(self, start_index, end_index):
            self.cursor, self.end = start_index, end_index

        def rewind(self):
            self.set_range(0, len(items))

        def get_next(self):
            if self.cursor >= self.end:
                return None
            dataset, index, row = items[self.cursor]
            value = np.ascontiguousarray(preprocess(dataset[index][0], SPEC), dtype=np.float32)
            if hashlib.sha256(value.tobytes()).hexdigest() != row["preprocessed_sha256"]:
                raise ValueError(f"Calibration input changed since manifest creation: {row['id']}")
            self.cursor += 1
            return {"image": value}

    reader = Reader()
    first = reader.get_next()["image"]
    reader.rewind()
    if not np.array_equal(first, reader.get_next()["image"]):
        raise ValueError("Calibration reader rewind is not deterministic")
    reader.rewind()
    augmented = stage / "calibration_augmented.onnx"
    calibrator = MinMaxCalibrater(prepared, OPS, augmented_model_path=str(augmented),
                                symmetric=False, moving_average=False, max_intermediate_outputs=None)
    calibrator.augment_graph()
    options = ort.SessionOptions()
    options.graph_optimization_level = ort.GraphOptimizationLevel.ORT_DISABLE_ALL
    options.intra_op_num_threads, options.inter_op_num_threads = threads, 1
    options.execution_mode = ort.ExecutionMode.ORT_SEQUENTIAL
    calibrator.infer_session = ort.InferenceSession(str(augmented), sess_options=options,
                                                   providers=["CPUExecutionProvider"])
    started = time.perf_counter()
    for first in range(0, len(items), 20):
        reader.set_range(first, min(first + 20, len(items)))
        calibrator.collect_data(reader)  # Computes/merges global MinMax, then releases the current block.
        print(f"Calibration {min(first + 20, len(items))}/{len(items)}: {time.perf_counter() - started:.1f}s", flush=True)
    ranges = calibrator.compute_data()
    cache = stage / "calibration_ranges.json"
    save_tensors_data(ranges, cache)
    range_count = len(ranges.data)
    del calibrator, ranges
    gc.collect()
    augmented.unlink()
    quant_options = {"ActivationSymmetric": False, "WeightSymmetric": True}
    excluded = []
    if keep_sensitive_fp32:
        for node in onnx.load(prepared).graph.node:
            group = next((attribute.i for attribute in node.attribute if attribute.name == "group"), 1)
            if node.op_type == "Conv" and ("reparam_conv" in node.name or group > 1):
                excluded.append({"name": node.name, "op_type": node.op_type, "group": group,
                                 "reason": "Reparameterized/grouped convolution retained after TRAIN-only layer error diagnosis"})
    quantize_static(prepared, output, calibration_data_reader=reader,
                    quant_format=QuantFormat.QDQ, activation_type=QuantType.QInt8,
                    weight_type=QuantType.QInt8, per_channel=True, reduce_range=False,
                    op_types_to_quantize=OPS, nodes_to_exclude=[row["name"] for row in excluded],
                    calibrate_method=CalibrationMethod.MinMax,
                    calibration_providers=["CPUExecutionProvider"], extra_options=quant_options,
                    calibration_cache_path=cache)
    return {"format": "QDQ", "activation_type": "QInt8", "weight_type": "QInt8",
            "per_channel": True, "reduce_range": False, "calibration_method": "MinMax", "op_types": OPS,
            "extra_options": quant_options, "calibration_threads": threads, "calibration_inter_op_threads": 1,
            "precision_policy": "mixed: reparameterized/grouped Conv FP32" if keep_sensitive_fp32 else "all eligible heavy operators INT8",
            "excluded_nodes": excluded,
            "calibration_execution_mode": "ORT_SEQUENTIAL", "calibration_graph_optimization": "ORT_DISABLE_ALL",
            "calibration_block_size": 20, "calibration_ranges_count": range_count,
            "calibration_samples_processed": len(items), "calibration_reader_rewind_verified": True,
            "calibration_cache": artifact(cache, True)}


def verify(args, prepared, quantized, converted, items, preprocess, format_helpers):
    import numpy as np
    import onnxruntime as ort
    settings = SimpleNamespace(threads=args.threads, target_platform="arm")
    sessions = {"source": format_helpers.cpu_session(args.source, settings, ort),
                "prepared": format_helpers.cpu_session(prepared, settings, ort),
                "onnx": format_helpers.cpu_session(quantized, settings, ort),
                "ort": format_helpers.cpu_session(converted, settings, ort)}
    contracts = {key: format_helpers.contract(session, "image") for key, session in sessions.items()}
    if any(contract != contracts["source"] for contract in contracts.values()):
        raise ValueError("Image input/output contract changed")
    fixture_args = format_helpers.parse_args(["--verification-manifest", str(args.verification_manifest)])
    sources = {"image": sha(args.source), "text": sha(ROOT / "app/src/main/assets/mobileclip2_s0_text_int8.onnx")}
    samples, provenance = format_helpers.verification_inputs(fixture_args, np, sources)
    images = samples["image"]
    training_indices = np.linspace(0, 199, 10, dtype=int).tolist() + (200 + np.linspace(0, 199, 10, dtype=int)).tolist()
    for index in training_indices:
        dataset, item, row = items[index]
        images.append((f"calibration:{row['dataset']}:{row['id']}", preprocess(dataset[item][0], SPEC)))
    format_rows, quant_rows, prepare_rows = [], [], []
    for label, value in images:
        feed = {"image": np.ascontiguousarray(value, dtype=np.float32)}
        outputs = {key: session.run(None, feed)[0] for key, session in sessions.items()}
        prepare_rows.append(format_helpers.compare_outputs(np, outputs["source"], outputs["prepared"], label))
        format_rows.append(format_helpers.compare_outputs(np, outputs["onnx"], outputs["ort"], label))
        lhs, rhs = outputs["source"].astype(np.float64).ravel(), outputs["ort"].astype(np.float64).ravel()
        quant_rows.append({"input": label, "cosine": float(np.dot(lhs, rhs) / (np.linalg.norm(lhs) * np.linalg.norm(rhs))),
                           "max_absolute_error": float(np.max(np.abs(lhs - rhs)))})
    parity = {"status": "passed", "target_platform": "arm", "runtime": ort.__version__,
              "provider": "CPUExecutionProvider", "threads": args.threads, "sample_count": len(images),
              "minimum_cosine": min(row["cosine"] for row in format_rows),
              "maximum_absolute_error": max(row["max_absolute_error"] for row in format_rows),
              "thresholds": {"minimum_cosine": format_helpers.MIN_COSINE,
                             "maximum_absolute_error": format_helpers.MAX_ABSOLUTE_ERROR},
              "samples": format_rows, "input_provenance": provenance}
    quality = {"samples": quant_rows, "minimum_cosine": min(row["cosine"] for row in quant_rows),
               "mean_cosine": float(np.mean([row["cosine"] for row in quant_rows])),
               "scope": "Descriptive quantized-image vs source-FP32 embedding differences; not accuracy or acceptance thresholds."}
    train_rows = [row for row in quant_rows if row["input"].startswith("calibration:")]
    quality["training_subset"] = {"indices": training_indices, "samples": len(train_rows),
                                  "selection": "10 evenly spaced selected calibration indices per dataset, fixed before mixed-candidate inference",
                                  "minimum_cosine": min(row["cosine"] for row in train_rows),
                                  "mean_cosine": float(np.mean([row["cosine"] for row in train_rows]))}
    preprocessing = {"status": "passed", "samples": prepare_rows,
                     "maximum_absolute_error": max(row["max_absolute_error"] for row in prepare_rows)}
    return contracts["source"], parity, quality, preprocessing


def main(argv=None):
    args = args_for(argv)
    import numpy as np
    import onnx
    import onnxruntime as ort
    import torch
    import evaluate_accuracy
    import export_mobileclip2_ort as format_helpers
    from onnxruntime.tools.convert_onnx_models_to_ort import convert_onnx_models_to_ort, OptimizationStyle
    from onnxruntime.tools.ort_format_model import OrtFormatModelProcessor
    import ort_flatbuffers_py.fbs as fbs
    if ort.__version__ != "1.29.0":
        raise ValueError(f"Expected ORT 1.29.0, received {ort.__version__} from {ort.__file__}")
    torch.set_num_threads(args.threads)
    args.output_dir.mkdir(parents=True, exist_ok=True)
    original = artifact(args.source)
    shared_text = {kind: artifact(ROOT / f"app/src/main/assets/mobileclip2_s0_text_int8.{kind}") for kind in ("onnx", "ort")}
    source_fp32_ort = artifact(ROOT / "app/src/main/assets/mobileclip2_s0_image.ort")
    print("Checking train caches and historical evaluation content isolation...", flush=True)
    items, calibration_manifest = prepare_calibration(args, evaluate_accuracy.preprocess)
    with tempfile.TemporaryDirectory(prefix=".quantize-", dir=args.output_dir) as temporary:
        stage = Path(temporary) / "candidate"
        stage.mkdir()
        manifest_path = stage / "calibration_manifest.json"
        save(manifest_path, calibration_manifest)
        print("Preparing shape inference and basic graph optimization...", flush=True)
        prepared = prepare_graph(args.source, stage, args.threads)
        quantized = stage / "mobileclip2_s0_image_int8.onnx"
        quantization = calibrate_and_quantize(prepared, quantized, items, evaluate_accuracy.preprocess, stage,
                                             args.threads, args.keep_sensitive_fp32)
        onnx.checker.check_model(onnx.load(quantized))
        coverage = {"source": graph_summary(args.source), "preprocessed": graph_summary(prepared),
                    "quantized": graph_summary(quantized)}
        quantized_graph = coverage["quantized"]
        if not quantized_graph["operators"].get("QuantizeLinear") or not quantized_graph["initializer_types"].get("INT8"):
            raise ValueError("Candidate graph has no QDQ INT8 quantization")
        coverage["quantized_heavy_ops"] = dict(Counter(row["op_type"] for row in quantized_graph["heavy_ops"]
                                                      if row["both_inputs_quantized"]))
        if not coverage["quantized_heavy_ops"].get("Conv"):
            raise ValueError("Candidate did not quantize convolution activations and weights")
        converted_dir = stage / "converted"
        convert_onnx_models_to_ort(quantized, output_dir=converted_dir,
                                  optimization_styles=[OptimizationStyle.Fixed], target_platform="arm",
                                  enable_type_reduction=True, save_optimized_onnx_model=False)
        converted = converted_dir / "mobileclip2_s0_image_int8.ort"
        coverage["ort"] = format_helpers.ort_graph(converted, "arm", OrtFormatModelProcessor, fbs, onnx, "image")
        if not (coverage["ort"]["initializer_types"].get("INT8") or coverage["ort"]["initializer_types"].get("UINT8")):
            raise ValueError("ORT graph lost INT8 weights")
        io, parity, quality, preparation_parity = verify(args, prepared, quantized, converted, items,
                                                        evaluate_accuracy.preprocess, format_helpers)
        for path in converted_dir.iterdir():
            path.rename(stage / path.name)
        converted_dir.rmdir()
        config = stage / "mobileclip2_s0_image_int8.required_operators_and_types.config"
        if not config.is_file():
            raise ValueError("Missing official required operators config")
        metadata = {
            "status": "passed", "candidate_quality_status": "accuracy_not_validated",
            "model_id": "v2_s0_image_int8_candidate", "onnxruntime_version": ort.__version__,
            "created_at_utc": datetime.now(timezone.utc).isoformat(), "script_sha256": sha(Path(__file__)),
            "source": original, "source_fp32_ort": source_fp32_ort, "shared_text": shared_text,
            "image": {"onnx": artifact(quantized, True), "ort": artifact(stage / converted.name, True)},
            "contract": io, "quantization": quantization, "coverage": coverage,
            "calibration": {"manifest_file": manifest_path.name, "manifest_sha256": sha(manifest_path),
                            "seed": SEED, "samples_total": 400,
                            "datasets": [{"dataset": "cifar100", "split": "train", "samples": 200, "per_class": 2},
                                         {"dataset": "imagenette", "split": "train", "samples": 200, "per_class": 20}],
                            "leakage_check": calibration_manifest["leakage_check"]},
            "preparation": {"steps": ["ORT_ENABLE_BASIC CPU graph optimization (including eligible BN fusion)",
                                        "Official quant_pre_process symbolic and ONNX shape inference; no further optimization"],
                            "artifact": artifact(prepared, True), "parity": preparation_parity},
            "format_conversion": {"tool": "onnxruntime.tools.convert_onnx_models_to_ort", "style": "Fixed",
                                  "target_platform": "arm", "optimization_level": "all", "NchwcTransformer_disabled": True,
                                  "required_operators_config": artifact(config, True)},
            "format_parity": parity, "quantization_cosines": quality,
            "versions": {**evaluate_accuracy.VERSIONS, "onnx": onnx.__version__, "numpy": np.__version__,
                         "torch": torch.__version__, "onnxruntime": ort.__version__},
            "limits": ["Training images only calibrate activation ranges; no fine-tuning or calibration on evaluation data.",
                       "This is a separate candidate. Accuracy, Android parity and speed must be evaluated separately.",
                       "The existing text encoder and all production assets remain unchanged."],
        }
        if sha(args.source) != original["sha256"]:
            raise ValueError("Source FP32 model changed during quantization")
        for item in shared_text.values():
            if sha(Path(item["file"])) != item["sha256"]:
                raise ValueError("Shared text model changed during quantization")
        save(stage / "quantization_metadata.json", metadata)
        filenames = sorted(path.name for path in stage.iterdir() if path.is_file() and path.name != "quantization_metadata.json")
        filenames.append("quantization_metadata.json")
        format_helpers.publish(stage, args.output_dir, filenames)
    print(f"Passed: {args.output_dir / 'quantization_metadata.json'}", flush=True)
    print(json.dumps({"image_bytes": metadata["image"], "coverage": coverage["quantized_heavy_ops"],
                      "format_parity_max_abs": parity["maximum_absolute_error"],
                      "quantization_cosine_min": quality["minimum_cosine"]}, indent=2), flush=True)


if __name__ == "__main__":
    try:
        main()
    except (OSError, RuntimeError, ValueError, KeyError) as error:
        print(f"Image quantization failed: {error}", file=sys.stderr)
        sys.exit(1)
