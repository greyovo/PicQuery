"""Portable synthetic report regressions; no models, device or downloads required."""
import ast, contextlib, copy, functools, importlib.util, io, json, pathlib, sys
from unittest.mock import patch
import numpy as np


import os
import tempfile
import unittest
from fixture_support import BASE, REPO, make_artifacts


class TestImageInt8Report(unittest.TestCase):
    def test_synthetic_artifact_contracts(self):
        previous_cwd = pathlib.Path.cwd()
        os.chdir(REPO)
        self.addCleanup(os.chdir, previous_cwd)
        temporary = tempfile.TemporaryDirectory(prefix='mobileclip-report-')
        self.addCleanup(temporary.cleanup)
        root = pathlib.Path(temporary.name)
        artifacts = make_artifacts(root)
        base = BASE
        sys.path.insert(0, str(base.resolve()))
        spec = importlib.util.spec_from_file_location('image_report', base/'render_image_int8_report.py')
        m = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(m)
        m.digest = functools.lru_cache(maxsize=None)(m.digest)

        old = m.read(base/'results/accuracy-results.json')
        manifest = m.read(base/'results/accuracy-results-manifest.json')
        configs = list(m.read(artifacts['host'])['models'].values())
        source_ids = {m.FP32:'v2_s0_onnx_int8', m.INT8:'v1_s0_onnx_int8', m.LEGACY:'legacy_clip_int8'}
        data = {k:copy.deepcopy(old[k]) for k in ('protocol','seed','host','threads','versions','manifest_sha256','prompt_template','limits')}
        data['versions']['onnxruntime'] = '1.29.0'
        data.update(models={}, datasets={}, host='SYNTHETIC OFFLINE TEST - NOT MEASUREMENTS')
        for cfg in configs:
            row = copy.deepcopy(cfg)
            for t in ('image','text'):
                row[t+'_sha256'] = m.digest(pathlib.Path(row[t]))
            row['total_bytes'] = sum(pathlib.Path(row[t]).stat().st_size for t in ('image','text'))
            data['models'][row['id']] = row
        tree = ast.parse((base/'evaluate_accuracy.py').read_text())
        fn = next(n for n in tree.body if isinstance(n,ast.FunctionDef) and n.name=='comparisons')
        ns = {'np':np, 'SEED':20260913}
        exec(compile(ast.Module(body=[fn],type_ignores=[]),'comparisons','exec'),ns)
        for name in manifest:
            rows = {model:copy.deepcopy(old['datasets'][name]['metrics'][source_ids[model]]) for model in m.MODELS}
            labels = np.array([s['label'] for s in manifest[name]['samples']])
            data['datasets'][name] = {'metrics':rows, 'comparisons':ns['comparisons'](rows,labels)}
        checks = []
        def rejects(name, fn):
            try:
                fn()
            except (ValueError, AssertionError, KeyError):
                checks.append(name)
            else:
                raise AssertionError('Accepted invalid input: '+name)
        def bad_accuracy(name, change):
            value = copy.deepcopy(data)
            change(value)
            rejects(name, lambda:m.validate_accuracy(value,manifest))
        valid = m.validate_accuracy(data,manifest)
        checks.append('Complete accuracy metrics and paired CI reconstruction')
        bad_accuracy('Old runtime',lambda x:x['versions'].update(onnxruntime='1.25.0'))
        bad_accuracy('Manifest hash',lambda x:x.update(manifest_sha256='0'*64))
        bad_accuracy('Wrong threads',lambda x:x.update(threads=1))
        bad_accuracy('Wrong image hash',lambda x:x['models'][m.INT8].update(image_sha256='0'*64))
        bad_accuracy('Different preprocess',lambda x:x['models'][m.INT8]['preprocess'].update(interpolation='bicubic'))
        def change_text(x):
            other = x['models'][m.LEGACY]
            x['models'][m.INT8].update(text=other['text'],text_sha256=other['text_sha256'])
            x['models'][m.INT8]['total_bytes'] = sum(pathlib.Path(x['models'][m.INT8][t]).stat().st_size for t in ('image','text'))
        bad_accuracy('Different shared text',change_text)
        bad_accuracy('Partial model metrics',lambda x:x['datasets']['cifar100']['metrics'].pop(m.INT8))
        bad_accuracy('Top1 corruption',lambda x:x['datasets']['cifar100']['metrics'][m.INT8].update(top1_correct=1))
        bad_accuracy('Top5 corruption',lambda x:x['datasets']['cifar100']['metrics'][m.INT8].update(top5=1.01))
        bad_accuracy('Class support corruption',lambda x:x['datasets']['cifar100']['metrics'][m.INT8]['per_class'][0].update(support=19))
        bad_accuracy('Retrieval aggregate corruption',lambda x:x['datasets']['cifar100']['metrics'][m.INT8].update(class_query_map=0))
        bad_accuracy('Paired count corruption',lambda x:x['datasets']['cifar100']['comparisons'][0].update(both_correct=1))
        bad_accuracy('Paired CI corruption',lambda x:x['datasets']['cifar100']['comparisons'][0].update(paired_bootstrap_95ci_pp=[-99,99]))
        bad_accuracy('Incomplete comparisons',lambda x:x['datasets']['cifar100']['comparisons'].pop())
        quant_path = artifacts['quantization']
        quant = m.read(quant_path)
        m.validate_quantization(quant,quant_path,data,manifest,valid['sizes_bytes'])
        checks.append('Training calibration provenance checks; bad cosine is diagnostic only')
        def bad_quant(name,change):
            value = copy.deepcopy(quant)
            change(value)
            rejects(name,lambda:m.validate_quantization(value,quant_path,data,manifest,valid['sizes_bytes']))
        bad_quant('Failed quant export',lambda x:x.update(status='failed'))
        bad_quant('Wrong FP32 source hash',lambda x:x['source_fp32_ort'].update(sha256='0'*64))
        bad_quant('Wrong quant ORT hash',lambda x:x['image']['ort'].update(sha256='0'*64))
        bad_quant('Reported calibration overlap',lambda x:x['calibration']['leakage_check'].update(native_sha256_overlap=1))
        bad_quant('Calibration manifest hash',lambda x:x['calibration'].update(manifest_sha256='0'*64))
        bad_quant('No INT8 weight coverage',lambda x:x['coverage']['quantized']['initializer_elements'].update(INT8=0))
        bad_quant('INT8 operator count mismatch',lambda x:x['coverage']['quantized_heavy_ops'].update(Conv=-1))
        mixed = copy.deepcopy(quant)
        node = next(r for r in mixed['coverage']['quantized']['heavy_ops'] if r['both_inputs_quantized'])
        node.update(both_inputs_quantized=False,int8_dequantized_inputs=0)
        mixed['coverage']['quantized_heavy_ops'][node['op_type']] -= 1
        mixed_result = m.validate_quantization(mixed,quant_path,data,manifest,valid['sizes_bytes'])
        assert any(r['name']==node['name'] for r in mixed_result['float_or_partial_nodes'])
        checks.append('Mixed INT8 and retained FP32 operators are accepted and listed')
        def write(path,value):
            path.write_text(json.dumps(value,ensure_ascii=False),encoding='utf-8')
        calib = m.read(quant_path.parent/'calibration_manifest.json')
        for index,(name,change) in enumerate([
            ('Non-train calibration',lambda x:x['samples'][0].update(split='val')),
            ('Native calibration/eval overlap',lambda x:x['samples'][0].update(sha256=manifest['cifar100']['samples'][0]['sha256'])),
            ('Duplicate calibration IDs',lambda x:x['samples'].__setitem__(1,copy.deepcopy(x['samples'][0]))),
            ('Calibration preprocessing differs',lambda x:x['preprocess']['spec'].update(interpolation='bicubic')),
        ]):
            value = copy.deepcopy(calib)
            change(value)
            cp = (root/f'bad-calibration-{index}.json').resolve()
            write(cp,value)
            value = copy.deepcopy(quant)
            value['calibration'].update(manifest_file=str(cp),manifest_sha256=m.digest(cp))
            rejects(name,lambda:m.validate_quantization(value,quant_path,data,manifest,valid['sizes_bytes']))
        device_root = root/'synthetic-device'
        device_root.mkdir(exist_ok=True)
        previous = base/'results/ort-comparison/pixel8a'
        metadata = m.read(previous/'run-metadata.json')
        metadata.update(model='SYNTHETIC OFFLINE TEST',runs=[])
        fixture = m.read(previous/'fixture-manifest.json')
        old_by_id = {r['id']:r for r in fixture['models']}
        fixture['models'] = []
        map_device = {m.FP32:'v2_s0_ort_int8',m.INT8:'v2_s0_ort_int8',m.LEGACY:'legacy_clip_int8'}
        for model in m.MODELS:
            item = copy.deepcopy(old_by_id[map_device[model]])
            item.update(id=model,label=m.LABELS[model],source_model_id=model)
            for t in ('image','text'):
                item.update({t+'_file':pathlib.Path(data['models'][model][t]).name,
                             t+'_sha256':data['models'][model][t+'_sha256'],
                             t+'_file_bytes':valid['sizes_bytes'][model][t]})
            fixture['models'].append(item)
        rows = {}
        for round_no in (1,2):
            for index,model in enumerate(m.MODELS if round_no==1 else m.MODELS[::-1]):
                row = m.read(previous/f'{map_device[model]}-r{round_no}.json')
                row.update(model_id=model)
                for t in ('image','text'):
                    row['actual_'+t+'_sha256'] = data['models'][model][t+'_sha256']
                    row[t+'_file_bytes'] = valid['sizes_bytes'][model][t]
                    samples = [10.0+index+v/100 for v in range(100)]
                    row[t].update(raw_samples_ms=samples,summary=m.stats(samples))
                if model==m.INT8:
                    row['image']['first_output'] = [1.0]+[0.0]*511
                filename = f'{model}-r{round_no}.json'
                rows[filename] = row
                metadata['runs'].append({'model':model,'round':round_no,'report':filename})
        write(device_root/'run-metadata.json',metadata)
        write(device_root/'fixture-manifest.json',fixture)
        for fn,row in rows.items():
            write(device_root/fn,row)
        device = m.validate_device(device_root,data,valid['sizes_bytes'])
        assert device['raw_sample_count']==1200
        assert device['models'][m.LEGACY]['image']['first_output_norm'][0]>1
        checks.append('1200 device samples; unequal INT8 vectors and legacy norms accepted')
        filename = f'{m.INT8}-r1.json'
        def bad_device(name,change):
            value = copy.deepcopy(rows[filename])
            change(value)
            write(device_root/filename,value)
            try:
                rejects(name,lambda:m.validate_device(device_root,data,valid['sizes_bytes']))
            finally:
                write(device_root/filename,rows[filename])
        bad_device('Failed device run',lambda x:x.update(status='failed'))
        bad_device('Old device runtime',lambda x:x.update(runtime_version='1.25.0'))
        bad_device('Device threads',lambda x:x.update(cpu_threads=1))
        bad_device('Device model hash',lambda x:x.update(actual_image_sha256='0'*64))
        bad_device('Device input hash',lambda x:x.update(image_input_sha256='0'*64))
        bad_device('Device sample count',lambda x:x.update(sample_count=99))
        bad_device('Missing device sample',lambda x:x['image']['raw_samples_ms'].pop())
        bad_device('Device percentile',lambda x:x['image']['summary'].update(p95_ms=900))
        bad_device('Nonfinite device output',lambda x:x['image']['first_output'].__setitem__(0,float('nan')))
        bad_device('Zero device output',lambda x:x['image'].update(first_output=[0.0]*512))
        bad_device('Device output dimension',lambda x:x['image']['first_output'].pop())
        bad_device('MobileCLIP nonunit output',lambda x:x['image']['first_output'].__setitem__(0,3.0))
        for name,change in [
            ('Missing device round',lambda x:x['runs'].pop()),
            ('Duplicate device round',lambda x:x['runs'].append(copy.deepcopy(x['runs'][0]))),
            ('Non-reversed device order',lambda x:x['runs'].__setitem__(slice(3,6),x['runs'][3:6][::-1])),
        ]:
            value = copy.deepcopy(metadata)
            change(value)
            write(device_root/'run-metadata.json',value)
            try:
                rejects(name,lambda:m.validate_device(device_root,data,valid['sizes_bytes']))
            finally:
                write(device_root/'run-metadata.json',metadata)
        write(root/'accuracy.json',data)
        write(root/'accuracy-manifest.json',manifest)
        for with_device in (True,False):
            output = root/('README.md' if with_device else 'README-without-device.md')
            argv = [str(base/'render_image_int8_report.py'),'--accuracy',str(root/'accuracy.json'),
                    '--quantization',str(quant_path),'--output',str(output)]
            if with_device:
                argv += ['--device-dir',str(device_root)]
            with patch.object(sys,'argv',argv),contextlib.redirect_stdout(io.StringIO()):
                m.main()
            verified = m.read(output.with_name(output.stem+'-validation.json'))
            assert verified['status']=='passed' and 'quality' in verified['status_scope']
            assert ('device' in verified)==with_device
            assert output.with_name(output.stem+'_zh.md').is_file()
            m.validate_bilingual_tables(output.read_text(), output.with_name(output.stem+'_zh.md').read_text())
        checks.append('CLI writes matched bilingual reports and sidecar with and without device')
        rejects('Bilingual table drift', lambda: m.validate_bilingual_tables('| 75.30% |', '| 75.31% |'))
        write(root/'verification.json',{'status':'passed','checks':checks,
              'note':'Synthetic JSON and existing artifacts only; no model or device execution. Synthetic reports are not measurement evidence.'})
        print(f'Report validator: {len(checks)} offline checks passed.')


if __name__ == '__main__':
    unittest.main()
