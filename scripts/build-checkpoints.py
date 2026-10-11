#!/usr/bin/env python3
from pathlib import Path
import datetime, hashlib, json, shutil, subprocess
ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'evidence/checkpoint-apks'/datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%S%fZ');OUT.mkdir(parents=True)
rows={}
for mode,args,source in [
 ('broken',[':app:assembleDebug','-PcartProvider=broken'],'app/build/outputs/apk/debug/app-debug.apk'),
 ('fixed',[':app:assembleDebug'],'app/build/outputs/apk/debug/app-debug.apk'),
 ('optimized',[':app:assembleRelease'],'app/build/outputs/apk/release/app-release.apk'),
]:
 with (OUT/(mode+'.log')).open('w') as log:
  subprocess.run([str(ROOT/'gradlew'),*args,'--offline','--console=plain'],cwd=ROOT,stdout=log,stderr=subprocess.STDOUT,check=True)
 apk=OUT/(mode+'.apk');shutil.copy2(ROOT/source,apk)
 rows[mode]={'source':source,'retained':str(apk.relative_to(ROOT)),'sha256':hashlib.sha256(apk.read_bytes()).hexdigest(),'arguments':args}
(OUT/'result.json').write_text(json.dumps({'passed':True,'artifacts':rows},indent=2)+'\n')
print(OUT)
