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

# The unified log, not --console-pty. A pty cannot be allocated on a headless runner and the
# launch itself dies with "Mach error -308 (ipc/mig) server died", which looks exactly like the
# app failing to start — an instrument that breaks the thing it measures. Streaming the log
# leaves the launch alone and still catches a Kotlin/Native exception, which goes to stderr and
# from there into the unified log.
xcrun simctl spawn "$udid" log stream --style compact \
    --predicate 'processImagePath CONTAINS "OpenBreath"' > "$out/console.log" 2>&1 &
logger=$!
sleep 1

xcrun simctl launch "$udid" "$bundle_id"

# three frames over a few seconds: the first can catch a launch screen rather than the app
for i in 1 2 3; do
    sleep 3
    xcrun simctl io "$udid" screenshot --type png "$out/frame-$i.png" || true
done

kill -INT "$recorder" 2>/dev/null || true
wait "$recorder" 2>/dev/null || true
kill "$logger" 2>/dev/null || true

# What fails this job is evidence that something broke, not a single liveness probe.
#
# launchctl once reported the app gone on a run whose own console showed it logging fifteen
# seconds after launch, on a runner that was simultaneously complaining about CoreAudio overload
# and having more threads than processors. The app was fine; the probe was not. A guard that
# fails a healthy app gets ignored, which costs more than the bug it was meant to catch.
#
# So the three things that genuinely mean failure are checked directly: a crash report, an
# uncaught exception, or an app that never said anything at all. Not being listed at the end is
# reported as a warning, because on this evidence it does not distinguish the two.
reports=$(find ~/Library/Logs/DiagnosticReports -name "OpenBreath*" -newermt '-10 minutes' 2>/dev/null | head -2)
fatal=$(grep -aE "Uncaught Kotlin exception|Terminating app due to uncaught|Fatal error:|signal SIGABRT" \
    "$out/console.log" 2>/dev/null | head -5 || true)
listed=1
xcrun simctl spawn "$udid" launchctl list 2>/dev/null | grep -q "$bundle_id" || listed=0
spoke=0
[ -s "$out/console.log" ] && spoke=1

crashed=0
if [ -n "$reports" ]; then
    crashed=1
    echo "::error title=App crashed::a crash report was written for $bundle_id"
elif [ -n "$fatal" ]; then
    crashed=1
    echo "::error title=App threw::$fatal"
elif [ "$spoke" -eq 0 ]; then
    crashed=1
    echo "::error title=App never started::$bundle_id logged nothing at all"
elif [ "$listed" -eq 0 ]; then
    echo "::warning title=App was not listed at the end::$bundle_id had stopped by the time the \
screenshots were taken, but it started, logged, and left no crash report. Usually a slow runner."
else
    echo "$bundle_id was still running — the frames are the app"
fi

# the console and any report go into the log whenever something is off, warning or error: the
# whole reason they are collected is that nobody can re-run a hosted simulator by hand
if [ "$crashed" -eq 1 ] || [ "$listed" -eq 0 ]; then
    if [ -s "$out/console.log" ]; then
        echo "=== console ==="
        cat "$out/console.log"
        python3 - "$out/console.log" <<'PYEOF'
import sys
body = open(sys.argv[1], errors="replace").read()[-1500:]
safe = body.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A").replace("::", "%3A%3A")
if safe.strip():
    print(f"::error title=App console::{safe}")
PYEOF
    fi
    for r in $reports; do
        echo "=== crash report $r ==="
        head -60 "$r"
        cp "$r" "$out/" 2>/dev/null || true
    done
    if [ -z "$reports" ]; then
        echo "(no crash report was written — the app exited rather than crashed)"
    fi
fi

xcrun simctl shutdown "$udid" || true
ls -la "$out"

# the whole point of the check. The previous version printed the error and exited 0, so a run
# where the app never started was reported as a pass — a guard that does not fail is decoration.
exit "$crashed"
