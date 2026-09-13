"""Portable synthetic report regressions; no models, device or downloads required."""
import copy
import json
import pathlib
import sys
from types import SimpleNamespace
from unittest.mock import patch
import numpy as np


import os
import tempfile
import unittest
from fixture_support import BASE, REPO, make_artifacts


class TestImageInt8Device(unittest.TestCase):
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
        import evaluate_image_int8_device as m


        manifest = m.read(root/'manifest.json')
        host_path = artifacts['host']
        eval_path = artifacts['evaluation']
        host, evaluation = m.read(host_path), m.read(eval_path)
        args = SimpleNamespace(input=root, host=host_path, evaluation_manifest=eval_path, source_input_dir=None,
                               parity=artifacts['parity'])
        original_digest = m.digest
        model_digests = {}
        def cached_models(path):
            if path.suffix=='.ort':
                if str(path) not in model_digests:
                    model_digests[str(path)] = original_digest(path)
                return model_digests[str(path)]
            return original_digest(path)
        m.digest = cached_models
        models,datasets,_ = m.validate_inputs(args,manifest,evaluation,host)
        checks = []
        def write(path,data):
            path.write_text(json.dumps(data,ensure_ascii=False),encoding='utf-8')
        def rejects(name,func):
            try:
                func()
            except (ValueError,AssertionError,KeyError):
                checks.append(name)
            else:
                raise AssertionError('Accepted invalid input: '+name)
        checks.append('Synthetic model manifest and real class prompt alignment')
        def bad_input(name,change):
            value = copy.deepcopy(manifest)
            change(value)
            rejects(name,lambda:m.validate_inputs(args,value,evaluation,host))
        bad_input('Unknown schema',lambda x:x.update(schema_version=2))
        bad_input('Wrong eval manifest hash',lambda x:x.update(evaluation_manifest_sha256='0'*64))
        bad_input('Wrong sample identities hash',lambda x:x.update(evaluation_samples_sha256='0'*64))
        bad_input('Changed host result',lambda x:x.update(host_accuracy_sha256='0'*64))
        bad_input('Old runtime',lambda x:x['versions'].update(onnxruntime='1.25.0'))
        bad_input('Different model hash',lambda x:x['models'][0].update(image_sha256='0'*64))
        bad_input('Duplicate model IDs',lambda x:x['models'].append(copy.deepcopy(x['models'][0])))
        bad_input('Duplicate datasets',lambda x:x['datasets'].append(copy.deepcopy(x['datasets'][0])))
        bad_input('Wrong image count',lambda x:x['datasets'][0].update(image_count=1999))
        bad_input('Different class token order/hash',lambda x:x['datasets'][0].update(tokens_sha256='0'*64))

        reports = {}
        template = m.read(base/'results/image-int8/pixel8a/v2_s0_fp32_image_ort-r1.json')
        for model in m.MODELS:
            report = {k:copy.deepcopy(template[k]) for k in ('manufacturer','sdk','abis','fingerprint')}
            report.update(status='passed',model_id=model,device='SYNTHETIC OFFLINE TEST - NOT DEVICE MEASUREMENTS',
                          runtime_version='1.29.0',cpu_threads=4,inter_op_threads=1,
                          model_hashes_verified=True,input_hashes_verified=True,
                          manifest_sha256=m.digest(root/'manifest.json'),
                          evaluation_manifest_sha256=m.digest(eval_path),model=models[model],datasets=[],
                          actual_image_sha256=models[model]['image_sha256'],actual_text_sha256=models[model]['text_sha256'],
                          output_format='Raw little-endian float32 [count,512], manifest order; no additional normalization.',
                          session_configuration={'execution_provider':'CPUExecutionProvider','intra_op_threads':4,'inter_op_threads':1,
                             'execution_mode':'ORT_SEQUENTIAL (runtime default)','graph_optimization':'runtime default'})
            for tower in ('image','text'):
                report[tower+'_session']={'runtime_version':'1.29.0','load_ms':1,'tensor_contract':template[tower]['tensor_contract']}
            for name,(count,class_count) in m.DATASETS.items():
                labels = np.array([s['label'] for s in evaluation[name]['samples']])
                predicted = labels.copy()
                if model==m.INT8:
                    predicted[:10]=(predicted[:10]+1)%class_count
                images=np.zeros((count,512),dtype='<f4')
                images[np.arange(count),predicted]=3.0
                text=np.zeros((class_count,512),dtype='<f4')
                text[np.arange(class_count),np.arange(class_count)]=2.0
                row=copy.deepcopy(datasets[name])
                for tower,values in (('image',images),('text',text)):
                    file=root/f'{model}-{name}-{tower}.f32'
                    values.tofile(file)
                    row[tower]={'status':'passed','file':file.name,'sha256':m.digest(file),'shape':list(values.shape),
                                'count':len(values),'completed_count':len(values),'bytes':file.stat().st_size,'elapsed_ms':0}
                report['datasets'].append(row)
            reports[model]=report
            write(root/f'{model}-report.json',report)
        run_metadata={'status':'complete','manifest_sha256':m.digest(root/'manifest.json'),
                      'device':{'model':reports[m.FP32]['device'],'sdk':str(reports[m.FP32]['sdk']),
                                'fingerprint':reports[m.FP32]['fingerprint']},'runs':[]}
        for model,report in reports.items():
            receipt=[]
            for row in report['datasets']:
                for tower in ('image','text'):
                    receipt.append({k:row[tower][k] for k in ('file','sha256','bytes','count','shape')})
            run_metadata['runs'].append({'model_id':model,'status':'passed','instrumentation_returncode':0,
                                        'report_file':f'{model}-report.json','report_sha256':m.digest(root/f'{model}-report.json'),
                                        'outputs':receipt})
        write(root/'run-metadata.json',run_metadata)
        result,_=m.evaluate(args)
        for name,(count,_) in m.DATASETS.items():
            row=result['datasets'][name]
            assert row['metrics'][m.FP32]['top1_correct']==count
            assert row['metrics'][m.INT8]['top1_correct']==count-10
            assert row['fp32_int8_prediction_flip_count']==10
            assert np.isclose(row['comparisons'][0]['b_minus_a_top1_pp'],-1000/count)
            for model in m.MODELS:
                item=row['host_device_comparisons'][model]
                assert item['prediction_flip_count']==len(item['prediction_flips'])
        assert result['first_input_parity']['status']=='failed'
        checks.append('Complete synthetic 2x5925 embeddings; normalized Top1 counts and failed parity remain distinct')
        for name,change in [
            ('Run still active',lambda x:x.update(status='running')),
            ('Run refers to different manifest',lambda x:x.update(manifest_sha256='0'*64)),
            ('Missing model run',lambda x:x['runs'].pop()),
            ('Duplicate model run',lambda x:x['runs'].append(copy.deepcopy(x['runs'][0]))),
            ('Failed model run',lambda x:x['runs'][0].update(status='failed')),
            ('Nonzero instrumentation return',lambda x:x['runs'][0].update(instrumentation_returncode=1)),
            ('Stale completed report hash',lambda x:x['runs'][0].update(report_sha256='0'*64)),
            ('Unexpected report file',lambda x:x['runs'][0].update(report_file=f'{m.INT8}-report.json')),
        ]:
            value=copy.deepcopy(run_metadata);change(value);write(root/'run-metadata.json',value)
            try:
                rejects(name,lambda:m.validate_run(root,m.digest(root/'manifest.json')))
            finally:
                write(root/'run-metadata.json',run_metadata)
        for name,change in [
            ('Run device fingerprint mismatch',lambda x:x['device'].update(fingerprint='different')),
            ('Incomplete retrieval receipt',lambda x:x['runs'][0]['outputs'].pop()),
            ('Receipt feature hash mismatch',lambda x:x['runs'][0]['outputs'][0].update(sha256='0'*64)),
        ]:
            value=copy.deepcopy(run_metadata);change(value);write(root/'run-metadata.json',value)
            try:
                rejects(name,lambda:m.evaluate(args))
            finally:
                write(root/'run-metadata.json',run_metadata)
        def validate_one(report):
            write(root/f'{m.INT8}-report.json',report)
            return m.validate_report(root,m.INT8,models[m.INT8],datasets,m.digest(root/'manifest.json'),m.digest(eval_path))
        def bad_report(name,change):
            value=copy.deepcopy(reports[m.INT8]);change(value)
            try:
                rejects(name,lambda:validate_one(value))
            finally:
                write(root/f'{m.INT8}-report.json',reports[m.INT8])
        bad_report('Running export',lambda x:x.update(status='running'))
        bad_report('Wrong model ID',lambda x:x.update(model_id=m.FP32))
        bad_report('Input manifest mismatch',lambda x:x.update(manifest_sha256='0'*64))
        bad_report('Missing runtime verification',lambda x:x.update(runtime_version='1.25.0'))
        bad_report('Wrong intra threads',lambda x:x.update(cpu_threads=1))
        bad_report('Wrong inter threads',lambda x:x.update(inter_op_threads=4))
        bad_report('Model hash verification false',lambda x:x.update(model_hashes_verified=False))
        bad_report('Input verification false',lambda x:x.update(input_hashes_verified=False))
        bad_report('Output format mismatch',lambda x:x.update(output_format='big endian'))
        bad_report('Device ABI mismatch',lambda x:x.update(abis=['x86_64']))
        bad_report('Unsupported device API',lambda x:x.update(sdk=28))
        bad_report('Provider mismatch',lambda x:x['session_configuration'].update(execution_provider='CUDAExecutionProvider'))
        bad_report('Session runtime mismatch',lambda x:x['image_session'].update(runtime_version='1.25.0'))
        bad_report('Tensor contract mismatch',lambda x:x['image_session']['tensor_contract'].update(output_shape=[1,1024]))
        bad_report('Different actual model hash',lambda x:x.update(actual_image_sha256='0'*64))
        bad_report('Missing dataset',lambda x:x['datasets'].pop())
        bad_report('Duplicate dataset',lambda x:x['datasets'].append(copy.deepcopy(x['datasets'][0])))
        bad_report('Wrong input tensor hash',lambda x:x['datasets'][0].update(image_sha256='0'*64))
        bad_report('Tower still running',lambda x:x['datasets'][0]['image'].update(status='running'))
        bad_report('Incomplete processed count',lambda x:x['datasets'][0]['image'].update(completed_count=1999))
        bad_report('Wrong feature shape',lambda x:x['datasets'][0]['image'].update(shape=[2000,511]))
        bad_report('Wrong feature SHA',lambda x:x['datasets'][0]['image'].update(sha256='0'*64))
        bad_report('Nonfinite export timing',lambda x:x['datasets'][0]['image'].update(elapsed_ms=float('nan')))
        supported=copy.deepcopy(reports[m.INT8]);supported['sdk']=29
        try:
            validate_one(supported)
            checks.append('Supported API 29 accepted; SDK 37 is not hardcoded')
        finally:
            write(root/f'{m.INT8}-report.json',reports[m.INT8])

        small=root/'small.f32'
        values=np.zeros((2,512),dtype='<f4');values[:,0]=3
        values.tofile(small)
        record={'status':'passed','file':'small.f32','shape':[2,512],'count':2,'completed_count':2,
                'bytes':4096,'sha256':m.digest(small),'elapsed_ms':1}
        normalized,_=m.load_features(root,record,'small.f32',2)
        assert np.array_equal(normalized[:,0],np.ones(2)) and np.allclose(np.linalg.norm(normalized,axis=1),1)
        checks.append('Raw little-endian features normalized per row')
        for label,value in [('NaN feature',float('nan')),('Infinite feature',float('inf')),('Zero row',0.0)]:
            bad=values.copy();bad[0,0]=value;bad.tofile(small)
            updated={**record,'sha256':m.digest(small)}
            rejects(label,lambda:m.load_features(root,updated,'small.f32',2))
        small.write_bytes(b'x'*4095)
        rejects('Truncated feature bytes',lambda:m.load_features(root,record,'small.f32',2))
        rejects('Artifact traversal',lambda:m.local_file(root,'../manifest.json'))

        with patch.object(sys,'argv',[str(base/'evaluate_image_int8_device.py'),'--input',str(root),'--host',str(host_path),
                                     '--evaluation-manifest',str(eval_path),'--parity',str(args.parity)]):
            m.main()
        written=m.read(root/'device-accuracy.json')
        assert written['datasets']['cifar100']['metrics'][m.INT8]['top1_correct']==1990
        assert 'export duration' in written['scope'].lower()
        assert (root/'README_zh.md').is_file()
        m.validate_bilingual_tables((root/'README.md').read_text(), (root/'README_zh.md').read_text())
        assert written['shared_text_outputs_byte_identical']
        checks.append('CLI writes bilingual reports and independent device JSON without mixing latency')
        bad_text = copy.deepcopy(result['export_reports'])
        bad_text[m.INT8]['outputs']['cifar100']['text']['sha256'] = '0'*64
        rejects('Different shared text output bytes', lambda: m.validate_shared_text_outputs(bad_text))
        write(root/'verification.json',{'status':'passed','checks':checks,'note':'Synthetic features only. Source/model/prompt integrity checked against real manifests; no model inference or device execution.'})
        print(f'Device evaluator: {len(checks)} offline checks passed.')


if __name__ == '__main__':
    unittest.main()
