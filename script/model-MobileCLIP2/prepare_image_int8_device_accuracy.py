#!/usr/bin/env python3
"""Prepare the exact host evaluation inputs for streaming Android inference."""
import argparse
import hashlib
import json
from pathlib import Path

import numpy as np
import open_clip

from evaluate_accuracy import datasets, digest, json_hash, preprocess, VERSIONS


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, default=Path('build/mobileclip2-image-int8-device-accuracy'))
    parser.add_argument('--data-root', type=Path, default=Path('build/mobileclip-accuracy-data'))
    parser.add_argument('--accuracy', type=Path, default=Path('build/mobileclip2-image-int8-results/accuracy.json'))
    args = parser.parse_args()
    accuracy = json.loads(args.accuracy.read_text(encoding='utf-8'))
    manifest_path = args.accuracy.with_name(args.accuracy.stem + '-manifest.json')
    expected = json.loads(manifest_path.read_text(encoding='utf-8'))
    if accuracy['versions']['onnxruntime'] != '1.29.0' or VERSIONS['onnxruntime'] != '1.29.0':
        raise ValueError('Use the ORT 1.29.0 evaluation environment')
    if accuracy['manifest_sha256'] != json_hash(expected):
        raise ValueError('Host accuracy manifest mismatch')
    model_ids = ['v2_s0_fp32_image_ort', 'v2_s0_int8_image_ort']
    configs = [accuracy['models'][model_id] for model_id in model_ids]
    spec = configs[0]['preprocess']
    if configs[1]['preprocess'] != spec or spec['size'] != 256:
        raise ValueError('Expected identical 256px preprocessing')
    if configs[0]['text_sha256'] != configs[1]['text_sha256']:
        raise ValueError('Expected shared text model')
    bundles = datasets(args.data_root, ['cifar100', 'imagenette'], False, 20)
    args.output.mkdir(parents=True, exist_ok=True)
    result = {'schema_version': 1, 'versions': VERSIONS, 'preprocess': spec,
              'evaluation_manifest_sha256': digest(manifest_path),
              'evaluation_samples_sha256': json_hash(expected),
              'host_accuracy_sha256': digest(args.accuracy), 'models': [], 'datasets': []}
    for cfg in configs:
        row = {'id': cfg['id']}
        for tower in ('image', 'text'):
            path = Path(cfg[tower])
            if digest(path) != cfg[tower + '_sha256']:
                raise ValueError(f'Model changed: {path}')
            row[tower + '_file'] = path.name
            row[tower + '_sha256'] = cfg[tower + '_sha256']
        result['models'].append(row)
    tokenizer = open_clip.get_tokenizer('MobileCLIP2-S0')
    uploads = []
    for name, (dataset, indices, _, manifest) in bundles.items():
        if manifest != expected[name]:
            raise ValueError(f'Input selection differs from host: {name}')
        image_path = args.output / f'{name}-images.f32'
        checksum = hashlib.sha256()
        with image_path.open('wb') as stream:
            for position, index in enumerate(indices):
                value = np.ascontiguousarray(preprocess(dataset[index][0], spec), dtype='<f4')
                if value.shape != (1, 3, 256, 256) or not np.isfinite(value).all():
                    raise ValueError(f'Invalid input: {name}/{index}')
                raw = value.tobytes()
                stream.write(raw)
                checksum.update(raw)
                if (position + 1) % 500 == 0:
                    print(f'{name}: {position + 1}/{len(indices)} exact float32 inputs', flush=True)
        token_path = args.output / f'{name}-tokens.i32'
        tokens = tokenizer(manifest['prompts']).numpy().astype('<i4')
        if tokens.shape != (len(manifest['classes']), 77):
            raise ValueError('Invalid token shape')
        token_path.write_bytes(tokens.tobytes())
        result['datasets'].append({'id': name, 'image_file': image_path.name,
                                   'image_sha256': checksum.hexdigest(), 'image_count': len(indices),
                                   'tokens_file': token_path.name, 'tokens_sha256': digest(token_path),
                                   'token_count': len(tokens)})
        uploads += [{'source': str(path), 'destination': path.name} for path in (image_path, token_path)]
    output_manifest = args.output / 'manifest.json'
    output_manifest.write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
    uploads.append({'source': str(output_manifest), 'destination': output_manifest.name})
    (args.output / 'upload-list.json').write_text(json.dumps(uploads, indent=2) + '\n', encoding='utf-8')
    print(f'Prepared all 5925 inputs and 110 prompts: {output_manifest}', flush=True)


if __name__ == '__main__':
    main()
