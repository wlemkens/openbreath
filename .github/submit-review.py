#!/usr/bin/env python3
"""Put a build in front of App Review, with the words that go with it.

Everything that used to be typed into App Store Connect by hand before a submission, done from the
files that already hold it:

- the version record, named after MARKETING_VERSION in iosApp/project.yml — created, or an editable
  one renamed, which is what an upload filed under a version that was later changed leaves behind;
- What's New in every language the listing has, from docs/store/listing{,.nl,.fr}.md;
- the App Review Notes, from docs/store/review-notes.md, the block its own count command reads;
- the build, then the submission itself.

Which build: BUILD when this run uploaded one — it is waited for, since a fresh upload spends a
while processing and a while before that not being listed at all — and otherwise the newest valid
build of the release, which is how a build already up gets submitted without a rebuild.

Standard library and the openssl binary only, so the runner installs nothing. Failures print as
`::error` lines on purpose: those become annotations, which can be read without a token.

Env: ASC_KEY_P8 (base64 of the .p8, as the upload step takes it), ASC_KEY_ID, ASC_ISSUER_ID, BUILD.
"""
import base64
import json
import os
import re
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.request

APP = "6805899911"
API = "https://api.appstoreconnect.apple.com/v1"
EDITABLE = {"PREPARE_FOR_SUBMISSION", "DEVELOPER_REJECTED", "REJECTED", "METADATA_REJECTED",
            "INVALID_BINARY"}
IN_REVIEW = {"WAITING_FOR_REVIEW", "IN_REVIEW", "PENDING_DEVELOPER_RELEASE"}


def fail(msg):
    print(f"::error title=App Store submission::{msg}")
    sys.exit(1)


def b64url(data):
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode()


def der_to_raw(der):
    # ES256 wants r||s, 32 bytes each; openssl gives SEQUENCE { INTEGER r, INTEGER s }. A signature
    # this size is always short-form lengths, so the walk is two reads
    i = 2
    out = b""
    for _ in range(2):
        n = der[i + 1]
        out += der[i + 2:i + 2 + n].lstrip(b"\0").rjust(32, b"\0")
        i += 2 + n
    return out


def token():
    head = b64url(json.dumps({"alg": "ES256", "kid": os.environ["ASC_KEY_ID"], "typ": "JWT"}).encode())
    now = int(time.time())
    body = b64url(json.dumps({"iss": os.environ["ASC_ISSUER_ID"], "iat": now, "exp": now + 900,
                              "aud": "appstoreconnect-v1"}).encode())
    with tempfile.NamedTemporaryFile("wb", suffix=".p8") as key:
        key.write(base64.b64decode(os.environ["ASC_KEY_P8"]))
        key.flush()
        der = subprocess.run(["openssl", "dgst", "-sha256", "-sign", key.name],
                             input=f"{head}.{body}".encode(), capture_output=True, check=True).stdout
    return f"{head}.{body}.{b64url(der_to_raw(der))}"


def call(method, path, body=None, ok=()):
    """One request, a fresh token each time: the wait for processing outlives any single one."""
    req = urllib.request.Request(
        path if path.startswith("http") else API + path, method=method,
        data=json.dumps(body).encode() if body is not None else None,
        headers={"Authorization": f"Bearer {token()}", "Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(req) as r:
            text = r.read()
            return json.loads(text) if text else {}
    except urllib.error.HTTPError as e:
        if e.code in ok:
            return None
        detail = e.read().decode(errors="replace")
        try:
            detail = "; ".join(f"{x.get('title')}: {x.get('detail')}"
                               for x in json.loads(detail).get("errors", []))
        except ValueError:
            pass
        fail(f"{method} {path.split('?')[0]} answered {e.code} — {detail}")


def rel(kind, id_):
    return {"data": {"type": kind, "id": id_}}


# ---- the words ----------------------------------------------------------------------------------

def block_after(path, heading):
    """The indented block under the first line starting with `heading`, unwrapped: the files are
    hard-wrapped for reading and the stores keep line breaks, so a paragraph becomes one line."""
    lines = open(path, encoding="utf-8").read().splitlines()
    start = next((i for i, l in enumerate(lines) if l.startswith(heading)), None)
    if start is None:
        fail(f"{path} has no line starting {heading!r}")
    paras, cur = [], []
    for l in lines[start + 1:]:
        if l.startswith("    "):
            cur.append(l.strip())
        elif not l.strip():
            if cur:
                paras.append(" ".join(cur))
                cur = []
        elif paras or cur:
            break
    if cur:
        paras.append(" ".join(cur))
    return "\n\n".join(paras)


def review_notes():
    text = open("docs/store/review-notes.md", encoding="utf-8").read()
    m = re.search(r"^## Paste from here.*?\n```[^\n]*\n(.*?)\n```", text, re.S | re.M)
    if not m:
        fail("docs/store/review-notes.md has no fenced block under '## Paste from here'")
    return m.group(1)


def version():
    m = re.search(r'MARKETING_VERSION:\s*"([\d.]+)"', open("iosApp/project.yml").read())
    return m.group(1) if m else fail("no MARKETING_VERSION in iosApp/project.yml")


# ---- the steps ----------------------------------------------------------------------------------

def find_build(release, number):
    """Waits up to 90 minutes: listing takes minutes after an upload, processing longer."""
    for _ in range(90):
        builds = call("GET", f"/builds?filter[app]={APP}&filter[preReleaseVersion.version]={release}"
                             "&sort=-uploadedDate&limit=20&fields[builds]=version,processingState")["data"]
        if number:
            builds = [b for b in builds if b["attributes"]["version"] == number]
        if builds:
            b = builds[0]
            state = b["attributes"]["processingState"]
            print(f"build {b['attributes']['version']} of {release}: {state}")
            if state == "VALID":
                return b
            if state in ("FAILED", "INVALID"):
                fail(f"build {b['attributes']['version']} of {release} is {state}")
        else:
            print(f"no build {number or ''} of {release} listed yet")
        time.sleep(60)
    fail(f"build {number or ''} of {release} did not become valid within 90 minutes")


def find_version(release):
    """The record for `release`, made if need be. Only one editable version exists at a time, so
    one filed under another name — an upload before the release was renamed — is renamed."""
    versions = call("GET", f"/apps/{APP}/appStoreVersions?filter[platform]=IOS&limit=20"
                           "&fields[appStoreVersions]=versionString,appStoreState")["data"]
    for v in versions:
        if v["attributes"]["versionString"] == release:
            return v
    for v in versions:
        if v["attributes"]["appStoreState"] in EDITABLE:
            print(f"renaming the editable version {v['attributes']['versionString']} to {release}")
            return call("PATCH", f"/appStoreVersions/{v['id']}", {"data": {
                "type": "appStoreVersions", "id": v["id"],
                "attributes": {"versionString": release}}})["data"]
    print(f"creating version {release}")
    return call("POST", "/appStoreVersions", {"data": {
        "type": "appStoreVersions",
        "attributes": {"platform": "IOS", "versionString": release},
        "relationships": {"app": rel("apps", APP)}}})["data"]


def fill_whats_new(v):
    words = {
        "en": block_after("docs/store/listing.md", "**This release"),
        "nl": block_after("docs/store/listing.nl.md", "**Nieuw in deze versie**"),
        "fr": block_after("docs/store/listing.fr.md", "**Nouveautés**"),
    }
    for lang, text in words.items():
        if len(text) > 4000:
            fail(f"What's New ({lang}) is {len(text)} characters; the App Store keeps 4000")
    locs = call("GET", f"/appStoreVersions/{v['id']}/appStoreVersionLocalizations")["data"]
    for loc in locs:
        locale = loc["attributes"]["locale"]
        text = words.get(locale.split("-")[0])
        if text is None:
            print(f"::warning title=No What's New::{locale} has no listing file to take it from")
            continue
        call("PATCH", f"/appStoreVersionLocalizations/{loc['id']}", {"data": {
            "type": "appStoreVersionLocalizations", "id": loc["id"],
            "attributes": {"whatsNew": text}}})
        print(f"What's New, {locale}: {len(text)} characters")


def fill_review_notes(v):
    notes = review_notes()
    if len(notes) >= 4000:
        fail(f"review notes are {len(notes)} characters; the field keeps under 4000")
    detail = call("GET", f"/appStoreVersions/{v['id']}/appStoreReviewDetail", ok=(404,))
    detail = detail and detail.get("data")
    if detail:
        call("PATCH", f"/appStoreReviewDetails/{detail['id']}", {"data": {
            "type": "appStoreReviewDetails", "id": detail["id"], "attributes": {"notes": notes}}})
    else:
        # a new record starts without one; the contact details are the previous version's
        attrs = {"notes": notes}
        for old in call("GET", f"/apps/{APP}/appStoreVersions?filter[platform]=IOS&limit=5")["data"]:
            prev = call("GET", f"/appStoreVersions/{old['id']}/appStoreReviewDetail", ok=(404,))
            if prev and prev.get("data"):
                keep = ("contactFirstName", "contactLastName", "contactPhone", "contactEmail",
                        "demoAccountRequired")
                attrs.update({k: prev["data"]["attributes"].get(k) for k in keep})
                break
        call("POST", "/appStoreReviewDetails", {"data": {
            "type": "appStoreReviewDetails", "attributes": attrs,
            "relationships": {"appStoreVersion": rel("appStoreVersions", v["id"])}}})
    print(f"review notes: {len(notes)} characters")


def submit(v):
    open_ = call("GET", f"/reviewSubmissions?filter[app]={APP}&filter[platform]=IOS"
                        "&filter[state]=READY_FOR_REVIEW&limit=1")["data"]
    sub = open_[0] if open_ else call("POST", "/reviewSubmissions", {"data": {
        "type": "reviewSubmissions", "attributes": {"platform": "IOS"},
        "relationships": {"app": rel("apps", APP)}}})["data"]
    # 409 is the version already being an item of it, which is where a rerun finds it
    call("POST", "/reviewSubmissionItems", {"data": {
        "type": "reviewSubmissionItems",
        "relationships": {"reviewSubmission": rel("reviewSubmissions", sub["id"]),
                          "appStoreVersion": rel("appStoreVersions", v["id"])}}}, ok=(409,))
    call("PATCH", f"/reviewSubmissions/{sub['id']}", {"data": {
        "type": "reviewSubmissions", "id": sub["id"], "attributes": {"submitted": True}}})


def main():
    release = version()
    v = find_version(release)
    state = v["attributes"].get("appStoreState")
    if state in IN_REVIEW:
        print(f"{release} is already {state}; nothing to do")
        return
    if state not in EDITABLE:
        fail(f"{release} is {state} — a shipped version cannot be resubmitted; bump MARKETING_VERSION")
    build = find_build(release, os.environ.get("BUILD") or None)
    fill_whats_new(v)
    fill_review_notes(v)
    call("PATCH", f"/appStoreVersions/{v['id']}/relationships/build", rel("builds", build["id"]))
    print(f"attached build {build['attributes']['version']}")
    submit(v)
    print(f"{release} ({build['attributes']['version']}) submitted for review")


if __name__ == "__main__":
    main()
