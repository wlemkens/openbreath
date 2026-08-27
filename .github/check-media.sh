#!/usr/bin/env bash
# Fail if any bundled media is still a Git LFS pointer rather than the thing itself.
#
# .gitattributes puts every audio format *and every png* in LFS, and actions/checkout does not fetch LFS unless
# asked. Without `lfs: true` the build succeeds, the APK packages a 130-byte text file where a
# 97 KB mp3 belongs, and the only symptom is that the singing bowl never sounds — the exact
# failure Audio.kt's own comment calls "invisible without this". A build that cannot make a noise
# should not be a green tick. The same now goes for png: the app icon is generated and tracked in
# LFS, and a pointer file shipped as an app icon fails review rather than merely looking wrong.
#
# ico and icns join them for the desktop installers, where the failure is louder — jpackage refuses
# a 130-byte icon outright — but the fix is the same one sentence, so it may as well be said here
# rather than in a jpackage error message about an unrecognised file format.
set -euo pipefail

bad=0
while IFS= read -r f; do
    if head -c 40 "$f" | grep -q "git-lfs.github.com"; then
        size=$(head -c 200 "$f" | sed -n 's/^size \([0-9]*\)/\1/p')
        echo "::error title=Media not fetched::$f is a Git LFS pointer, not audio (should be ${size:-?} bytes). The checkout needs lfs: true."
        bad=1
    fi
done < <(find app/src app/desktopIcons media iosApp docs -type f \( -name '*.mp3' -o -name '*.wav' -o -name '*.ogg' -o -name '*.png' -o -name '*.ico' -o -name '*.icns' \) 2>/dev/null)

if [ "$bad" -eq 0 ]; then
    echo "bundled media is real:"
    find app/src app/desktopIcons media iosApp docs -type f \( -name '*.mp3' -o -name '*.png' -o -name '*.ico' -o -name '*.icns' \) -exec ls -la {} \; 2>/dev/null
fi
exit "$bad"
