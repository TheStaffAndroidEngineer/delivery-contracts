#!/usr/bin/env python3
"""Run deliberate source/policy defects in disposable copies; never edit reader work."""
from pathlib import Path
import datetime, hashlib, json, os, shutil, subprocess, tempfile
ROOT=Path(__file__).resolve().parents[1]
STAMP=datetime.datetime.now(datetime.timezone.utc).strftime("%Y%m%dT%H%M%S%fZ")
OUT=ROOT/"evidence/probes"/STAMP
OUT.mkdir(parents=True)
rows=[]

def clone():
    dest=Path(tempfile.mkdtemp(prefix="delivery-contract-probe-"))
    shutil.copytree(ROOT,dest,dirs_exist_ok=True,
        ignore=shutil.ignore_patterns("build",".gradle",".kotlin","evidence","__pycache__"))
    return dest

def run(dest,name,args,expected,needle):
    command=[str(dest/"gradlew"),*args,"--offline","--console=plain"]
    with (OUT/(name+".log")).open("w") as log:
        result=subprocess.run(command,cwd=dest,stdout=log,stderr=subprocess.STDOUT)
    text=(OUT/(name+".log")).read_text()
    ok=(result.returncode==0 if expected=="success" else result.returncode!=0) and needle in text
    rows.append({"name":name,"command":command,"exit_code":result.returncode,"expected":expected,"required_diagnostic":needle,"passed":ok})
    assert ok,(name,result.returncode,text[-2500:])

baseline={str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest() for p in ROOT.rglob("*") if p.is_file() and not any(x in p.relative_to(ROOT).parts for x in ["build",".gradle",".kotlin","evidence","__pycache__"])}
try:
    work=clone()
    try:
        run(work,"baseline-policy",["verifyBoundaries"],"success","DELIVERY BOUNDARIES PASS")
        run(work,"cache-hit",["verifyBoundaries"],"success","Configuration cache entry reused")
        path=work/"feature-saved/build.gradle.kts"
        path.write_text(path.read_text()+"\ndependencies { implementation(project(\":cart-impl\")) }\n")
        run(work,"forbidden-edge-compiles",[":feature-saved:compileKotlin"],"success","BUILD SUCCESSFUL")
        run(work,"forbidden-edge-blocks-delivery",["verifyDelivery"],"failure","Boundary violation")
    finally: shutil.rmtree(work)
    work=clone()
    try:
        path=work/"app/build.gradle.kts"
        path.write_text(path.read_text()+"\ndependencies { releaseImplementation(project(\":test-lab\")) }\n")
        run(work,"release-edge",["verifyBoundaries"],"failure",":app|releaseImplementation|:test-lab")
    finally: shutil.rmtree(work)
    work=clone()
    try:
        path=work/"feature-saved/build.gradle.kts"
        path.write_text(path.read_text()+"\nval escapeHatch by configurations.creating\ndependencies { add(\"escapeHatch\", project(\":cart-impl\")) }\n")
        run(work,"custom-edge",["verifyBoundaries"],"failure",":feature-saved|escapeHatch|:cart-impl")
    finally: shutil.rmtree(work)
    work=clone()
    try:
        path=work/"settings.gradle.kts"; path.write_text(path.read_text()+"\ninclude(\":unclassified\")\n")
        (work/"unclassified").mkdir();(work/"unclassified/build.gradle.kts").write_text("")
        run(work,"new-module",["verifyBoundaries"],"failure","Module registry requires policy review")
    finally: shutil.rmtree(work)
    work=clone()
    try:
        path=work/"feature-saved/src/main/kotlin/staff/android/delivery/saved/SavedPresenter.kt"
        path.write_text(path.read_text().replace("throw cancelled", "// Deliberate defect: swallow cancellation"))
        run(work,"swallowed-cancellation",[":contract-tests:test","--tests","*SavedPresenterTest.cancellationIsPropagatedAndCleansBusy"],"failure","cancellationIsPropagatedAndCleansBusy() FAILED")
    finally: shutil.rmtree(work)
finally:
    after={n:hashlib.sha256((ROOT/n).read_bytes()).hexdigest() for n in baseline}
    preserved=baseline==after
    (OUT/"result.json").write_text(json.dumps({"passed":bool(rows) and all(r["passed"] for r in rows) and preserved,"reader_sources_preserved":preserved,"checks":rows},indent=2)+"\n")
    print(OUT)
    assert preserved,"A baseline source changed during the probes; inspect before continuing."
