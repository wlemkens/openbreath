#!/usr/bin/env bash
# Select the newest Xcode on the runner, refusing anything below a floor.
#
# The floor exists because Compose Multiplatform is built against a particular iOS SDK and the
# link fails on an undefined Objective-C class when the SDK is older — see CLAUDE.md. The
# "newest" part exists because runner images move on: matching a single version by name means
# this breaks the day GitHub ships the next Xcode, which is the worst possible time to discover
# it if an OS release has already knocked the app off the store.
#
# Usage: select-xcode.sh [minimum-major-version]
set -uo pipefail

MIN="${1:-26}"

chosen=$(python3 - "$MIN" <<'PY'
import glob, os, plistlib, sys

minimum = int(sys.argv[1])

def version_of(app):
    for rel in ("Contents/version.plist", "Contents/Info.plist"):
        try:
            with open(os.path.join(app, rel), "rb") as f:
                v = plistlib.load(f).get("CFBundleShortVersionString")
            if v:
                return tuple(int(p) for p in v.split(".") if p.isdigit()), v
        except Exception:
            continue
    return None, None

# several names symlink to one bundle (Xcode.app, Xcode_26.3.0.app); realpath collapses them
seen = {}
root = os.environ.get("XCODE_ROOT", "/Applications")  # overridable so this is testable
for app in glob.glob(os.path.join(root, "Xcode*.app")):
    real = os.path.realpath(app)
    if real in seen:
        continue
    parts, text = version_of(real)
    if parts:
        seen[real] = (parts, text)

if not seen:
    print("NONE::no Xcode found in " + root + " at all")
    raise SystemExit

usable = {p: v for p, v in seen.items() if v[0][0] >= minimum}
if not usable:
    have = ", ".join(sorted(v[1] for v in seen.values()))
    print(f"NONE::the runner has only {have}, and Compose Multiplatform needs {minimum} or newer")
    raise SystemExit

best = max(usable.items(), key=lambda kv: kv[1][0])
print(f"OK::{best[0]}::{best[1][1]}")
PY
)

case "$chosen" in
    OK::*)
        path="${chosen#OK::}"; version="${path#*::}"; path="${path%%::*}"
        echo "selecting Xcode $version at $path"
        sudo xcode-select -s "$path"
        xcodebuild -version
        ;;
    *)
        reason="${chosen#NONE::}"
        echo "::error title=No usable Xcode::${reason}"
        exit 1
        ;;
esac
