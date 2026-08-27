#!/usr/bin/env bash
# Take the App Store screenshots off a simulator.
#
# The counterpart of `docs/store/screenshots.py`, which does the Android set from an emulator. That
# script can tap, because uiautomator can; `simctl` cannot, so the tapping is an XCUITest —
# iosApp/StoreScreenshots — and this script is only what has to happen around it: pick a phone whose
# screenshots Apple will accept, wipe it, hand the test the demo log, and check what came out.
#
# **The device has to be a 6.9" iPhone.** Apple takes one iPhone set and scales it down for smaller
# phones itself, at 1320x2868 (a 16/17 Pro Max) or 1290x2796 in the same slot. Anything smaller is
# refused at upload, which is a slow way to find out — so the sizes are asserted here instead.
#
# Usage: ios-screenshots.sh <output-dir>
set -euo pipefail

out="${1:?usage: ios-screenshots.sh <output-dir>}"
mkdir -p "$out"
out="$(cd "$out" && pwd)"

# Preference order, and it is only a preference: the newest Pro Max the installed Xcode knows about,
# falling back a generation at a time. A name pinned here would rot the day Apple ships a phone,
# which is the same reason record-simulator.sh asks rather than guesses.
#
# **No apostrophe may appear in the Python below.** The whole program is single-quoted for bash, so
# one of them ends the quoting and the rest is re-quoted into nonsense — `runtime['name']` reached
# Python as `runtime[name]` and died on a KeyError naming the phone it had just found, which reads
# like a lookup bug rather than a quoting one.
udid=$(xcrun simctl list -j | python3 -c '
import json, sys
sim = json.load(sys.stdin)
wanted = ["iPhone 17 Pro Max", "iPhone 16 Pro Max", "iPhone 15 Pro Max"]

runtimes = [r for r in sim["runtimes"] if r["isAvailable"] and "iOS" in r["name"]]
if not runtimes:
    sys.exit("no iOS simulator runtime installed")
runtime = max(runtimes, key=lambda r: [int(p) for p in r["version"].split(".")])
ios = runtime["name"]

types = {t["name"]: t["identifier"] for t in sim["devicetypes"]}
name = next((n for n in wanted if n in types), None)
if not name:
    sys.exit(f"none of {wanted} is available; Apple screenshots need a 6.9-inch iPhone")

# an existing device of that type on that runtime, or a new one. Reused rather than recreated so a
# second run is not a fresh 30-second boot for nothing.
for d in sim["devices"].get(runtime["identifier"], []):
    if d.get("isAvailable") and d.get("deviceTypeIdentifier") == types[name]:
        print(d["udid"])
        sys.stderr.write(f"using the existing {name} on {ios}\n")
        break
else:
    import subprocess
    udid = subprocess.check_output(
        ["xcrun", "simctl", "create", "openbreath-shots", types[name], runtime["identifier"]],
        text=True).strip()
    print(udid)
    sys.stderr.write(f"created an {name} on {ios}\n")
') || { echo "::error title=No simulator to photograph::See the log: no 6.9-inch iPhone, no iOS runtime, or the picker itself failed"; exit 1; }

# Erased, not merely reinstalled: the first shot is the first-run question, which only exists while
# nothing has ever been stored. A device carried over from a previous run has been through it.
echo "erasing $udid"
xcrun simctl shutdown "$udid" 2>/dev/null || true
xcrun simctl erase "$udid"
xcrun simctl boot "$udid"
xcrun simctl bootstatus "$udid" -b

# the same seven months of practice the Android set is photographed against, from the same generator
demo="$(mktemp -d)/openbreath-demo.json"
python3 docs/store/screenshots.py --demo-json "$demo"

# TEST_RUNNER_ is how xcodebuild passes an environment variable through to the test process, with
# the prefix stripped. Without it the test has no idea where to write.
set -o pipefail
TEST_RUNNER_SCREENSHOT_DIR="$out" \
TEST_RUNNER_DEMO_JSON="$demo" \
xcodebuild test \
    -project iosApp/iosApp.xcodeproj -scheme iosApp \
    -configuration Debug -sdk iphonesimulator \
    -destination "platform=iOS Simulator,id=$udid" \
    -derivedDataPath build/ios-shots \
    2>&1 | tee /tmp/ios-shots.log | xcbeautify || {
      .github/print-test-failures.sh /tmp/ios-shots.log; exit 1; }

# What came out, and whether Apple will take it. Nine rather than the Android set's ten: reminders
# are Android's alone, so there is no screen to photograph.
expected=(firstrun milestone session-idle session-breathe-in log achievements goals
          sound-per-phase settings-options)
missing=()
for name in "${expected[@]}"; do
    [ -f "$out/$name.png" ] || missing+=("$name.png")
done
if [ ${#missing[@]} -gt 0 ]; then
    echo "::error title=Screenshots missing::${missing[*]}"
    exit 1
fi

for png in "$out"/*.png; do
    size="$(sips -g pixelWidth -g pixelHeight "$png" \
        | awk '/pixelWidth/ {w=$2} /pixelHeight/ {h=$2} END {print w "x" h}')"
    case "$size" in
        1320x2868|1290x2796) ;;
        *)
            echo "::error title=Wrong screenshot size::$(basename "$png") is $size; App Store Connect takes 1320x2868 or 1290x2796"
            exit 1
            ;;
    esac
    echo "  $(basename "$png") $size"
done

echo "${#expected[@]} screenshots in $out, all at a size App Store Connect accepts"
