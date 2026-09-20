# App Review Information — Notes

What goes in the **Notes** field of App Review Information in App Store Connect, every
submission. Build 70 went up with it empty, and the reply was a Guideline 2.1 "Information
Needed" letter asking for all seven items below — not a fault in the app, a fault in the
paperwork. It is kept here rather than only in the Console because the Console field is not
version-controlled and the answers change with the app: **anything that adds a permission
prompt, an outbound link, a paid anything or a language makes this file wrong**, and a wrong
answer here is a rejection that costs a week.

A language is on that list because it is the one that actually happened. Item 6 read "English
only" for a week after the app shipped in three, and nothing anywhere went red — the same shape
as item 2 going stale, and the reason this file is re-read rather than re-pasted.

One of the seven is not text at all: **item 1**, the recording, which is uploaded to the
Resolution Center. `video-script.md` beside this file is the shot list.

**The Notes field takes under 4000 characters**, which the first draft of this missed by more
than half — it was 9520. It is the only version kept, because two lengths of the same answers
would drift and the shorter one is what actually gets pasted. **Anything added has to buy its
space from something else**: the Rate link cost a sentence of item 3 and half of item 7's
sourcing, and the notification prompt in item 1 cost the rest of that sourcing and two clauses of
item 5's PayPal bullet. It stands at 3969, so there is room for one sentence and no more. Check
the count after editing — `awk '/^## Paste from here/{f=1;next} f&&/^```$/
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

## The build these describe, and what is owed with it

**Build 159, version 1.2, is on App Store Connect** as of 2026-09-19 — the three languages, the
milestones kept on the achievements screen, and one sound for the whole breath under Standard.
The notes below match that binary rather than running ahead of it.

**One recording exists, and it is older than the notes around it.** It was made on 2026-08-30 —
commit `a294117`, "Describe the recording that exists, not the one the script asked for" — on a
physical iPhone off build 120, and it answered the Guideline 2.1 letter for the 1.0.120 review.
**It predates reminders**, so the app's only permission prompt is not in it, and neither are the
first run's two switches. That is why this file spent a fortnight saying a refilm was owed.

1.1 shipped on 2026-09-12 without one, which settles what an update actually needs: nothing. The
2.1 letter belonged to the first review. But item 1 opened with **"Attached."** long after there
was anything to attach, so it now says where that recording went, **says out loud that it does not
show the prompt**, and offers a current one instead. Volunteering the gap is the same move item 5
makes with the PayPal link: a reviewer who watches the old take and finds no prompt has caught
item 1 lying, and there is no recovering from that in the same letter.

**Do not empty the field on the strength of that.** Six of the seven answers are not about the
video at all, and an empty Notes field is what earned the letter in the first place. The video is
one item; the field is the seven.

If a recording is ever attached again — because App Review asks, or because a release is worth
filming — item 1 opens with "Attached." again and `video-script.md` holds the shot list, with the
one beat that has changed since marked. **Film it off the build being submitted**, which is that
file's own rule and the reason build 150's take is not simply re-attached here: the Settings
screen has changed under it.

**Skip build 145**, still. It was the first with reminders in it and it carries a silent bug:
saying yes to the reminder on the first-run screen armed it without asking for notification
permission, so no prompt appeared and no notification ever arrived. Item 1 would be wrong of 145
in the one direction Apple asks about.

**Item 2's physical device is a claim about this build.** The iPhone 15 line was true of 150 off
TestFlight; install 159 on it before pasting, or say which build it really was. The two simulator
lines were re-checked against run 162 on 2026-09-19 — iPhone 17 Pro Max and iPad Pro 13-inch (M5),
both iOS 26.2 — and are as written.

## What build 120 needed, kept because a rejected version asks the same questions

**Build 70's Support buttons open a profile that does not exist.** Every build up to and
including it carried `paypal.me/wimlemkens`; the handle is `wlemkens`, and the wrong one answers
"We can't find this profile" on all three platforms. Build 120 carries the fix, and item 5 names
the corrected link, as does 150 — so do not leave 70 anywhere a reviewer can reach that screen.
A link that goes nowhere is a Guideline 2.1 *bugs* rejection, a worse letter than the information
one this file answers.

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

Build 120 is the last one this happens to, but the fix took two goes and the first one is worth
keeping. The workflow stamped `1.0.<run number>` into both halves of the build, which SideStore
needs — it compares the advertised version against the bundle on the phone — and the App Store
does not: there, the marketing version is the *release* and the build number climbs underneath it.

Stamping a stable `1.0` was the obvious answer and **altool refused it**: a marketing version has
to be *higher* than the last approved one, and 1.0.120 is approved and live, so 1.0 is a
downgrade. Build 141 died on it — "CFBundleShortVersionString [1.0] must contain a higher version
than that of the previously approved version [1.0.120]" — after which the number can only go
forward. It is `1.1` now, bumped by hand once per release in the `app-store` job's `VERSION` and
in `iosApp/project.yml`, with the build number climbing underneath it. **The version record in App
Store Connect has to exist for whatever this says**, so a release still starts by creating it
there.

Build 141 is also why a green `app-store` job is no longer proof of anything on its own:
**altool exited 0 on that failure**, printing "UPLOAD FAILED with 2 errors" as it went. The job now
decides on the log line "No errors uploading" instead, and puts the validation errors in
annotations, which are readable without a token. If this file is ever being read after a
submission that "arrived" and cannot be found, read the annotations rather than the job status.

**Re-run that check against whatever build is actually submitted.** These notes describe a
binary, and the version they were written for stops being the version being sent the moment
another build goes up.

---

## Paste from here

```
1. SCREEN RECORDING AND PERMISSIONS

A recording of the whole flow on a physical iPhone was sent to the Resolution Center for
this app's first review. It predates reminders, so it does not show the prompt described
below; a current one can be provided on request.

There is no account, login, purchase, subscription, user-generated content or sharing, so
none of those flows appear. One permission prompt exists: the standard iOS notification
alert, shown when a reminder is first added and only then; declining costs nothing but
reminders. The app declares no purpose strings and needs none - the optional torch is
device configuration, not capture, so no capture session is created.

2. DEVICES AND OPERATING SYSTEMS TESTED

iPhone 15, iOS 26.6 - physical device, this build, via TestFlight.
iPhone 17 Pro Max and iPad Pro 13-inch (M5) simulators, both iOS 26.2 - automated UI runs.
Targets iPhone and iPad; deployment target iOS 15.0.

3. WHAT THE APP DOES, AND FOR WHOM

A paced breathing timer for coherence practice. You set the four phase lengths, or take a
preset, and follow a sphere that opens and closes with the breath, with an optional sound
at each turn so it can be followed with eyes shut. Counting breaths occupies the attention
the practice is meant to free. Every sitting is logged, and goals and streaks counted
from it.

For anyone practising paced or coherence breathing: a general-audience wellness and timing
app, free, with no account and no network, so the log stays on the phone. It makes no
health or medical claim and reads no sensor or health data.

4. SETUP AND ACCESS

No credentials and nothing to prepare: no account, sign-in, demo account, server or sample
files. One screen precedes use, only on a phone the app has never been opened on:
"Welcome" offers a goal of one sitting a day and an evening reminder for the days it has
not happened, both switches OFF, so Continue creates nothing unless one is turned on.
Everything else has a working default. Delete and reinstall to see it again.

5. EXTERNAL SERVICES, TOOLS AND PLATFORMS

None deliver any functionality: no data provider, authentication, payment processor,
analytics, advertising SDK, crash reporter, AI service or backend of ours. Third-party code
is the Kotlin and Compose Multiplatform runtimes (Apache-2.0), carrying no service. The app makes no network request, which is also why
ITSAppUsesNonExemptEncryption is false.

Three links open Safari, and are the only outbound traffic:

- paypal.me/wlemkens, on the Support screen. An optional gift to the developer as an
  individual, under Guideline 3.2.1(vii); 100% goes to him. It grants no content, feature
  or badge - the app is identical whether or not anyone uses it. No payment is taken
  inside the app.
- A Google Forms page, the Feedback menu item, for writing to the developer. Google hosts
  the form and nothing else; the app sends it nothing.
- This app's own App Store page, from the Rate menu item.

No data is collected, matching PrivacyInfo.xcprivacy: no tracking, no collected data, one
required-reason API - NSPrivacyAccessedAPICategoryFileTimestamp, reason C617.1, the
settings store reading its own file's metadata to write atomically.

6. REGIONAL DIFFERENCES

None. Identical in every region and storefront: no geo-restriction, no region-gated
feature or content, no server that could vary by country. English, Dutch and French,
following the device language; dates and times follow the device locale. Wording and
formatting, not a feature difference.

7. REGULATED INDUSTRY AND THIRD-PARTY MATERIAL

Neither applies. A breathing timer is not a medical device or health service: no sensor, no
HealthKit, no diagnostic or therapeutic claim. The app is our own work, GPL-3.0-or-later,
source at github.com/wlemkens/openbreath.

The only assets not ours are two recorded singing bowls from freesound_community on Pixabay
under CC0 1.0, requiring no attribution. Every other sound is synthesised.
```
