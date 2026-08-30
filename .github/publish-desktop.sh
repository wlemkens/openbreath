#!/usr/bin/env bash
# Put one desktop installer on the rolling `latest` prerelease, under a name that never changes.
#
# An upload-artifact needs a GitHub login to download, so it is useless as a link to hand anyone —
# the same reason the ipa goes to a release asset rather than an artifact. A release asset is public
# and its URL is guessable, which is the whole point: the README can name it once.
#
# Called three times over, from the one `publish` job — once per installer, since nothing but the
# filename differs between them. It used to be called once from each of the three jobs that built
# one, which is what raced; see the comment on that job.
#
# The glob is what jpackage wrote, which now arrives via download-artifact rather than sitting in
# the build directory. The script does not care which, and that is why it did not have to change.
#
#   publish-desktop.sh <glob jpackage wrote> <fixed asset name>
set -euo pipefail

# CI only, and learned the hard way: run by hand in a checkout that still holds an old build
# directory, this puts a laptop's binary on the public download link — the same thing
# OPENBREATH_BUILD exists to prevent on the version string.
[ -n "${GITHUB_ACTIONS:-}" ] || { echo "publish-desktop.sh runs in CI, not on a laptop"; exit 1; }

src="$1"
name="$2"

# Unquoted on purpose: the caller passes a glob, because packageVersion carries the build number and
# the built filename therefore changes every push.
# shellcheck disable=SC2086
file="$(ls -1 $src 2>/dev/null | head -1 || true)"
[ -n "$file" ] || { echo "::error title=No installer::nothing matched $src"; exit 1; }

# A URL that moves every push is not a download link, so the versioned file is copied to a fixed
# name and clobbered rather than kept. The ipa needs per-version names because SideStore checks the
# version it cached against the one it was promised; a human clicking a link does not.
out="$(dirname "$file")/$name"
cp "$file" "$out"

# Three jobs race to create it and the ios job may have got there first, so a failure to create is
# only interesting if the upload then fails too.
gh release view latest >/dev/null 2>&1 || gh release create latest \
  --prerelease \
  --title "Latest test build" \
  --notes "Rolling builds. Not a release." || true

gh release upload latest "$out" --clobber

{
  echo "### Download"
  echo "https://github.com/$GITHUB_REPOSITORY/releases/download/latest/$name"
} >> "$GITHUB_STEP_SUMMARY"
