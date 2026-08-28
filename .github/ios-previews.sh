#!/usr/bin/env bash
# Film the App Store preview videos off a simulator.
#
# The screenshots' sibling. `simctl` can record a simulator but not tap one, and XCUITest can tap one
# but not record it, so this runs both at once: iosApp/StoreScreenshots/AppPreview taps Start and
# writes the instant the first inhale begins, while `simctl io recordVideo` films, and ffmpeg then
# trims the capture to that instant.
#
# **Apple requires an audio track** — stereo AAC, 256 kbps — and a simulator records no audio at all,
# so the soundtrack is rendered from the shared WaveDsp by `PreviewAudio` in desktopTest. Same
# filters, same easing, same edge dip as the phone: the app's sound, asked for a buffer instead of a
# speaker. The alternative was a silent track, which would pass and misrepresent the app.
#
# What Apple accepts, and why every number below is what it is:
#   duration   15-30 s          -> 22, two whole breaths of the default 5.5/5.5 preset
#   6.9" phone 886x1920         -> from a Pro Max capture at 1320x2868
#   13" iPad   1200x1600        -> from a 13" iPad Pro capture at 2064x2752, the same 3:4
#   video      H.264 high 4.0, 30 fps max, 10-12 Mbps VBR
#   audio      stereo AAC 256 kbps, 44.1 or 48 kHz
#
# Usage: ios-previews.sh <output-dir>
set -euo pipefail

out="${1:?usage: ios-previews.sh <output-dir>}"
mkdir -p "$out"
out="$(cd "$out" && pwd)"

SECONDS_OF_BREATH=22

# ffmpeg does the scaling, the frame rate, the trim and the mux. Not preinstalled on every runner
# image, and a preview run is a manual job, so paying for the install here is cheaper than a second
# tool that does the job worse — `avconvert` cannot be told a frame rate or a bit rate.
if ! command -v ffmpeg >/dev/null; then
    echo "installing ffmpeg"
    brew install ffmpeg
fi

# The soundtrack, once for both device classes: the sound does not depend on the screen.
echo "rendering the wave bed"
./gradlew --no-daemon :app:desktopTest --tests '*PreviewAudio*' -Ppreview=true 2>&1 \
    | tee /tmp/preview-audio.log | tail -5 || {
      .github/print-test-failures.sh /tmp/preview-audio.log; exit 1; }
waves="app/build/preview/waves.wav"
[ -f "$waves" ] || { echo "::error title=No soundtrack::$waves was not written"; exit 1; }

# film <name> <WxH> <device type name>...
film() {
    local label="$1" size="$2"; shift 2
    # under its own directory, not `$out/$label`: that is where the screenshots of the same device
    # class live, and this one gets `rm -rf`d at the start of every run
    local work="$out/preview-work/$label"
    rm -rf "$work"; mkdir -p "$work"

    local udid
    udid=$(.github/pick-simulator.sh "$@") \
        || { echo "::error title=No simulator to film::$label: see the log for what this Xcode has"; exit 1; }

    # erased so the run is the same every time, and because the app has to be on its defaults: a
    # fresh install plays waves on every phase, which is what the rendered soundtrack is
    xcrun simctl shutdown "$udid" 2>/dev/null || true
    xcrun simctl erase "$udid"
    xcrun simctl boot "$udid"
    xcrun simctl bootstatus "$udid" -b

    local marker="$work/inhale-began"
    rm -f "$marker"

    # The test in the background, because this shell has to be free to start recording the moment
    # the marker appears. Its output goes to a file rather than the console, and the failure paths
    # below put that file through print-test-failures.sh — an annotation is the half of a job log
    # that can be read without a token.
    (
        TEST_RUNNER_PREVIEW_DIR="$work" \
        xcodebuild test \
            -project iosApp/iosApp.xcodeproj -scheme iosApp \
            -configuration Debug -sdk iphonesimulator \
            -destination "platform=iOS Simulator,id=$udid" \
            -derivedDataPath build/ios-shots \
            -only-testing:StoreScreenshots/AppPreview \
            > "$work/xcodebuild.log" 2>&1
    ) &
    local driver=$!

    # Recording starts before the app does and runs long: a capture cannot be started late, whereas
    # anything filmed too early is thrown away by the trim below. The install and first launch of a
    # Compose app take minutes on a runner, which is what makes guessing an offset hopeless and a
    # marker cheap.
    local raw="$work/capture.mov"
    xcrun simctl io "$udid" recordVideo --codec h264 --force "$raw" &
    local recorder=$!

    local waited=0
    while [ ! -s "$marker" ]; do
        if ! kill -0 "$driver" 2>/dev/null; then
            kill -INT "$recorder" 2>/dev/null || true
            echo "::error title=The driver exited before the sitting began::$label"
            .github/print-test-failures.sh "$work/xcodebuild.log" || true
            exit 1
        fi
        sleep 1
        waited=$((waited + 1))
        [ "$waited" -lt 480 ] || {
            kill -INT "$recorder" 2>/dev/null || true
            echo "::error title=No inhale within eight minutes::$label"
            exit 1
        }
    done

    echo "$label: inhale began after ${waited}s, filming ${SECONDS_OF_BREATH}s"
    sleep "$((SECONDS_OF_BREATH + 2))"

    # SIGINT rather than SIGKILL: recordVideo finalises the container on interrupt, and a killed
    # recorder leaves a file that ffmpeg refuses.
    local stopped
    stopped=$(python3 -c 'import time; print(f"{time.time():.3f}")')
    kill -INT "$recorder" 2>/dev/null || true
    wait "$recorder" 2>/dev/null || true
    local status=0
    wait "$driver" || status=$?
    if [ "$status" -ne 0 ]; then
        # print-test-failures.sh rather than a bare tail: it turns the interesting lines into
        # annotations, and an annotation is the half of a job log that can be read without a token
        .github/print-test-failures.sh "$work/xcodebuild.log"
        exit 1
    fi
    [ -s "$raw" ] || { echo "::error title=Nothing was recorded::$raw is empty"; exit 1; }

    # Where the first inhale is in the capture, measured **backwards from the end**: the capture
    # stops when the recorder is interrupted, so the phase change sits `stopped - marker` seconds
    # before the last frame, wherever the recording actually began.
    #
    # It was measured forwards from the start once, and that is a trap worth leaving a note about:
    # `recordVideo` reported "Recording started" a full two minutes after the process was launched
    # on a freshly erased device, so the offset came out 50 seconds beyond the end of a 226-second
    # capture. ffmpeg then seeked past the end and wrote a perfectly valid file containing the
    # soundtrack and no video at all — `video:0KiB audio:595KiB` — which the size check caught only
    # because ffprobe returned an empty width. Nothing about the warmup is knowable in advance;
    # the end of the file is.
    local captured offset
    captured=$(ffprobe -v error -show_entries format=duration -of csv=p=0 "$raw")
    offset=$(python3 -c "
import sys
captured, stopped, marker_at = float(sys.argv[1]), float(sys.argv[2]), float(open(sys.argv[3]).read().strip())
offset = captured - (stopped - marker_at)
if offset < 0 or offset + float(sys.argv[4]) > captured:
    print(f'::error title=The trim does not fit the capture::{offset:.1f}s + {sys.argv[4]}s of a {captured:.1f}s recording')
    raise SystemExit(1)
print(f'{offset:.3f}')" "$captured" "$stopped" "$marker" "$SECONDS_OF_BREATH")
    echo "$label: ${captured}s captured, trimming from ${offset}s"

    local final="$out/$label-breathing.mp4"
    ffmpeg -nostdin -y -ss "$offset" -i "$raw" -i "$waves" \
        -t "$SECONDS_OF_BREATH" \
        -vf "scale=$size,fps=30,format=yuv420p" \
        -c:v libx264 -profile:v high -level 4.0 -b:v 11M -maxrate 12M -bufsize 24M \
        `# 11M is the middle of Apple's 10-12 Mbps and x264 comes in under it — about 7.4 — because` \
        `# the app is a glow on black and there is genuinely less to encode. That is a ceiling being` \
        `# respected rather than a target being missed, and forcing bits into black frames would only` \
        `# make the file bigger. Do not "fix" it.` \
        -c:a aac -b:a 256k -ar 44100 -ac 2 \
        -map 0:v:0 -map 1:a:0 -movflags +faststart \
        "$final" 2>&1 | tail -3

    # What Apple will check, checked here instead: the size, the duration, and that there is sound
    # at all. A preview refused at upload costs a round trip through App Store Connect to learn one
    # number, and every one of these is knowable now.
    local width height duration audio
    width=$(ffprobe -v error -select_streams v:0 -show_entries stream=width -of csv=p=0 "$final")
    height=$(ffprobe -v error -select_streams v:0 -show_entries stream=height -of csv=p=0 "$final")
    duration=$(ffprobe -v error -show_entries format=duration -of csv=p=0 "$final")
    audio=$(ffprobe -v error -select_streams a:0 -show_entries stream=codec_name -of csv=p=0 "$final")

    [ -n "$width" ] || {
        echo "::error title=The preview has no video::$label got a file with a soundtrack and no picture, which means the trim landed outside the capture"
        exit 1
    }
    [ "${width}x${height}" = "$size" ] || {
        echo "::error title=The preview is the wrong size::$label is ${width}x${height}, Apple wants $size"
        exit 1
    }
    [ -n "$audio" ] || {
        echo "::error title=The preview has no sound::Apple requires an audio track, and the wave bed did not make it in"
        exit 1
    }
    python3 -c "
import sys
d = float(sys.argv[1])
if not 15.0 <= d <= 30.0:
    print(f'::error title=The preview is the wrong length::{d:.1f}s, and Apple takes 15 to 30')
    raise SystemExit(1)
print(f'  {sys.argv[2]} {sys.argv[3]} {d:.1f}s, {sys.argv[4]} audio')
" "$duration" "$label-breathing.mp4" "$size" "$audio"

    rm -f "$raw"
}

film iphone 886x1920 \
    "iPhone 17 Pro Max" "iPhone 16 Pro Max" "iPhone 15 Pro Max"
film ipad 1200x1600 \
    "iPad Pro 13-inch (M5)" "iPad Pro 13-inch (M4)" "iPad Pro (12.9-inch) (6th generation)"

echo "previews in $out"
