#!/usr/bin/env bash
# Take the App Store screenshots off a simulator.
#
# The counterpart of `docs/store/screenshots.py`, which does the Android set from an emulator. That
# script can tap, because uiautomator can; `simctl` cannot, so the tapping is an XCUITest —
# iosApp/StoreScreenshots — and this script is only what has to happen around it: pick a device whose
# screenshots Apple will accept, wipe it, hand the test the demo log, and check what came out.
#
# **Two device classes, because the binary says it runs on both.** Apple asks for the largest phone
# and the largest tablet and scales those down itself: a 6.9" iPhone at 1320x2868, and — since
# TARGETED_DEVICE_FAMILY is 1,2 — a 13" iPad at 2064x2752. Anything else is refused at upload, which
# is a slow way to find out, so the sizes are asserted here instead.
#
# Usage: ios-screenshots.sh <output-dir>
set -euo pipefail
shopt -s inherit_errexit 2>/dev/null || true

out="${1:?usage: ios-screenshots.sh <output-dir>}"
mkdir -p "$out"
out="$(cd "$out" && pwd)"

# the same seven months of practice the Android set is photographed against, from the same generator
demo="$(mktemp -d)/openbreath-demo.json"
python3 docs/store/screenshots.py --demo-json "$demo"

# Nine rather than the Android set's ten: reminders are Android's alone, so there is no screen to
# photograph.
expected=(firstrun milestone session-idle session-breathe-in log achievements goals
          sound-per-phase settings-options)

# shoot <subdirectory> <accepted sizes> <device type name>...
shoot() {
    local label="$1" dir="$out/$1" sizes="$2"; shift 2
    mkdir -p "$dir"

    local udid
    udid=$(.github/pick-simulator.sh "$@") \
        || { echo "::error title=No simulator to photograph::$label: see the log for what this Xcode has"; exit 1; }

    # Erased, not merely reinstalled: the first shot is the first-run question, which only exists
    # while nothing has ever been stored. A device carried over from a previous run has been
    # through it.
    echo "erasing $udid"
    xcrun simctl shutdown "$udid" 2>/dev/null || true
    xcrun simctl erase "$udid"
    xcrun simctl boot "$udid"
    xcrun simctl bootstatus "$udid" -b

    # TEST_RUNNER_ is how xcodebuild passes an environment variable through to the test process,
    # with the prefix stripped. Without it the test has no idea where to write.
    TEST_RUNNER_SCREENSHOT_DIR="$dir" \
    TEST_RUNNER_DEMO_JSON="$demo" \
    xcodebuild test \
        -project iosApp/iosApp.xcodeproj -scheme iosApp \
        -configuration Debug -sdk iphonesimulator \
        -destination "platform=iOS Simulator,id=$udid" \
        -derivedDataPath build/ios-shots \
        -only-testing:StoreScreenshots/StoreScreenshots \
        2>&1 | tee /tmp/ios-shots.log | xcbeautify || {
          .github/print-test-failures.sh /tmp/ios-shots.log; exit 1; }

    local missing=() name
    for name in "${expected[@]}"; do
        [ -f "$dir/$name.png" ] || missing+=("$name.png")
    done
    if [ ${#missing[@]} -gt 0 ]; then
        echo "::error title=Screenshots missing::${missing[*]} in $dir"
        exit 1
    fi

    local png size
    for png in "$dir"/*.png; do
        size="$(sips -g pixelWidth -g pixelHeight "$png" \
            | awk '/pixelWidth/ {w=$2} /pixelHeight/ {h=$2} END {print w "x" h}')"
        case " $sizes " in
            *" $size "*) ;;
            *)
                echo "::error title=Wrong screenshot size::$(basename "$png") is $size; App Store Connect takes $sizes here"
                exit 1
                ;;
        esac
        echo "  $label/$(basename "$png") $size"
    done
}

# 1290x2796 is the 6.7" phones, which App Store Connect accepts in the 6.9" slot; 2048x2732 is the
# 12.9" iPad, likewise accepted as a 13" set.
shoot iphone "1320x2868 1290x2796" \
    "iPhone 17 Pro Max" "iPhone 16 Pro Max" "iPhone 15 Pro Max"
shoot ipad "2064x2752 2048x2732" \
    "iPad Pro 13-inch (M5)" "iPad Pro 13-inch (M4)" "iPad Pro (12.9-inch) (6th generation)"

echo "${#expected[@]} screenshots per device class in $out, all at sizes App Store Connect accepts"
