#!/usr/bin/env bash
# Print failing tests into the job log.
#
# Reading a run's logs or artifacts needs an authenticated token even on a public repository, so
# a failure that only exists inside an uploaded report is a failure nobody can see without
# downloading it. This puts the assertion straight in the console, where the run page shows it.
set -uo pipefail

shopt -s nullglob
found=0
for dir in app/build/test-results/*/; do
    files=("$dir"TEST-*.xml)
    [ ${#files[@]} -eq 0 ] && continue
    echo "=== ${dir} ==="
    python3 - "${files[@]}" <<'PY'
import sys, xml.etree.ElementTree as ET

for path in sys.argv[1:]:
    try:
        root = ET.parse(path).getroot()
    except ET.ParseError as e:
        print(f"  (could not parse {path}: {e})")
        continue
    for case in root.iter("testcase"):
        for bad in list(case.findall("failure")) + list(case.findall("error")):
            print(f"  {case.get('classname')} > {case.get('name')}")
            text = (bad.get("message") or "").strip()
            if text:
                for line in text.splitlines()[:12]:
                    print(f"      {line}")
            body = (bad.text or "").strip()
            if body:
                for line in body.splitlines()[:20]:
                    print(f"      {line}")
            print()
PY
    found=1
done

[ "$found" -eq 0 ] && echo "no test XML was written — the failure was before or outside the tests"
exit 0
