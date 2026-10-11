#!/usr/bin/env python3
"""Capture the same visible history from three explicit retained APKs."""
from pathlib import Path
from fresh_android_xml import read_ui_xml
from common_goods_ui import CommonGoodsUi
import argparse, datetime, hashlib, json, os, re, shutil, subprocess, time
import xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[1]
PACKAGE='staff.android.delivery'
SDK=Path(os.environ.get('ANDROID_HOME',str(Path.home()/'Library/Android/sdk')))
ADB=SDK/'platform-tools/adb'
parser=argparse.ArgumentParser()
parser.add_argument('--fixed',type=Path)
parser.add_argument('--broken',type=Path)
parser.add_argument('--optimized',type=Path,required=True)
parser.add_argument('--optimized-only',action='store_true')
parser.add_argument('--navigation-only',action='store_true',
    help='Assert the seed/add/reject history and retain only the distinct Cart destination captures.')
args=parser.parse_args()
serial=subprocess.check_output([str(ADB),'devices'],text=True).splitlines()
devices=[x.split()[0] for x in serial if x.endswith('\tdevice')]
assert len(devices)==1, 'Exactly one disposable emulator is required.'
SERIAL=devices[0]
def adb(*words, binary=False):
 return subprocess.check_output([str(ADB),'-s',SERIAL,*words],text=not binary,stderr=subprocess.STDOUT)
assert 'StaffAndroid_API_36' in adb('emu','avd','name'),'Use the named disposable edition emulator.'
stamp=datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')
OUT=ROOT/'evidence/host-journeys'/stamp;OUT.mkdir(parents=True)
for helper_name in ('common_goods_ui.py', 'fresh_android_xml.py'):
    helper = Path(__file__).with_name(helper_name)
    if helper.is_file():
        (OUT / helper_name).write_bytes(helper.read_bytes())
shutil.copy2(Path(__file__), OUT/'tested-host-journey.py')
rows=[]
history=[]
def tree():
    content = read_ui_xml(adb, '/sdcard/chapter19-window.xml', {PACKAGE})
    return content, ET.fromstring(content)

ui = CommonGoodsUi(lambda: tree()[0], lambda *parts: adb(*parts,binary=True), OUT)
def wait_text(value):
 deadline=time.monotonic()+20
 while time.monotonic()<deadline:
  if ui.text(value) is not None:return tree()
  time.sleep(.2)
 raise AssertionError('Visible text not found: '+value)
def capture(label,required,quantity,revision,absent=()):
 wait_text(required)
 frames=ui.observe(label)
 values=[n.get('text') for _,raw in frames for n in ET.fromstring(raw).iter('node')]
 assert required in values,(label,'missing destination/result',required,values)
 assert f"Accepted quantity: {quantity}" in values,(label,values)
 assert f"Revision: {revision}" in values,(label,values)
 for value in absent:assert value not in values,(label,'unexpected',value)
 rows.append({'label':label,'required':required,'viewports':[name for name,_ in frames],
              'absent':list(absent),
              'observed_texts':[value for value in values if value],
              'scope':'Retained scrolling viewports, not one simultaneous screenshot'})
def tap(text=None,desc=None):
 value=text if text is not None else desc
 if desc is not None and desc.startswith('Open '):
  # A scrolled Views tab can remain in the XML beneath the system top inset.
  # Rewind before looking up its current bounds, then prove the destination.
  ui.top()
 node=ui.find(lambda n: n.get('text','').casefold()==value.casefold() or n.get('content-desc','').casefold()==value.casefold())
 assert node is not None and ui.click_node(node),(text,desc)
artifacts={}
modes=[('optimized',args.optimized)] if args.optimized_only else [('broken',args.broken),('fixed',args.fixed),('optimized',args.optimized)]
assert all(path is not None for _,path in modes),'Supply all three APKs or --optimized-only'
for mode,path in modes:
 path=path.resolve();assert path.is_file(),path
 retained=OUT/(mode+'.apk');shutil.copy2(path,retained)
 artifacts[mode]={'source':str(path),'retained':str(retained.relative_to(ROOT)),'sha256':hashlib.sha256(retained.read_bytes()).hexdigest()}
 adb('install','-r',str(retained))
 installed=adb('shell','pm','path',PACKAGE).strip().removeprefix('package:')
 assert installed and '\n' not in installed,'One base APK expected'
 installed_hash=adb('shell','sha256sum',installed).split()[0]
 assert installed_hash==artifacts[mode]['sha256'],(mode,installed_hash)
 artifacts[mode]['installed_sha256']=installed_hash
 adb('shell','pm','clear',PACKAGE)
 adb('shell','am','start','-n',PACKAGE+'/.MainActivity')
 quantity=5 if mode=='broken' else 4
 revision=2 if mode=='broken' else 1
 if args.navigation_only:
  wait_text('Accepted quantity: 3')
 else:capture(mode+'-01-seed','Accepted quantity: 3',3,0)
 tap(text='ADD 1 TEA')
 if args.navigation_only:
  wait_text('Added. Cart has 4.');wait_text('Accepted quantity: 4')
 else:capture(mode+'-02-accepted','Added. Cart has 4.',4,1)
 tap(text='ADD 1 TEA')
 if args.navigation_only:
  wait_text('Maximum quantity reached.');wait_text(f'Accepted quantity: {quantity}')
 else:capture(mode+'-03-rejected','Maximum quantity reached.',quantity,revision)
 history.append({'provider':mode,'asserted_quantity_history':[3,4,quantity],
                 'asserted_rejection':'Maximum quantity reached.'})
 tap(desc='Open Cart')
 capture(mode+'-04-cart','A little everyday ritual. In your cart.',quantity,revision,
         absent=('A little everyday ritual. Saved for later.','Add 1 Tea'))
 assert ui.text('Revision: '+('2' if mode=='broken' else '1')) is not None
 _,root=tree();values=[n.get('text') for n in root.iter('node')]
 assert ('Revision: '+('2' if mode=='broken' else '1')) in values
(OUT/'device.txt').write_text(adb('shell','getprop'))
(OUT/'result.json').write_text(json.dumps({'passed':True,'artifacts':artifacts,'observations':rows,
 'history':history,'navigation_only':args.navigation_only,
 'limits':'API36 emulator functional screen observations; no timing or physical-device claim.'},indent=2)+'\n')
print(OUT)
