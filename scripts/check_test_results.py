"""Check fresh JVM results, including the real offline C protocol replay."""
from pathlib import Path
import xml.etree.ElementTree as ET
root = Path(__file__).resolve().parents[1]
reports = sorted((root / 'core/build/test-results/test').glob('TEST-*.xml'))
assert reports, 'No JVM test reports found'
cases = [case for report in reports for case in ET.parse(report).getroot().iter('testcase')]
failed = [case for case in cases if any(case.find(tag) is not None for tag in ('failure', 'error', 'skipped'))]
assert cases and not failed, 'JVM tests must pass without skips: ' + ', '.join(case.attrib['name'] for case in failed)
assert any(case.attrib.get('name') == 'realFirmwareReplayBeginScoreUndoPauseResumeFinishClear' for case in cases), 'Offline C protocol replay was not executed'
print(f'PASS: {len(cases)} JVM tests; no skips; real C protocol replay included.')
