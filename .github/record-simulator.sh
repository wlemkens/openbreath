#!/usr/bin/env bash
# Boot a simulator, install the app, launch it and capture what it draws.
#
# This exists for the case of having no iPhone to hand. It proves the app launches and renders —
# that the framework links, the Compose tree comes up, the store opens and the session screen
# draws — which is the failure everything up to now could not have caught, because compiling and
# passing tests say nothing about whether an app starts.
#
# What it cannot show is the cue moving. simctl has no way to tap a button, and the cue is still
# until Start is pressed, so nothing here reaches the animation. Seeing that needs either a
# UI test target driving the tap, or the app on a real phone.
#
# Usage: record-simulator.sh <path-to-.app>
set -euo pipefail

app="${1:?usage: record-simulator.sh <app>}"
bundle_id="io.github.wlemkens.openbreath"
out="build/recording"

[ -d "$app" ] || { echo "::error title=No app bundle::$app does not exist"; exit 1; }
mkdir -p "$out"

# whatever iPhone this image actually has, rather than a model name guessed a year in advance
udid=$(xcrun simctl list devices available -j | python3 -c '
import json, sys
runtimes = json.load(sys.stdin)["devices"]
best = None
for runtime, devices in runtimes.items():
    if "iOS" not in runtime:
        continue
    for d in devices:
        if d.get("isAvailable") and "iPhone" in d["name"]:
            # last runtime listed tends to be the newest; take the newest iPhone on it
            best = (runtime, d["udid"], d["name"])
if not best:
    sys.exit("no available iPhone simulator")
print(best[1])
sys.stderr.write(f"using {best[2]} on {best[0]}\n")
')

echo "booting $udid"
xcrun simctl boot "$udid" || true
xcrun simctl bootstatus "$udid" -b

xcrun simctl install "$udid" "$app"

# record for the whole launch, then stop it politely so the container is finalised
xcrun simctl io "$udid" recordVideo --codec h264 --force "$out/launch.mp4" &
recorder=$!

# attached to a pty, so whatever the app writes on its way down is captured. A Kotlin/Native
# uncaught exception prints its message and stack here and nowhere else that survives the run.
( xcrun simctl launch --console-pty "$udid" "$bundle_id" > "$out/console.log" 2>&1 & )

# three frames over a few seconds: the first can catch a launch screen rather than the app
for i in 1 2 3; do
    sleep 3
    xcrun simctl io "$udid" screenshot --type png "$out/frame-$i.png" || true
done

kill -INT "$recorder" 2>/dev/null || true
wait "$recorder" 2>/dev/null || true

# a crash on launch leaves the process gone; say so rather than uploading three identical frames
# of a home screen and calling the job green
if xcrun simctl spawn "$udid" launchctl list 2>/dev/null | grep -q "$bundle_id"; then
    echo "$bundle_id was still running — the frames are the app"
else
    echo "::error title=App crashed on launch::$bundle_id was not running when the screenshots were taken"

    # the console is where a Kotlin exception says what it was
    if [ -s "$out/console.log" ]; then
        echo "=== console ==="
        cat "$out/console.log"
        python3 - "$out/console.log" <<'PY'
import sys
body = open(sys.argv[1], errors="replace").read()[-1500:]
safe = body.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A").replace("::", "%3A%3A")
if safe.strip():
    print(f"::error title=App console::{safe}")
PY
    fi

    # and the crash report is where the signal and the frame are
    reports=$(find ~/Library/Logs/DiagnosticReports -name "OpenBreath*" -newermt '-10 minutes' 2>/dev/null | head -2)
    for r in $reports; do
        echo "=== crash report $r ==="
        head -60 "$r"
        cp "$r" "$out/" 2>/dev/null || true
    done
    [ -z "$reports" ] && echo "(no crash report was written — the app may have exited rather than crashed)"
fi

xcrun simctl shutdown "$udid" || true
ls -la "$out"
