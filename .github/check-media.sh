#!/usr/bin/env bash
# Fail if any bundled media is still a Git LFS pointer rather than the thing itself.
#
# .gitattributes puts every audio format in LFS, and actions/checkout does not fetch LFS unless
# asked. Without `lfs: true` the build succeeds, the APK packages a 130-byte text file where a
# 97 KB mp3 belongs, and the only symptom is that the singing bowl never sounds — the exact
# failure Audio.kt's own comment calls "invisible without this". A build that cannot make a noise
# should not be a green tick.
set -euo pipefail

bad=0
while IFS= read -r f; do
    if head -c 40 "$f" | grep -q "git-lfs.github.com"; then
        size=$(head -c 200 "$f" | sed -n 's/^size \([0-9]*\)/\1/p')
        echo "::error title=Media not fetched::$f is a Git LFS pointer, not audio (should be ${size:-?} bytes). The checkout needs lfs: true."
        bad=1
    fi
done < <(find app/src media -type f \( -name '*.mp3' -o -name '*.wav' -o -name '*.ogg' \) 2>/dev/null)

if [ "$bad" -eq 0 ]; then
    echo "bundled media is real:"
    find app/src -type f -name '*.mp3' -exec ls -la {} \; 2>/dev/null
fi
exit "$bad"
