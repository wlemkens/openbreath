#!/usr/bin/env bash
# Report failing tests where they can actually be read.
#
# Reading a run's logs or artifacts needs an authenticated token even on a public repository, so
# a failure that only reaches the console or an uploaded report is one nobody can see from the
# run page without downloading it. Check annotations are readable without a token, so every
# failure is emitted as a ::error:: workflow command as well as printed — that puts the assertion
# on the run page, in the API, and inline against the diff.
set -uo pipefail

shopt -s nullglob
found=0
for dir in app/build/test-results/*/; do
    files=("$dir"TEST-*.xml)
    [ ${#files[@]} -eq 0 ] && continue
    echo "=== ${dir} ==="
    python3 - "$dir" "${files[@]}" <<'PY'
import sys, xml.etree.ElementTree as ET

where, paths = sys.argv[1], sys.argv[2:]

def command_safe(s):
    # workflow commands are one line: newlines and % have to be escaped or the message is cut off
    return s.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A").replace("::", "%3A%3A")

count = 0
for path in paths:
    try:
        root = ET.parse(path).getroot()
    except ET.ParseError as e:
        print(f"  (could not parse {path}: {e})")
        continue
    for case in root.iter("testcase"):
        for bad in list(case.findall("failure")) + list(case.findall("error")):
            name = f"{case.get('classname')} > {case.get('name')}"
            detail = "\n".join(
                x for x in ((bad.get("message") or "").strip(), (bad.text or "").strip()) if x
            )
            print(f"  {name}")
            for line in detail.splitlines()[:20]:
                print(f"      {line}")
            print()
            count += 1
            # ten is as many as one step will show; past that the tail is noise anyway
            if count <= 10:
                title = command_safe(name)[:200]
                body = command_safe(detail[:1500]) or "no message"
                print(f"::error title={title}::{body}")

if count == 0:
    print(f"  (no failing testcase in {where} — the task failed outside the tests)")
PY
    found=1
done

if [ "$found" -eq 0 ]; then
    msg="No test XML was written at all, so the task failed before or outside the tests — a simulator that would not boot, or the test binary failing to launch, rather than a failing assertion."
    echo "$msg"
    echo "::error title=No test results::${msg}"
fi

# Optional: the build log, whose tail carries the reason when the failure was not an assertion.
# Same argument as above — the console needs a token to read, an annotation does not.
log="${1:-}"
if [ -n "$log" ] && [ -f "$log" ]; then
    echo "=== tail of $log ==="
    tail -40 "$log"
    python3 - "$log" <<'PY'
import sys
lines = open(sys.argv[1], errors="replace").read().splitlines()
# the interesting part of a Gradle failure is what follows "What went wrong", else just the tail
start = next((i for i, l in enumerate(lines) if "What went wrong" in l), max(0, len(lines) - 30))
body = "\n".join(lines[start:start + 30])[:1500]
safe = body.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A").replace("::", "%3A%3A")
print(f"::error title=Build log::{safe or 'empty log'}")
PY
fi
exit 0
