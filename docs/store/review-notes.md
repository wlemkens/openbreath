# App Review Information — Notes

What goes in the **Notes** field of App Review Information in App Store Connect, every
submission. Build 70 went up with it empty, and the reply was a Guideline 2.1 "Information
Needed" letter asking for all seven items below — not a fault in the app, a fault in the
paperwork. It is kept here rather than only in the Console because the Console field is not
version-controlled and the answers change with the app: **anything that adds a permission
prompt, an outbound link or a paid anything makes this file wrong**, and a wrong answer here
is a rejection that costs a week.

One of the seven is not text at all: **item 1**, the recording, which is uploaded to the
Resolution Center. `video-script.md` beside this file is the shot list.

**The Notes field takes under 4000 characters**, which the first draft of this missed by more
than half — it was 9520. It is the only version kept, because two lengths of the same answers
would drift and the shorter one is what actually gets pasted. **Anything added has to buy its
space from something else**: the Rate link cost a sentence of item 3 and half of item 7's
sourcing. Check the count after editing — `awk '/^## Paste from here/{f=1;next} f&&/^```$/
{if(++n==2)exit; next} f' docs/store/review-notes.md | wc -m`.

**Item 2 goes stale, and silently.** It is a claim about machines that really ran the app, so it
has to be re-checked rather than re-pasted:

- The phone comes from whoever filmed the video. A simulator cannot stand in — Apple asks the
  question precisely because it is not a device.
- The two simulator lines come from the screenshot run and are honest only while labelled as
  simulators. `.github/ios-screenshots.sh` names the device and runtime it chose in the job
  log — `using the existing iPhone 17 Pro Max on iOS 26.2` — and the choice depends on what
  that runner's Xcode carried, so it changes under you without any diff to notice. The ones
  below are from run 94, 2026-08-27. Read the log rather than guessing: a model Apple can see
  was never booted is worse than a shorter list.

The rest is true of the app as it stands and is worth re-reading rather than re-pasting.

## The standing monetisation check

Item 5 declares the Support screen rather than waiting for a reviewer to find a PayPal button
and ask about it. The rule it lives under is **3.2.1(vii)**: optional, 100% to the receiver,
nothing given in return. That holds — no feature, theme, badge or acknowledgement is behind it,
and the screen says so in its own words. See the Monetisation section of CLAUDE.md; if that
check ever stops holding, this file and the submission are both wrong.

## These describe build 120, not build 70

**Build 70's Support buttons open a profile that does not exist.** Every build up to and
including it carried `paypal.me/wimlemkens`; the handle is `wlemkens`, and the wrong one answers
"We can't find this profile" on all three platforms. Build 120 carries the fix, and item 5 names
the corrected link — so send these with 120, and do not leave 70 anywhere a reviewer can reach
that screen. A link that goes nowhere is a Guideline 2.1 *bugs* rejection, a worse letter than
the information one this file answers.

Checked against 120's tree rather than 70's, because fifty commits sit between them. What
actually moved in the shipping app: the PayPal handle and its wording, two cue sliders in
Settings, and ± on the session length. `Info.plist` and the bundled audio are untouched, so no
new purpose string and no new asset — items 1, 5 and 7 stand as written.

`canRate` was false for build 120, hiding the Rate item. It is true now that the listing is
live, which adds a third outbound link, so item 5 says three — correct for the next build and
one more than build 120 actually had.

**Build 120 also carries a different marketing version from 70**, so App Store Connect files it
under version **1.0.120** and will not offer it in the build picker of the rejected 1.0.70
record. Change that record's Version field to 1.0.120 first, or conclude wrongly that the upload
never arrived.

Build 120 is the last one this happens to. The workflow stamped `1.0.<run number>` into both
halves of the build, which SideStore needs — it compares the advertised version against the
bundle on the phone — and the App Store does not: there, the marketing version is the *release*
and the build number climbs underneath it. The App Store job now stamps a stable `1.0`, so every
upload after this lands under one record with the build number rising, and there is no version
field to edit.

**Re-run that check against whatever build is actually submitted.** These notes describe a
binary, and the version they were written for stops being the version being sent the moment
another build goes up.

---

## Paste from here

```
1. SCREEN RECORDING

Attached. Physical iPhone 15, iOS 26.6, clean install of this build. It begins on the Home
screen with the app being launched from its icon, and runs the typical flow through to a
completed one-minute sitting and the log it writes.

There is no account, login, purchase, subscription, user-generated content or sharing, so
none of those flows appear. No permission prompt appears anywhere: the app declares no
purpose strings and needs none - the optional torch is device configuration, not capture,
so no capture session is created.

2. DEVICES AND OPERATING SYSTEMS TESTED

iPhone 15, iOS 26.6 - physical device, this build, via TestFlight.
iPhone 17 Pro Max and iPad Pro 13-inch (M5) simulators, both iOS 26.2 - automated UI runs.
Targets iPhone and iPad; deployment target iOS 15.0.

3. WHAT THE APP DOES, AND FOR WHOM

A paced breathing timer for coherence practice. You set the four phase lengths, or take a
preset, and follow a sphere that opens and closes with the breath, with an optional sound
at each turn so it can be followed with eyes shut. The problem it solves: counting breaths
yourself occupies the attention the practice is meant to free. Every sitting is logged, and
goals and streaks counted from it.

For anyone practising paced or coherence breathing: a general-audience wellness and timing
app, free, with no account and no network, so the log stays on the phone. It makes no
health or medical claim, treats nothing, and reads no sensor or health data.

4. SETUP AND ACCESS

No credentials and nothing to prepare: no account, sign-in, demo account, server or sample
files. Only one screen precedes use, and only on a phone the app has never been opened on:
"Welcome" offers a goal of one sitting a day with its single switch OFF, so Continue creates
nothing unless you turn it on. Everything else has a working default. Delete and reinstall
to see it again.

5. EXTERNAL SERVICES, TOOLS AND PLATFORMS

None deliver any functionality: no data provider, authentication, payment processor,
analytics, advertising SDK, crash reporter, AI service or backend of ours. Third-party code
is the Kotlin and Compose Multiplatform runtimes (Apache-2.0), carrying no service. The app makes no network request, which is also why
ITSAppUsesNonExemptEncryption is false.

Three links open Safari, and are the only outbound traffic:

- paypal.me/wlemkens, on the Support screen. An optional gift to the developer as an
  individual, under Guideline 3.2.1(vii); 100% goes to him. It grants no content, feature,
  badge or acknowledgement - the app is identical whether or not anyone uses it, and the
  screen says so. No payment is taken inside the app by any mechanism.
- A Google Forms page, the Feedback menu item, for writing to the developer. Google hosts
  the form and nothing else; the app sends it nothing.
- This app's own App Store page, from the Rate menu item.

No data is collected, matching PrivacyInfo.xcprivacy: no tracking, no collected data, one
required-reason API - NSPrivacyAccessedAPICategoryFileTimestamp, reason C617.1, the
settings store reading its own file's metadata to write atomically.

6. REGIONAL DIFFERENCES

None. Identical in every region and storefront: no geo-restriction, no region-gated
feature or content, no server that could vary by country. English only; dates and times
follow the device locale, which is formatting, not a feature difference.

7. REGULATED INDUSTRY AND THIRD-PARTY MATERIAL

Neither applies. A breathing timer is not a medical device or health service: no sensor, no
HealthKit, no diagnostic or therapeutic claim. The app is our own work, GPL-3.0-or-later,
source at github.com/wlemkens/openbreath.

The only assets not ours are two recorded singing bowls, from freesound_community on
Pixabay under CC0 1.0, a public domain dedication requiring no attribution or permission:
session_end.mp3 (60767), singing_bowl.mp3 (33366). Every other sound is synthesised at
runtime.
```
