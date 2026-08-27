#!/usr/bin/env bash
# Write the SideStore/AltStore source JSON that offers this build as an update.
#
# A "source" is one static JSON file listing apps and their versions. SideStore polls it and offers
# an update when the advertised version differs from what is installed — so this file, not the ipa,
# is what turns a push into a notification on the phone.
#
# It is published as a release asset rather than committed, for two reasons. A commit from CI on a
# branch CI watches retriggers CI, and that loop is only stopped by remembering to guard it. And an
# asset on a fixed tag has a fixed URL whose *contents* change, which is exactly what a source has
# to be: added to SideStore once, never again.
#
# Usage: make-source.sh <ipa> <version> <build> <download-url> <out.json>
set -euo pipefail

ipa="${1:?usage: make-source.sh <ipa> <version> <build> <download-url> <out.json>}"
version="${2:?missing version}"
build="${3:?missing build}"
url="${4:?missing download url}"
out="${5:?missing output path}"

[ -f "$ipa" ] || { echo "::error title=No ipa::$ipa does not exist"; exit 1; }

# wc rather than stat, whose flags for this differ between the BSD stat on the macOS runner and the
# GNU one everywhere else. SideStore rejects a size that disagrees with the download.
size="$(wc -c < "$ipa" | tr -d ' ')"
date="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
# set -u would abort on a bare $GITHUB_SHA outside Actions, and this script is worth running by hand
sha="$(echo "${GITHUB_SHA:-local}" | cut -c1-7)"

# `versions` is an array so a source can carry history; one entry is enough, and SideStore takes the
# first as current. `version` and `buildVersion` must match CFBundleShortVersionString and
# CFBundleVersion in the ipa exactly, or the update either never appears or never stops appearing —
# which is why the build stamps them from the same run number this does.
#
# The same version is then repeated in the flat `version`/`downloadURL`/`size` fields on the app
# itself. That is not redundancy for its own sake: `versions` is the newer shape, the flat fields
# are the original one, and a client reading only the old fields finds an app with no version at
# all — which is what "The latest version could not be determined" means. Writing both costs four
# lines and works whichever a given SideStore build looks for. Keep them in step.
#
# iconURL is required by the schema and there is no app icon yet, so it points at the GitHub avatar
# to keep the file valid. Swap it for a real icon when the app has one.
cat > "$out" <<JSON
{
  "name": "OpenBreath",
  "identifier": "io.github.wlemkens.openbreath.source",
  "subtitle": "Heart coherence breathing meditations",
  "website": "https://github.com/wlemkens/openbreath",
  "apps": [
    {
      "name": "OpenBreath",
      "bundleIdentifier": "io.github.wlemkens.openbreath",
      "developerName": "Wim Lemkens",
      "subtitle": "Heart coherence breathing meditations",
      "localizedDescription": "Breathing meditations with custom timings, soundscapes and progress tracking. Free software, GPL-3.0-or-later.",
      "iconURL": "https://github.com/wlemkens.png",
      "category": "lifestyle",
      "version": "$version",
      "versionDate": "$date",
      "versionDescription": "Build $build from $sha.",
      "downloadURL": "$url",
      "size": $size,
      "versions": [
        {
          "version": "$version",
          "buildVersion": "$build",
          "date": "$date",
          "downloadURL": "$url",
          "size": $size,
          "localizedDescription": "Build $build from $sha.",
          "minOSVersion": "15.0"
        }
      ],
      "appPermissions": { "entitlements": [], "privacy": {} }
    }
  ],
  "news": []
}
JSON

# a malformed source fails silently on the phone — SideStore just shows nothing — so it is worth
# catching here, where the log is readable
python3 -c "import json,sys; json.load(open(sys.argv[1]))" "$out"

echo "wrote $out advertising $version ($build), ipa $size bytes"
