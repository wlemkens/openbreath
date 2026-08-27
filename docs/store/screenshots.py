#!/usr/bin/env python3
"""Take the store screenshots off a running emulator, from a known practice log.

    ./gradlew :app:installDebug && python3 docs/store/screenshots.py

Play wants 9:16 and at least two shots, so the emulator is resized to 1080x1920 for the run and
put back afterwards. **It clears the app's data** — the log has to be the demo one for the
numbers on the achievements screen to mean anything, and a real one cannot be arranged on
purpose. Run it on an emulator, never on a phone you practise on.

The demo log is imported through the app's own backup import rather than written into DataStore,
because that is the one route in that is a supported feature rather than a poke at a private file.
"""
import datetime, json, pathlib, random, re, subprocess, sys, time
from zoneinfo import ZoneInfo

PKG = "io.github.wlemkens.openbreath"
OUT = pathlib.Path(__file__).resolve().parent / "android"
TZ = ZoneInfo("Europe/Brussels")
# fixed, so the same run twice gives the same screenshots; the log ends "today"
TODAY = datetime.date.today()

def sh(*a): return subprocess.run(["adb", *a], capture_output=True, text=True).stdout

def demo_backup() -> str:
    """Seven months of practice with an unbroken run of days at the end of it."""
    random.seed(7)
    presets = [("Coherence 5.5", 5500, 0, 5500, 0), ("4-7-8", 4000, 7000, 8000, 0),
               ("Box 4", 4000, 4000, 4000, 4000)]
    history = []
    for back in range(210, -1, -1):
        day = TODAY - datetime.timedelta(days=back)
        if back > 120 and random.random() < 0.25:   # the streak starts 120 days ago
            continue
        # only the morning sitting today: an entry stamped later than the clock reads as a bug
        times = [(7, 20)] if back == 0 else [(7, 20), (8, 5), (21, 30)]
        for h, m in random.sample(times, 1 if back == 0 or random.random() < 0.75 else 2):
            name, i, hi, e, ho = presets[0] if random.random() < 0.6 else random.choice(presets)
            dur = random.choice([5, 5, 5, 10, 10, 15]) * 60_000
            start = datetime.datetime(day.year, day.month, day.day, h, m, tzinfo=TZ)
            history.append({"at": int(start.timestamp() * 1000), "durationMs": dur,
                            "preset": name, "inhaleMs": i, "holdInMs": hi, "exhaleMs": e,
                            "holdOutMs": ho, "cycles": dur // (i + hi + e + ho)})
    history.sort(key=lambda x: x["at"])
    return json.dumps({
        "version": 1,
        "config": {"presets": [{"name": n, "inhaleMs": i, "holdInMs": hi, "exhaleMs": e,
                                "holdOutMs": ho} for n, i, hi, e, ho in presets],
                   "activeIndex": 0, "durationMs": 300000, "vibrate": True, "endSound": True},
        "history": history,
        "goals": [{"id": 1, "metric": "SITTINGS", "period": "DAY", "target": 1},
                  {"id": 2, "metric": "MINUTES", "period": "WEEK", "target": 60}],
        # 0, so the hundred-day milestone is still owed and shows itself once during the run
        "celebrated": 0,
        "reminders": [
            {"id": 1, "name": "Morning sit", "hour": 7, "minute": 30, "repeat": "DAILY"},
            {"id": 2, "name": "If the day got away", "hour": 21, "minute": 0, "repeat": "DAILY",
             "onlyIfBehind": True}],
    })

def nodes():
    """Every label on screen with where to tap it, out of uiautomator's dump."""
    sh("shell", "uiautomator", "dump", "/sdcard/ui.xml")
    xml = sh("exec-out", "cat", "/sdcard/ui.xml")
    found = []
    for m in re.finditer(r'(?:text|content-desc)="([^"]+)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml):
        x1, y1, x2, y2 = map(int, m.groups()[1:])
        found.append((m.group(1), (x1 + x2) // 2, (y1 + y2) // 2))
    return found

def tap(label, wait=2.0, exact=False, required=True):
    for txt, x, y in nodes():
        if txt == label if exact else label.lower() in txt.lower():
            sh("shell", "input", "tap", str(x), str(y)); time.sleep(wait); return True
    if required: sys.exit(f"no '{label}' on screen: {[n[0] for n in nodes()]}")
    return False

def swipe(y1, y2, ms=250): sh("shell", "input", "swipe", "200", str(y1), "200", str(y2), str(ms)); time.sleep(1)

def shot(name):
    OUT.mkdir(parents=True, exist_ok=True)
    png = subprocess.run(["adb", "exec-out", "screencap", "-p"], capture_output=True).stdout
    (OUT / f"{name}.png").write_bytes(png)
    print(f"  {name}.png")

def main():
    if PKG not in sh("shell", "pm", "list", "packages"):
        sys.exit(f"{PKG} is not installed: ./gradlew :app:installDebug")
    pathlib.Path("/tmp/openbreath-demo.json").write_text(demo_backup())
    sh("push", "/tmp/openbreath-demo.json", "/sdcard/Download/openbreath-demo.json")
    sh("shell", "wm", "size", "1080x1920")
    try:
        sh("shell", "pm", "clear", PKG)
        sh("shell", "am", "start", "-n", f"{PKG}/.MainActivity"); time.sleep(4)

        shot("firstrun"); tap("Continue")

        # the demo log, through Settings ▸ Advanced ▸ Import
        tap("⋮", exact=True); tap("Settings", exact=True); tap("Advanced")
        for _ in range(8): swipe(1600, 400)
        tap("Import", exact=True, wait=3)
        for txt, x, y in nodes():                       # the file row, not its preview button
            if txt == "openbreath-demo.json":
                sh("shell", "input", "tap", str(x), str(y)); time.sleep(3); break
        else:
            sys.exit("the demo backup is not in the picker")
        tap("Import", exact=True, wait=3); tap("OK", exact=True)

        # a hundred days of practice has just arrived, so the milestone shows itself
        shot("milestone"); tap("Onwards")

        for _ in range(10): swipe(400, 1600)             # back to the top of Settings, then out
        tap("Done", exact=True)
        shot("session-idle")

        tap("Start", exact=True, wait=0)                 # near the top of the second inhale
        time.sleep(4.2 + 5.5 + 5.5)
        shot("session-breathe-in")
        tap("Reset", exact=True)

        for item in ["Log", "Achievements", "Goals", "Reminders"]:
            tap("⋮", exact=True); tap(item, exact=True, wait=2.5)
            shot(item.lower())
            tap("Done", exact=True)

        tap("⋮", exact=True); tap("Settings", exact=True); tap("Advanced")
        swipe(1600, 700)                                 # the sound section, with one marker set
        tap("Marker", exact=True)
        shot("sound-per-phase")
        swipe(1600, 700); swipe(1600, 900)
        shot("settings-options")
    finally:
        sh("shell", "wm", "size", "reset")

if __name__ == "__main__":
    main()
