#!/usr/bin/env python3
import importlib.util, tempfile, unittest
from pathlib import Path
spec=importlib.util.spec_from_file_location('delivery_ci',Path(__file__).with_name('ci.py'))
ci=importlib.util.module_from_spec(spec);spec.loader.exec_module(ci)
class GateChecks(unittest.TestCase):
 def setUp(self):
  self.temp=tempfile.TemporaryDirectory();self.root=Path(self.temp.name)
  self.path=self.root/'app/build/outputs/androidTest-results/connected/debug/TEST-device.xml'
  self.path.parent.mkdir(parents=True)
  self.log='> Task :app:connectedDebugAndroidTest\n'
 def tearDown(self): self.temp.cleanup()
 def report(self,count=1,skipped=0,name='staff.android.delivery.DeliveryJourneyTest'):
  self.path.write_text(f'<testsuites><testsuite name="{name}" tests="{count}" skipped="{skipped}" failures="0" errors="0">'+('<testcase name="journey"/>'*count)+'</testsuite></testsuites>')
 def verify(self): return ci.verify_stage(self.root,'device',self.log)['passed']
 def test_valid_required_journey_passes(self): self.report();self.assertTrue(self.verify())
 def test_successful_process_with_zero_discovery_fails(self): self.report(0);self.assertFalse(self.verify())
 def test_skipped_case_fails(self): self.report(skipped=1);self.assertFalse(self.verify())
 def test_missing_task_with_stale_report_fails(self): self.report();self.log='BUILD SUCCESSFUL\n';self.assertFalse(self.verify())
 def test_skipped_task_with_old_report_fails(self): self.report();self.log='> Task :app:connectedDebugAndroidTest SKIPPED\n';self.assertFalse(self.verify())
 def test_unrelated_discovery_fails(self): self.report(name='UnrelatedTest');self.assertFalse(self.verify())
if __name__=='__main__': unittest.main(verbosity=2)
