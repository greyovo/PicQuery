#!/usr/bin/env python3
"""Measure configured deployment pairs sequentially on host CPU."""
from __future__ import annotations

import argparse
import gc
import json
import os
import platform
import time
from datetime import datetime, timezone
from pathlib import Path

import numpy as np
import open_clip
import torch
from PIL import Image

from benchmark_mobileclip2 import DEFAULT_TEXTS, digest, runtime
from evaluate_accuracy import VERSIONS, preprocess


def read_system_file(path):
    try:
        return Path(path).read_text().strip()
    except OSError:
        return None


def measure(path, inputs, threads, warmup, count):
    start = time.perf_counter_ns()
    invoke = runtime(path, threads)
    load_ms = (time.perf_counter_ns() - start) / 1e6
    start = time.perf_counter_ns()
    invoke(inputs[0])
    first_ms = (time.perf_counter_ns() - start) / 1e6
    for i in range(warmup):
        invoke(inputs[i % len(inputs)])
    samples = []
    for i in range(count):
        start = time.perf_counter_ns()
        output = invoke(inputs[i % len(inputs)])
        samples.append((time.perf_counter_ns() - start) / 1e6)
        if output.shape != (1, 512) or not np.isfinite(output).all():
            raise ValueError(f"Invalid embedding from {path}")
    del invoke
    gc.collect()
    return {"session_load_ms": load_ms, "first_invoke_ms": first_ms, "warm_samples_ms": samples}


def summarize(rounds):
    samples = np.array([v for r in rounds for v in r["warm_samples_ms"]])
    return {
        "count": len(samples), "median_ms": float(np.median(samples)),
        "mean_ms": float(samples.mean()), "p95_ms": float(np.percentile(samples, 95)),
        "round_medians_ms": [float(np.median(r["warm_samples_ms"])) for r in rounds],
        "median_session_load_ms": float(np.median([r["session_load_ms"] for r in rounds])),
        "median_first_invoke_ms": float(np.median([r["first_invoke_ms"] for r in rounds])),
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--models", type=Path, default=Path("script/model-MobileCLIP2/accuracy_models.json"))
    parser.add_argument("--fixtures", type=Path, default=Path("build/mobileclip2-fixtures"))
    parser.add_argument("--output", type=Path, default=Path("build/mobileclip-accuracy/speed-comparison.json"))
    parser.add_argument("--threads", type=int, default=4)
    parser.add_argument("--warmup", type=int, default=10)
    parser.add_argument("--rounds", type=int, default=3)
    parser.add_argument("--samples", type=int, default=100)
    args = parser.parse_args()
    if min(args.threads, args.rounds, args.samples) < 1 or args.warmup < 0:
        parser.error("threads, rounds and samples must be positive; warmup nonnegative")
    torch.set_num_threads(args.threads)
    specs = [s for s in json.loads(args.models.read_text(encoding="utf-8-sig")) if "control" not in s["id"]]
    if not specs or len({s["id"] for s in specs}) != len(specs):
        parser.error("The model configuration must contain distinct, nonempty model IDs")
    paths = [args.fixtures / name for name in ("dog1.jpg", "dog2.jpg", "astronaut.jpg", "leaning_tower.jpg", "pottery.jpg")]
    images = []
    for path in paths:
        with Image.open(path) as source:
            images.append(source.convert("RGB"))
    tokens = open_clip.get_tokenizer("MobileCLIP2-S0")(DEFAULT_TEXTS).numpy()
    inputs = {
        s["id"]: {"image": [preprocess(image, s["preprocess"]) for image in images],
                  "text": [t[None] for t in tokens]}
        for s in specs
    }
    report = {
        "created_utc": datetime.now(timezone.utc).isoformat(), "host": platform.platform(),
        "cpu": next((s.split(":", 1)[1].strip() for s in (read_system_file("/proc/cpuinfo") or "").splitlines()
                     if s.startswith("model name")), platform.processor() or os.environ.get("PROCESSOR_IDENTIFIER", "unknown")),
        "versions": VERSIONS, "threads": args.threads, "rounds": args.rounds,
        "warmup_per_tower_per_round": args.warmup, "samples_per_tower_per_round": args.samples,
        "batch_size": 1, "seed": 20260913,
        "scope": "Host CPU inference only; includes runtime input/output copies, excludes image decode/resize, tokenization, database, similarity search and Android overhead.",
        "load_note": "Fresh sessions each round; OS file cache is not flushed. Session-load and first-call values are not cold app startup.",
        "loadavg_start": read_system_file("/proc/loadavg"),
        "models_config_sha256": digest(args.models),
        "fixtures": {p.name: digest(p) for p in paths}, "prompts": DEFAULT_TEXTS,
        "execution_order": [], "models": {},
    }
    for spec in specs:
        report["models"][spec["id"]] = {**spec, "image_sha256": digest(Path(spec["image"])),
                                             "text_sha256": digest(Path(spec["text"])),
                                             "image_bytes": Path(spec["image"]).stat().st_size,
                                             "text_bytes": Path(spec["text"]).stat().st_size,
                                             "format": spec.get("format", Path(spec["image"]).suffix[1:]),
                                             "source_model_id": spec.get("source_model_id", spec["id"]),
                                             "image_rounds": [], "text_rounds": []}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    rng = np.random.default_rng(20260913)
    for round_id in range(args.rounds):
        order = rng.permutation(len(specs)).tolist()
        report["execution_order"].append([specs[i]["id"] for i in order])
        for index in order:
            spec = specs[index]
            entry = report["models"][spec["id"]]
            for tower in ("image", "text"):
                entry[tower + "_rounds"].append(measure(
                    Path(spec[tower]), inputs[spec["id"]][tower], args.threads, args.warmup, args.samples))
                entry[tower + "_summary"] = summarize(entry[tower + "_rounds"])
            print(f"round {round_id + 1}: {spec['id']} image={entry['image_summary']['median_ms']:.2f}ms text={entry['text_summary']['median_ms']:.2f}ms", flush=True)
            args.output.write_text(json.dumps(report, indent=2) + "\n")
    report["loadavg_end"] = read_system_file("/proc/loadavg")
    report["status"] = "complete"
    args.output.write_text(json.dumps(report, indent=2) + "\n")
    lines = ["# v1 / v2 主机速度对比", "", f"CPU：{report['cpu']}；{args.threads} 线程，batch=1。",
             f"每轮先预热 {args.warmup} 次，再记录 {args.samples} 次，共 {args.rounds} 轮；模型顺序按固定种子打乱，同一时间只跑一个模型塔。", "",
             "以下均为纯推理毫秒数，包含运行库输入输出拷贝，不含读图、缩放、分词、数据库和相似度搜索。并非手机端速度。", "",
             "| 模型 | 图像 / 文本 MiB | 图像中位数 / P95 ms | 文本中位数 / P95 ms |", "|---|---:|---:|---:|"]
    for spec in specs:
        entry = report["models"][spec["id"]]
        a, b = entry["image_summary"], entry["text_summary"]
        lines.append(f"| {spec['label']} | {entry['image_bytes']/2**20:.2f} / {entry['text_bytes']/2**20:.2f} | {a['median_ms']:.2f} / {a['p95_ms']:.2f} | {b['median_ms']:.2f} / {b['p95_ms']:.2f} |")
    lines += ["", "加载与首次执行：各轮中位数，单位 ms；文件缓存未清空，不代表冷磁盘或冷 App 启动。", "",
              "| 模型 | 图像加载 | 图像首次执行 | 文本加载 | 文本首次执行 |", "|---|---:|---:|---:|---:|"]
    for spec in specs:
        entry = report["models"][spec["id"]]
        cells = [f"{entry[t + '_summary'][key]:.2f}" for t in ("image", "text")
                 for key in ("median_session_load_ms", "median_first_invoke_ms")]
        lines.append(f"| {spec['label']} | " + " | ".join(cells) + " |")
    lines += ["", "主机运行库与 Android 运行库、CPU 指令集不同，不能把这些比例直接套用到真机。",
              "本测试不包含 Android 测量；同代 ONNX/ORT 比较与跨代模型比较应分别解释。",
              "", f"[原始耗时、模型哈希和运行环境]({args.output.name})。"]
    args.output.with_suffix(".md").write_text("\n".join(lines) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
