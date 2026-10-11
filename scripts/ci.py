#!/usr/bin/env python3
"""CI entry point: retain command, result and available reports even on failure."""
from pathlib import Path
import argparse, datetime, hashlib, json, os, re, shutil, subprocess, sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
STAGES = {
    "fast": ["verifyDelivery"],
    "device": [":app:connectedDebugAndroidTest"],
    "optimized": [":app:assembleRelease", ":app:lintRelease"],
    "instrumented-release": [":app:connectedReleaseAndroidTest", "-PtestedBuildType=release"],
}


def verify_stage(root, stage, log_text):
    """A successful process is insufficient: required work and tests must exist."""
    tasks = {
        "fast": [":verifyBoundaries", ":contract-tests:test", ":test-lab:test", ":app:lintDebug", ":app:assembleDebug"],
        "device": [":app:connectedDebugAndroidTest"],
        "optimized": [":app:minifyReleaseWithR8", ":app:assembleRelease", ":app:lintRelease"],
        "instrumented-release": [":app:connectedReleaseAndroidTest"],
    }[stage]
    issues = []
    for task in tasks:
        match = re.search(r"^> Task " + re.escape(task) + r"(?: ([A-Z-]+))?$", log_text, re.M)
        if match is None or match.group(1) in {"SKIPPED", "NO-SOURCE", "FAILED"}:
            issues.append("Required task did not run/reuse valid outputs: " + task)
    if stage == "optimized":
        return {"passed": not issues, "issues": issues, "requires_host_journey": True}
    if stage == "fast":
        expected = {
            "staff.android.delivery.checks.CartContractTest": 16,
            "staff.android.delivery.checks.SavedPresenterTest": 5,
            "staff.android.delivery.checks.RealContentionTest": 1,
            "staff.android.delivery.transfer.UploadQueueReferenceTest": 14,
        }
        paths = [*root.glob("contract-tests/build/test-results/test/TEST-*.xml"),
                 *root.glob("test-lab/build/test-results/test/TEST-*.xml")]
    else:
        expected = {"staff.android.delivery.DeliveryJourneyTest": 1}
        variant = "release" if stage == "instrumented-release" else "debug"
        paths = list(root.glob("app/build/outputs/androidTest-results/connected/" + variant + "/TEST-*.xml"))
    actual = {}
    for path in paths:
        document = ET.parse(path).getroot()
        suites = [document] if document.tag == "testsuite" else list(document.iter("testsuite"))
        for suite in suites:
            name = suite.attrib.get("name", "")
            count = len(suite.findall("testcase"))
            actual[name] = actual.get(name, 0) + count
            if any(int(suite.attrib.get(key, "0")) for key in ["failures", "errors", "skipped"]):
                issues.append("Nonpassing discovered suite: " + name)
            if any(case.find(tag) is not None for case in suite.findall("testcase") for tag in ["failure", "error", "skipped"]):
                issues.append("Nonpassing testcase in: " + name)
            if int(suite.attrib.get("tests", "0")) != count:
                issues.append("Inconsistent discovered count: " + name)
    if actual != expected:
        issues.append("Unexpected test discovery: expected=" + repr(expected) + "; actual=" + repr(actual))
    return {"passed": not issues, "expected_suites": expected, "observed_suites": actual, "issues": issues}

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("stage", choices=STAGES)
    args = parser.parse_args()
    stamp = datetime.datetime.now(datetime.timezone.utc).strftime("%Y%m%dT%H%M%S%fZ")
    destination = ROOT / "evidence/runs" / (stamp + "-" + args.stage)
    destination.mkdir(parents=True)
    shutil.copy2(__file__, destination / "executed-ci.py")
    command = [str(ROOT / "gradlew"), *STAGES[args.stage], "--offline", "--console=plain"]
    started = datetime.datetime.now(datetime.timezone.utc).isoformat()
    with (destination / "gradle.log").open("w") as log:
        code = subprocess.run(command, cwd=ROOT, stdout=log, stderr=subprocess.STDOUT).returncode
    gate = verify_stage(ROOT, args.stage, (destination / "gradle.log").read_text())
    if args.stage == "optimized" and code == 0 and gate["passed"]:
        host_command = [sys.executable, str(ROOT / "scripts/host-journey.py"),
                        "--optimized-only", "--optimized", str(ROOT / "app/build/outputs/apk/release/app-release.apk")]
        host = subprocess.run(host_command, cwd=ROOT, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
        (destination / "host.log").write_text(host.stdout)
        host_result = None
        if host.returncode == 0:
            record_path = Path(host.stdout.strip().splitlines()[-1]) / "result.json"
            host_result = json.loads(record_path.read_text())
            complete = host_result.get("passed") and len(host_result.get("observations", [])) == 4
            complete = complete and set(host_result.get("artifacts", {})) == {"optimized"}
            gate["host_record"] = str(record_path.relative_to(ROOT))
            gate["host_result"] = host_result
        else: complete = False
        if not complete:
            gate["passed"] = False
            gate["issues"].append("Required optimized APK screen journey did not complete")
    gradle_code = code
    if not gate["passed"] and code == 0: code = 1
    # These can include reports from an earlier stage. Their paths/hashes are evidence;
    # the command log says which tasks ran or reused outputs on this invocation.
    for module in ["app", "contract-tests", "test-lab"]:
        for relative in ["build/test-results", "build/outputs/androidTest-results", "build/reports"]:
            source = ROOT / module / relative
            if source.exists(): shutil.copytree(source, destination / "reports" / module / relative)
    artifacts = {}
    for pattern in ["app/build/outputs/apk/**/*.apk", "app/build/outputs/mapping/release/*", "app/build/outputs/mapping/releaseAndroidTest/*"]:
        for source in ROOT.glob(pattern):
            if not source.is_file(): continue
            target = destination / "artifacts" / source.relative_to(ROOT)
            target.parent.mkdir(parents=True, exist_ok=True); shutil.copy2(source, target)
            artifacts[str(source.relative_to(ROOT))] = hashlib.sha256(source.read_bytes()).hexdigest()
    result = {"stage":args.stage, "started_utc":started, "command":command, "exit_code":code, "gradle_exit_code":gradle_code, "gate":gate,
              "artifact_hashes":artifacts, "limits":"Local execution; no hosted CI service run is implied."}
    (destination / "result.json").write_text(json.dumps(result,indent=2)+"\n")
    print(json.dumps({"record":str(destination.relative_to(ROOT)),"exit_code":code}))
    return code
if __name__ == "__main__": sys.exit(main())
