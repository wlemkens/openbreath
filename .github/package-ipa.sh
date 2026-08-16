#!/usr/bin/env bash
# Wrap a built .app into an unsigned .ipa.
#
# An .ipa is a zip with the bundle under Payload/ and nothing else, which is the whole reason this
# can be done without a certificate: signing is a property of the bundle, not of the container.
# AltStore and SideStore re-sign with the free Apple ID they are given, so shipping an unsigned
# container is not a shortcut — it is what those tools expect, and a CI signature would be thrown
# away by them anyway.
#
# Usage: package-ipa.sh <path-to-.app> <output.ipa>
set -euo pipefail

app="${1:?usage: package-ipa.sh <app> <ipa>}"
out="${2:?usage: package-ipa.sh <app> <ipa>}"

[ -d "$app" ] || { echo "::error title=No app bundle::$app does not exist"; exit 1; }

staging="$(mktemp -d)"
mkdir -p "$staging/Payload"
cp -R "$app" "$staging/Payload/"

out_abs="$(cd "$(dirname "$out")" && pwd)/$(basename "$out")"
(cd "$staging" && zip -qry "$out_abs" Payload)
rm -rf "$staging"

echo "wrote $out_abs ($(du -h "$out_abs" | cut -f1))"
