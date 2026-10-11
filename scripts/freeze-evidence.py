#!/usr/bin/env python3
"""Seal this chapter's dated execution record after all authored sources settle."""
from pathlib import Path
import hashlib,json,shutil,xml.etree.ElementTree as ET
P=Path(__file__).resolve().parents[1];ROOT=P.parents[1];OUT=P/'evidence/2026-10-06'
def sha(p):
 with p.open('rb') as stream:return hashlib.file_digest(stream,'sha256').hexdigest()
def rel(p): return str(p.relative_to(ROOT))
assert not (OUT/'verification.json').exists(),'A freeze already exists; preserve it before another run.'
run_ids={'fast':'20261006T155817601399Z-fast','device':'20261006T155809506574Z-device','optimized':'20261006T155222548452Z-optimized'}
runs={name:json.loads((P/'evidence/runs'/id/'result.json').read_text()) for name,id in run_ids.items()}
assert all(row['exit_code']==0 and row['gate']['passed'] for row in runs.values())
assert sum(runs['fast']['gate']['observed_suites'].values())==36
assert runs['device']['gate']['observed_suites']=={'staff.android.delivery.DeliveryJourneyTest':1}
host=P/'evidence/host-journeys/20261006T155631357539Z'
h=json.loads((host/'result.json').read_text());assert h['passed'] and len(h['observations'])==12
checkpoint=P/'evidence/checkpoint-apks/20261006T155604016970Z'
c=json.loads((checkpoint/'result.json').read_text());assert c['passed']
for mode,a in h['artifacts'].items():
 assert sha(P/a['retained'])==a['sha256']==a['installed_sha256']==c['artifacts'][mode]['sha256']
assert sha(P/'app/build/outputs/apk/debug/app-debug.apk')==h['artifacts']['fixed']['sha256']==runs['device']['artifact_hashes']['app/build/outputs/apk/debug/app-debug.apk']
assert sha(P/'app/build/outputs/apk/release/app-release.apk')==h['artifacts']['optimized']['sha256']==runs['optimized']['artifact_hashes']['app/build/outputs/apk/release/app-release.apk']
probes=P/'evidence/probes/20261006T155729480815Z/result.json';pr=json.loads(probes.read_text());assert pr['passed'] and len(pr['checks'])==8
counter=P/'evidence/counterexamples/20261006T155507993333Z/result.json';cr=json.loads(counter.read_text());assert cr['passed']
assert [r['counts']['failures'] for r in cr['records']]==[3,7,0]
assert 'Ran 6 tests' in (OUT/'raw/ci-gate-tests.log').read_text()
sources=[]
for p in sorted(P.rglob('*')):
 if not p.is_file() or any(x in p.relative_to(P).parts for x in {'build','.gradle','.kotlin','evidence','__pycache__'}):continue
 if p.name in {'local.properties','.DS_Store'}:continue
 snap=OUT/'source-snapshot'/p.relative_to(P);snap.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(p,snap)
 assert sha(p)==sha(snap)
 sources.append({'path':rel(p),'snapshot':rel(snap),'sha256':sha(p)})
artifacts=[]
for mode in ['broken','fixed','optimized']:
 origin=checkpoint/(mode+'.apk');target=OUT/'artifacts'/(mode+'.apk');target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(origin,target)
 artifacts.append({'path':rel(target),'built_from':rel(origin),'sha256':sha(target),'scope':'final installed '+mode+' checkpoint'})
for name in ['app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk','app/build/outputs/apk/androidTest/release/app-release-androidTest.apk','cart-api/build/libs/cart-api.jar','cart-impl/build/libs/cart-impl.jar','feature-saved/build/libs/feature-saved.jar']:
 origin=P/name;assert origin.is_file(),origin
 target=OUT/'artifacts'/origin.name;shutil.copy2(origin,target)
 artifacts.append({'path':rel(target),'built_from':rel(origin),'sha256':sha(target),'scope':'failed diagnostic test APK' if 'release-androidTest' in name else 'compiled final fixture'})
evidence=[]
for p in sorted((P/'evidence').rglob('*')):
 if not p.is_file() or 'source-snapshot' in p.parts or p.name=='verification.json':continue
 evidence.append({'path':rel(p),'sha256':sha(p)})
result={'status':'PASS','date':'2026-10-06','edition_cutoff':'2026-10-04',
 'authoritative_runs':{k:rel(P/'evidence/runs'/v/'result.json') for k,v in run_ids.items()},
 'host':rel(host/'result.json'),'probes':rel(probes),'counterexamples':rel(counter),
 'reference_checks':{'cart_and_presenter_jvm':22,'queue_contract_jvm':7,'debug_android':1,'total':30},
 'separate_evidence':{'queue_mutation_witnesses':7,'probe_commands':8,'ci_guard_tests':6,'host_paired_captures':12,'two_worker_histories_inside_one_test':50},
 'learner':{'cases':8,'passes':1,'intended_assertion_failures':7,'errors':0,'skipped':0},
 'broken_provider':{'cases':22,'passes':19,'intended_assertion_failures':3,'errors':0,'skipped':0},
 'limitations':['Integrated release instrumentation crashes before discovery; its zero-test results are NOT passes. Optimized acceptance uses the external installed-app driver.','Local host and API36 emulator functional evidence; no hosted CI, physical-device timing, production signing or durable commerce system claim.'],
 'sources':sources,'artifacts':artifacts,'evidence_files':evidence}
(OUT/'verification.json').write_text(json.dumps(result,indent=2)+'\n')
print(json.dumps({'status':'PASS','sources':len(sources),'artifacts':len(artifacts),'evidence_files':len(evidence),'manifest':rel(OUT/'verification.json')}))
