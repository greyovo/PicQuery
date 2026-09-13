#!/usr/bin/env python3
"""Convert the existing MobileCLIP2 ONNX pair to checked CPU ORT artifacts.

No training, export from PyTorch, or quantization is performed. Android defaults
to Fixed/arm; amd64 artifacts can contain host-specific NCHWc optimizations and
must use a separate output directory. See the official converter documentation:
https://onnxruntime.ai/docs/performance/model-optimizations/ort-format-models.html
"""

from __future__ import annotations

import argparse
from collections import Counter
from datetime import datetime, timezone
import hashlib
import importlib.metadata
import json
import os
from pathlib import Path
import platform
import shutil
import sys
import tempfile


ROOT = Path(__file__).resolve().parents[2]
RUNTIME_VERSION = "1.29.0"
MODEL_ID = "v2_s0_onnx_int8"
SOURCES = {
    "image": "mobileclip2_s0_image.onnx",
    "text": "mobileclip2_s0_text_int8.onnx",
}
MIN_COSINE = 0.99999
MAX_ABSOLUTE_ERROR = 0.0001
NORM_TOLERANCE = 0.0001


def parse_args(argv=None):
    parser = argparse.ArgumentParser(
        description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter
    )
    parser.add_argument("--asset-dir", type=Path, default=ROOT / "app/src/main/assets")
    parser.add_argument("--output-dir", type=Path,
                        help="Default: --asset-dir for ARM. amd64 requires a separate directory.")
    parser.add_argument("--target-platform", choices=("arm", "amd64"), default="arm",
                        help="arm: Android, excludes NCHWc; amd64: host-specific CPU export.")
    parser.add_argument("--threads", type=int, default=4, help="CPU intra-op threads for numerical validation.")
    parser.add_argument("--reference-fixture", type=Path,
                        default=ROOT / "app/src/androidTest/assets/mobileclip2/reference_dog_256.json",
                        help="Golden JSON containing 77 CLIP token_ids; no tokenizer dependency.")
    parser.add_argument("--verification-manifest", type=Path,
                        help="Existing device-benchmark manifest: verifies all v2 images and prompt tokens.")
    parser.add_argument("--image-fixture", type=Path, action="append", default=[],
                        help="Additional preprocessed float32 .npy, shape [N,3,256,256], RGB [0,1]; repeatable.")
    args = parser.parse_args(argv)
    if args.threads < 1:
        parser.error("--threads must be positive")
    args.asset_dir = args.asset_dir.resolve()
    if args.target_platform == "amd64" and (
        args.output_dir is None or args.output_dir.resolve() == args.asset_dir
    ):
        parser.error("amd64 requires --output-dir distinct from --asset-dir to preserve Android ARM artifacts")
    args.output_dir = (args.output_dir or args.asset_dir).resolve()
    return args


def dependencies():
    # Keep --help functional even on machines without model conversion packages.
    try:
        import numpy as np
        import onnx
        import onnxruntime as ort
        import flatbuffers
    except ImportError as exc:
        raise RuntimeError(
            "Missing conversion dependency. Install requirements-ort.txt with this Python "
            "(python -m pip install -r script/model-MobileCLIP2/requirements-ort.txt). "
            f"Details: {exc}"
        ) from exc
    if ort.__version__ != RUNTIME_VERSION:
        raise RuntimeError(
            f"Expected onnxruntime {RUNTIME_VERSION} to match Android, found {ort.__version__} "
            f"at {ort.__file__}. Select the correct Python environment/PYTHONPATH."
        )
    from onnxruntime.tools.convert_onnx_models_to_ort import (
        OptimizationStyle,
        convert_onnx_models_to_ort,
    )
    from onnxruntime.tools.ort_format_model import OrtFormatModelProcessor
    import ort_flatbuffers_py.fbs as fbs

    return np, onnx, ort, flatbuffers, OptimizationStyle, convert_onnx_models_to_ort, OrtFormatModelProcessor, fbs


def sha256(path):
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def load_json(path):
    with path.open(encoding="utf-8") as stream:
        return json.load(stream)


def verified_file(base, name, expected_sha):
    path = (base / name).resolve()
    if sha256(path) != expected_sha:
        raise ValueError(f"SHA256 mismatch: {path}")
    return path


def check_image(np, value, label):
    if value.dtype != np.float32 or value.ndim != 4 or list(value.shape[1:]) != [3, 256, 256]:
        raise ValueError(f"{label}: expected float32 [N,3,256,256], received {value.dtype} {value.shape}")
    if value.shape[0] < 1 or not np.isfinite(value).all() or value.min() < 0 or value.max() > 1:
        raise ValueError(f"{label}: images must be finite RGB [0,1] with at least one sample")


def check_tokens(np, value, label):
    if value.ndim != 2 or value.shape[0] < 1 or value.shape[1] != 77 or value.dtype.kind not in "iu":
        raise ValueError(f"{label}: expected integer [N,77] CLIP token IDs")
    if value.min() < 0 or value.max() > 49407:
        raise ValueError(f"{label}: CLIP token IDs must be in [0,49407]")
    for row in value:
        ends = np.flatnonzero(row == 49407)
        if row[0] != 49406 or len(ends) != 1 or ends[0] == 0 or np.any(row[ends[0] + 1:] != 0):
            raise ValueError(f"{label}: expected SOT, one EOT, and zero padding after EOT")


def verification_inputs(args, np, source_hashes):
    rng = np.random.default_rng(20260913)
    images = [
        ("synthetic:black", np.zeros((1, 3, 256, 256), dtype=np.float32)),
        ("synthetic:white", np.ones((1, 3, 256, 256), dtype=np.float32)),
        ("synthetic:noise:seed20260913", rng.random((1, 3, 256, 256), dtype=np.float32)),
    ]
    reference = args.reference_fixture.resolve()
    golden = load_json(reference)
    ids = np.asarray(golden["token_ids"])
    check_tokens(np, ids.reshape(1, -1), str(reference))
    empty = np.zeros((1, 77), dtype=np.int32)
    empty[0, :2] = [49406, 49407]
    long_tokens = np.full((1, 77), 320, dtype=np.int32)
    long_tokens[0, 0], long_tokens[0, -1] = 49406, 49407
    texts = [
        ("golden:" + golden.get("text", "reference"), ids.astype(np.int32).reshape(1, 77)),
        ("synthetic:empty", empty),
        ("synthetic:full_context", long_tokens),
    ]
    provenance = {"golden_reference": {"path": str(reference), "sha256": sha256(reference),
                                        "use": "Exact token IDs only; parity compares the existing quantized ONNX."}}
    for fixture in args.image_fixture:
        value = np.load(fixture, allow_pickle=False)
        check_image(np, value, str(fixture))
        images.extend((f"npy:{fixture.name}:{i}", value[i:i + 1]) for i in range(len(value)))
    provenance["image_fixtures"] = [{"path": str(p.resolve()), "sha256": sha256(p)} for p in args.image_fixture]
    if args.verification_manifest:
        manifest_path = args.verification_manifest.resolve()
        manifest = load_json(manifest_path)
        selected = [model for model in manifest["models"] if model["id"] == MODEL_ID]
        if len(selected) != 1:
            raise ValueError(f"Manifest must contain exactly one {MODEL_ID}")
        model = selected[0]
        for tower in SOURCES:
            if model[tower + "_sha256"] != source_hashes[tower]:
                raise ValueError(f"Manifest {tower} model hash does not match the selected ONNX")
        if model["input_size"] != 256 or model["image_count"] < 1:
            raise ValueError("Manifest requires 256x256 MobileCLIP2 image inputs")
        image_path = verified_file(manifest_path.parent, model["image_input_file"], model["image_input_sha256"])
        image_values = np.fromfile(image_path, dtype="<f4").reshape(model["image_count"], 3, 256, 256)
        check_image(np, image_values, str(image_path))
        images.extend((f"manifest:image:{i}", image_values[i:i + 1]) for i in range(len(image_values)))
        token_path = verified_file(manifest_path.parent, manifest["tokens_file"], manifest["tokens_sha256"])
        token_values = np.fromfile(token_path, dtype="<i4").reshape(manifest["token_count"], 77)
        check_tokens(np, token_values, str(token_path))
        prompts = manifest["prompts"]
        if len(prompts) != len(token_values):
            raise ValueError("Manifest prompt count does not match token_count")
        texts.extend((f"manifest:prompt:{prompt}", token_values[i:i + 1]) for i, prompt in enumerate(prompts))
        provenance["manifest"] = {"path": str(manifest_path), "sha256": sha256(manifest_path),
                                   "image_input_sha256": sha256(image_path), "tokens_sha256": sha256(token_path),
                                   "image_count": len(image_values), "prompt_count": len(token_values)}
    return {"image": images, "text": texts}, provenance


def source_graph(path, onnx, tower):
    model = onnx.load_model(path, load_external_data=False)
    if any(t.data_location == onnx.TensorProto.EXTERNAL for t in model.graph.initializer):
        raise ValueError(f"{path}: expected the self-contained packaged ONNX; external data is unsupported")
    types = Counter(onnx.TensorProto.DataType.Name(t.data_type) for t in model.graph.initializer)
    ops = Counter((node.domain or "ai.onnx") + ":" + node.op_type for node in model.graph.node)
    if tower == "text" and (not types["INT8"] or not ops["ai.onnx:DynamicQuantizeLinear"]):
        raise ValueError("Text input must be the existing dynamic INT8 ONNX, not the FP32 reference")
    if tower == "image" and (not types["FLOAT"] or types["FLOAT16"] or types["INT8"] or types["UINT8"]):
        raise ValueError("Image input must retain the existing FP32 precision")
    return {"initializer_types": dict(sorted(types.items())), "operators": dict(sorted(ops.items()))}


def ort_graph(path, target, processor_type, fbs, onnx, tower):
    with path.open("rb") as stream:
        if stream.read(8)[4:8] != b"ORTM":
            raise ValueError(f"Missing ORTM FlatBuffer file identifier: {path}")
    required_ops = {}
    processor = processor_type(str(path), required_ops, None)
    processor.process()
    if target == "arm" and any("nchwc" in domain.lower() for domain in required_ops):
        raise ValueError("ARM artifact contains x86-specific NCHWc operators")
    # Read the public FlatBuffer schema, not private processor state.
    data = path.read_bytes()
    session = fbs.InferenceSession.InferenceSession.GetRootAsInferenceSession(data, 0)
    graph = session.Model().Graph()
    types = Counter(onnx.TensorProto.DataType.Name(graph.Initializers(i).DataType())
                    for i in range(graph.InitializersLength()))
    if tower == "text" and not (types["INT8"] or types["UINT8"]):
        raise ValueError("ORT text graph lost all INT8/UINT8 initializers; refusing a precision change")
    return {
        "file_identifier": "ORTM", "format_version": session.OrtVersion().decode(),
        "initializer_types": dict(sorted(types.items())),
        "required_operators": {domain: {str(version): sorted(ops) for version, ops in sorted(versions.items())}
                               for domain, versions in sorted(required_ops.items())},
        "contains_nchwc": any("nchwc" in domain.lower() for domain in required_ops),
    }


def cpu_session(path, args, ort):
    options = ort.SessionOptions()
    options.intra_op_num_threads = args.threads
    options.inter_op_num_threads = 1
    options.execution_mode = ort.ExecutionMode.ORT_SEQUENTIAL
    options.graph_optimization_level = ort.GraphOptimizationLevel.ORT_ENABLE_ALL
    options.add_session_config_entry("session.qdqisint8allowed", "1" if args.target_platform == "arm" else "0")
    return ort.InferenceSession(
        str(path), sess_options=options, providers=["CPUExecutionProvider"],
        disabled_optimizers=["NchwcTransformer"] if args.target_platform == "arm" else [],
    )


def contract(session, tower):
    inputs, outputs = session.get_inputs(), session.get_outputs()
    if len(inputs) != 1 or len(outputs) != 1:
        raise ValueError(f"{tower}: expected exactly one input and one output")
    inp, out = inputs[0], outputs[0]
    shape = [1, 3, 256, 256] if tower == "image" else [1, 77]
    types = ["tensor(float)"] if tower == "image" else ["tensor(int32)", "tensor(int64)"]
    if inp.shape != shape or inp.type not in types or out.shape != [1, 512] or out.type != "tensor(float)":
        raise ValueError(f"{tower}: unexpected input/output contract: {inp.shape} {inp.type}, {out.shape} {out.type}")
    return {"input": {"name": inp.name, "shape": inp.shape, "type": inp.type},
            "output": {"name": out.name, "shape": out.shape, "type": out.type, "l2_normalized": True}}


def compare_outputs(np, reference, actual, label):
    for backend, vector in (("onnx", reference), ("ort", actual)):
        if vector.dtype != np.float32 or vector.shape != (1, 512) or not np.isfinite(vector).all():
            raise ValueError(f"{label} {backend}: expected finite float32 [1,512] output")
        norm = float(np.linalg.norm(vector.astype(np.float64)))
        if abs(norm - 1.0) > NORM_TOLERANCE:
            raise ValueError(f"{label} {backend}: embedding norm {norm} is not one")
    lhs, rhs = reference.astype(np.float64).ravel(), actual.astype(np.float64).ravel()
    cosine = float(np.clip(np.dot(lhs, rhs) / (np.linalg.norm(lhs) * np.linalg.norm(rhs)), -1, 1))
    max_abs = float(np.max(np.abs(lhs - rhs)))
    if cosine < MIN_COSINE or max_abs > MAX_ABSOLUTE_ERROR:
        raise ValueError(f"{label}: ONNX/ORT parity failed: cosine={cosine:.10f}, max_abs={max_abs:.8g}. "
                         "Investigate target-specific optimizations/kernels; do not re-quantize or lower tolerances.")
    return {"input": label, "cosine": cosine, "max_absolute_error": max_abs,
            "onnx_norm": float(np.linalg.norm(lhs)), "ort_norm": float(np.linalg.norm(rhs))}


def verify_pair(source, output, tower, samples, args, np, ort):
    reference, actual = cpu_session(source, args, ort), cpu_session(output, args, ort)
    input_contract = contract(reference, tower)
    if contract(actual, tower) != input_contract:
        raise ValueError(f"{tower}: ORT changed the ONNX input/output contract")
    dtype = {"tensor(float)": np.float32, "tensor(int32)": np.int32, "tensor(int64)": np.int64}[
        input_contract["input"]["type"]
    ]
    records = []
    for label, values in samples:
        feed = {input_contract["input"]["name"]: np.ascontiguousarray(values, dtype=dtype)}
        records.append(compare_outputs(np, reference.run(None, feed)[0], actual.run(None, feed)[0], label))
    return input_contract, {"status": "passed", "sample_count": len(records), "samples": records,
                            "minimum_cosine": min(row["cosine"] for row in records),
                            "maximum_absolute_error": max(row["max_absolute_error"] for row in records)}


def publish(staged, output_dir, filenames):
    """Replace only this export's checked files, rolling back ordinary I/O errors."""
    backup = staged.parent / "backup"
    backup.mkdir()
    replaced, preserved = [], []
    try:
        for name in filenames:
            destination = output_dir / name
            if destination.is_symlink() or (destination.exists() and not destination.is_file()):
                raise ValueError(f"Refusing to replace non-regular output: {destination}")
            if destination.exists():
                os.replace(destination, backup / name)
                preserved.append(name)
            os.replace(staged / name, destination)
            replaced.append(name)
    except BaseException:
        for name in reversed(replaced):
            (output_dir / name).unlink()
        for name in preserved:
            os.replace(backup / name, output_dir / name)
        raise


def main(argv=None):
    args = parse_args(argv)
    np, onnx, ort, flatbuffers, style, convert, processor, fbs = dependencies()
    override = os.environ.get("ORT_CONVERT_ONNX_MODELS_TO_ORT_OPTIMIZATION_LEVEL", "all")
    if override != "all":
        raise ValueError("Unset ORT_CONVERT_ONNX_MODELS_TO_ORT_OPTIMIZATION_LEVEL; this export requires Fixed/all")
    source_paths = {tower: args.asset_dir / name for tower, name in SOURCES.items()}
    source_hashes = {tower: sha256(path) for tower, path in source_paths.items()}
    graphs = {tower: source_graph(path, onnx, tower) for tower, path in source_paths.items()}
    samples, provenance = verification_inputs(args, np, source_hashes)
    args.output_dir.mkdir(parents=True, exist_ok=True)
    metadata = {
        "status": "passed", "source_model_id": MODEL_ID, "onnxruntime_version": ort.__version__,
        "created_at_utc": datetime.now(timezone.utc).isoformat(), "target_platform": args.target_platform,
        "optimization_style": "Fixed", "optimization_level": "all", "execution_provider": "CPUExecutionProvider",
        "precision": {"image": "FP32", "text": "existing dynamic INT8 weights, FP32 embeddings"},
        "quantization_changed": False,
        "portability": "ARM CPU target; Android validation is separate." if args.target_platform == "arm" else
                       "Host-specific amd64 CPU artifact; do not deploy to Android ARM.",
        "conversion_tool": "onnxruntime.tools.convert_onnx_models_to_ort",
        "conversion_tool_sha256": sha256(Path(sys.modules[convert.__module__].__file__)),
        "host": {"system": platform.system(), "machine": platform.machine(), "python": platform.python_version()},
        "package_versions": {"onnxruntime": ort.__version__, "onnx": onnx.__version__, "numpy": np.__version__,
                             "flatbuffers": flatbuffers.__version__, "protobuf": importlib.metadata.version("protobuf")},
        "verification": {"provider": "CPUExecutionProvider", "intra_op_threads": args.threads, "inter_op_threads": 1,
                         "execution_mode": "ORT_SEQUENTIAL", "graph_optimization_level": "ORT_ENABLE_ALL",
                         "disabled_optimizers": ["NchwcTransformer"] if args.target_platform == "arm" else [],
                         "session.qdqisint8allowed": "1" if args.target_platform == "arm" else "0",
                         "minimum_cosine": MIN_COSINE, "maximum_absolute_error": MAX_ABSOLUTE_ERROR,
                         "normalization_tolerance": NORM_TOLERANCE, "fixtures": provenance,
                         "scope": "Same CPU runtime and target options, ONNX versus ORT format; not Android parity or accuracy."},
    }
    with tempfile.TemporaryDirectory(prefix=".mobileclip2-ort-", dir=args.output_dir) as temporary:
        stage = Path(temporary)
        sources, converted = stage / "sources", stage / "converted"
        sources.mkdir()
        for tower, path in source_paths.items():
            shutil.copyfile(path, sources / path.name)
            if sha256(sources / path.name) != source_hashes[tower]:
                raise ValueError(f"Source changed while copying: {path}")
        convert(sources, output_dir=converted, optimization_styles=[style.Fixed],
                target_platform=args.target_platform, enable_type_reduction=True,
                save_optimized_onnx_model=False, allow_conversion_failures=False)
        filenames = []
        for tower, path in source_paths.items():
            output = converted / path.with_suffix(".ort").name
            graph = ort_graph(output, args.target_platform, processor, fbs, onnx, tower)
            io, parity = verify_pair(sources / path.name, output, tower, samples[tower], args, np, ort)
            metadata[tower] = {"file": output.name, "sha256": sha256(output), "bytes": output.stat().st_size,
                               "source_file": path.name, "source_sha256": source_hashes[tower],
                               "source_bytes": path.stat().st_size, "contract": io,
                               "source_graph": graphs[tower], "ort_graph": graph, "parity": parity}
            filenames.append(output.name)
            print(f"{tower}: {parity['sample_count']} inputs passed, cosine >= {parity['minimum_cosine']:.10f}, "
                  f"max_abs <= {parity['maximum_absolute_error']:.8g}", flush=True)
        config = converted / "required_operators_and_types.config"
        if not config.is_file() or not config.stat().st_size:
            raise ValueError("Official converter did not produce the required-operators config")
        # Avoid replacing a generic config belonging to another model export.
        owned_config = converted / "mobileclip2_required_operators_and_types.config"
        config.rename(owned_config)
        metadata["required_operators_config"] = {"file": owned_config.name, "sha256": sha256(owned_config),
                                                  "type_reduction": True,
                                                  "use": "For reduced ORT builds; the full Android package does not consume it."}
        filenames.append(owned_config.name)
        for tower, path in source_paths.items():
            if sha256(path) != source_hashes[tower]:
                raise ValueError(f"Original ONNX changed during conversion: {path}")
        manifest_name = "mobileclip2_ort_metadata.json"
        (converted / manifest_name).write_text(json.dumps(metadata, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
        filenames.append(manifest_name)
        publish(converted, args.output_dir, filenames)
    print(f"Passed; wrote {args.output_dir / manifest_name}")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (ImportError, OSError, ValueError, RuntimeError, KeyError) as error:
        print(f"ORT export failed: {error}", file=sys.stderr)
        sys.exit(1)
