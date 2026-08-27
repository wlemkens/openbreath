#!/usr/bin/env bash
# Print the udid of a simulator of the first named device type this Xcode has, creating one if there
# is none. Used by both ios-screenshots.sh and ios-previews.sh, which is the whole reason it is its
# own file: the picker was copied once and that was one copy too many.
#
# The names are tried in order. Named rather than matched loosely on purpose — "the newest thing that
# looks like a Pro Max" is a guess about simctl's ordering, while a list that runs out says so and is
# one line to extend.
#
# Usage: pick-simulator.sh "iPhone 17 Pro Max" "iPhone 16 Pro Max" ...
set -euo pipefail

[ $# -gt 0 ] || { echo "usage: pick-simulator.sh <device type name>..." >&2; exit 2; }

#
# **No apostrophe may appear in the Python below.** The whole program is single-quoted for bash, so
# one of them ends the quoting and the rest is re-quoted into nonsense — `runtime['name']` reached
# Python as `runtime[name]` and died on a KeyError naming the device it had just found, which reads
# like a lookup bug rather than a quoting one.
xcrun simctl list -j | python3 -c '
import json, sys
sim = json.load(sys.stdin)
wanted = sys.argv[1:]

runtimes = [r for r in sim["runtimes"] if r["isAvailable"] and "iOS" in r["name"]]
if not runtimes:
    sys.exit("no iOS simulator runtime installed")
runtime = max(runtimes, key=lambda r: [int(p) for p in r["version"].split(".")])
ios = runtime["name"]

types = {t["name"]: t["identifier"] for t in sim["devicetypes"]}
name = next((n for n in wanted if n in types), None)
if not name:
    have = sorted(n for n in types if "iPhone" in n or "iPad" in n)
    sys.exit(f"none of {wanted} is available. This Xcode has: {have}")

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
' "$@"
