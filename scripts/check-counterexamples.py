#!/usr/bin/env python3
"""Require deliberate assertion failures, preserve reports, then restore reference."""
from pathlib import Path
import datetime, json, shutil, subprocess, xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[1]
stamp=datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')
OUT=ROOT/'evidence/counterexamples'/stamp;OUT.mkdir(parents=True)
records=[]
def run(name,args,folder,expected_tests,expected_failures):
 destination=OUT/name;destination.mkdir()
 command=[str(ROOT/'gradlew'),*args,'--offline','--console=plain']
 with (destination/'gradle.log').open('w') as log:
  result=subprocess.run(command,cwd=ROOT,stdout=log,stderr=subprocess.STDOUT)
 source=ROOT/folder
 shutil.copytree(source,destination/'reports')
 counts={'tests':0,'failures':0,'errors':0,'skipped':0};failed=[];passed=[]
 for path in (destination/'reports').glob('TEST-*.xml'):
  root=ET.parse(path).getroot()
  for key in counts: counts[key]+=int(root.attrib.get(key,0))
  for case in root.findall('testcase'):
   failure=case.find('failure')
   if failure is not None:
    assert failure.attrib.get('type') in {'org.opentest4j.AssertionFailedError','java.lang.AssertionError'},failure.attrib
    failed.append(case.attrib['name'])
   elif case.find('error') is None: passed.append(case.attrib['name'])
 expected={'tests':expected_tests,'failures':expected_failures,'errors':0,'skipped':0}
 assert counts==expected,(name,counts,expected)
 assert (result.returncode==0)==(expected_failures==0),(name,result.returncode)
 if name=='broken-provider':
  assert set(failed)=={'real/preservesRejectedState','real/rejectionDoesNotPublish','featureAndRealProviderAgree()'},failed
  assert 'programmedRepliesCanHideProviderDefect()' in passed
 record={'name':name,'command':command,'exit_code':result.returncode,'counts':counts,'failed_assertions':failed,'passed_cases':passed}
 records.append(record);return record
try:
 run('broken-provider',[':contract-tests:test','-PcartProvider=broken'],'contract-tests/build/test-results/test',22,3)
 run('learner',[':test-lab:exerciseTest'],'test-lab/build/test-results/exerciseTest',8,7)
finally:
 run('restored-reference',[':contract-tests:test',':test-lab:test'],'contract-tests/build/test-results/test',22,0)
 (OUT/'result.json').write_text(json.dumps({'passed':len(records)==3,'records':records},indent=2)+'\n')
 print(OUT)
