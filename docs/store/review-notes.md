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

## Item 5 describes the next build, not build 70

**Build 70's Support buttons open a profile that does not exist.** Every build up to and
including it carried `paypal.me/wimlemkens`; the handle is `wlemkens`, and the wrong one answers
with "We can't find this profile" on all three platforms. It is fixed in the source, and item 5
below names the corrected link — so these notes belong with a build that has the fix.

Do not send them with build 70, and do not leave build 70 where a reviewer can reach that
screen. A link that goes nowhere is a Guideline 2.1 *bugs* rejection, which is a worse letter
than the information one this file answers.

---

## Paste from here

```
1. SCREEN RECORDING

Attached. Captured on a physical iPhone 15 running iOS 26.6. It begins on the Home screen with
the app being launched from its icon, and follows the typical flow from there: the main screen
and its phase timings, a breathing session started and running, and the Goals, Achievements and
Settings screens, with settings changed on camera so that their effect can be seen.

Three things are not in the recording. They are named here rather than left for you to notice:

  - The breathing session is ended early rather than run to its full length, to keep the
    recording short. Ending early is a normal path and not a failure — the sitting is still
    written to the log, with the breaths actually taken.
  - The first-launch "Welcome" screen does not appear, because the app had already been opened
    once on that phone. It is described in item 4, and deleting and reinstalling brings it back.
  - The Support screen is not opened. It is described in item 5. Nothing is sold anywhere in
    this app: that screen carries an optional link which opens PayPal in Safari, and no payment
    is taken inside the app by any mechanism. We will gladly send a second clip of it if you
    would like to see it.

The app has no account, no login, no purchases, no subscriptions, no user-generated content and
no sharing between users, so none of those flows appear in it — there are none to show.

It also requests no permissions, and no system prompt appears anywhere in the recording. This
is not an omission: the app declares no purpose strings at all, because it needs none. It makes
no network connection, reads no contacts or location, and the optional torch is configuration
on the capture device rather than capture, so no AVCaptureSession is created and
NSCameraUsageDescription does not apply. Verified on a device: no prompt appears.

2. DEVICES AND OPERATING SYSTEMS TESTED

  - iPhone 15, iOS 26.6 — physical device, via TestFlight; the build submitted
  - iPhone 17 Pro Max simulator, iOS 26.2 — automated UI run, which took the 6.9" screenshots
  - iPad Pro 13-inch (M5) simulator, iOS 26.2 — automated UI run, which took the 13" screenshots

The app targets iPhone and iPad, deployment target iOS 15.0. Nothing is laid out separately
for a tablet; the breathing cue takes the space it is given.

3. WHAT THE APP DOES, AND FOR WHOM

OpenBreath is a paced breathing timer for coherence practice.

The problem: breathing at a slow, even, fixed pace is easy to describe and hard to do, because
counting occupies the attention the practice is meant to free. Watching a clock is worse.

What it does: you set how long to breathe in, hold, breathe out and hold — or take one of the
presets (coherence 5.5, 4-6, 4-7-8, box 4) — and then follow a sphere that opens as you breathe
in and closes as you breathe out, with an optional sound at each turn so it can be followed
with the eyes shut. It keeps a log of every sitting, from which it counts goals, streaks and
milestones.

Audience: anyone practising paced or coherence breathing — people who already know the patterns
and want a timer that is not a subscription, and beginners who want the presets. It is a
general-audience wellness and timing app.

The value: it is free, it has no account, and it makes no network connection of any kind, so a
practice log stays on the phone. Nothing is behind a payment, and there is nothing to unlock.

It makes no health or medical claim. It does not diagnose, treat or measure anything, it reads
no sensor and no health data, and neither the app nor its store copy says that breathing treats
any condition. It is a timer with a visual and audible pace.

4. SETUP AND ACCESS

No credentials of any kind. There is no account, no sign-in, no demo account, no server and no
sample files, so there is nothing to give you to log in with and nothing to prepare beforehand.

One screen can stand between launching the app and using it, on a phone the app has never been
opened on. It is not in the attached recording — that was filmed on a phone where the app had
already been launched once — so it is described here instead:

  - "Welcome" says that a breathing meditation every day is what we would recommend, and offers
    to set that as a goal. There is a single switch, "A goal of one sitting a day", and it
    starts OFF: the recommendation is stated, not applied. Tapping Continue with it off creates
    nothing and is a complete answer. Either way it can be changed later from the Goals screen.
    Deleting the app and installing it again shows the screen, if you would like to see it.

Nothing else is required. Every setting has a working default and the app is fully usable
without opening Settings at all; everything below is optional.

  - Main screen: the four phase timings and the session length, a preset row, and Start.
  - Start begins a four-second count-in, then the sitting. The sphere opens and closes with the
    breath. Tapping the screen pauses; a button ends the sitting early, and a short sitting is
    still logged.
  - The ⋮ menu on the main screen reaches everything else: Settings, Goals, Achievements, Log,
    Feedback and Support the app.
  - Settings has a Standard and an Advanced tab: cue shape and colour, the sound for each phase,
    the session-end bowl, haptics, the torch, the count-in length, what the session screen
    shows, saving and renaming presets, and a Backup section.
  - Backup's Export writes one JSON file through the system document picker, and Import reads
    one back. That is the only way data enters or leaves the app.

5. EXTERNAL SERVICES, TOOLS AND PLATFORMS

None deliver the app's functionality. There are no data providers, no authentication service,
no payment processor, no analytics, no advertising SDK, no crash reporter, no AI or machine
learning service, and no backend of ours. The app makes no network request; every feature —
the timings, the cue, the synthesised sounds, the log, the goals — is computed on the device.
That is also why ITSAppUsesNonExemptEncryption is false: there is no connection to encrypt.

Two links open Safari when the user taps them, and are the app's only outbound traffic:

  - paypal.me/wlemkens — on the Support screen. An optional gift to the developer as an
    individual, under Guideline 3.2.1(vii). 100% goes to him. It grants no content, feature,
    badge or acknowledgement of any kind; the app is identical whether or not anyone uses it,
    and the screen says so. No payment is taken inside the app by any mechanism.
  - A Google Forms page — the Feedback item in the menu, so a user can write to the developer
    without composing an email. Google hosts that form and nothing else; the app itself sends it
    nothing, and the form is opened in the browser rather than embedded.

Third-party code in the binary is open source and carries no service: the Kotlin and Compose
Multiplatform runtimes and their libraries, all Apache-2.0. None of them phones home, and there
is no SDK in the app that talks to anyone.

The app collects no data. This matches PrivacyInfo.xcprivacy, which declares no tracking and no
collected data, and one required-reason API — NSPrivacyAccessedAPICategoryFileTimestamp, reason
C617.1, which is the settings store reading the metadata of its own file in the app container
in order to write it atomically.

6. REGIONAL DIFFERENCES

None. The app functions identically in every region and storefront. There is no
geo-restriction, no region-gated feature, no regional content, and no server that could vary by
country.

The interface is English only. Dates and times follow the device's own locale — first day of
the week, day names, and 12- or 24-hour clock — which is formatting, not a difference in
features.

7. REGULATED INDUSTRY AND THIRD-PARTY MATERIAL

Neither applies.

Not a regulated industry: this is a breathing timer, not a medical device or health service. It
reads no sensor and no HealthKit data, makes no diagnostic or therapeutic claim, and provides no
regulated service of any kind.

No protected third-party material. The app is our own work, released under the GNU General
Public License v3 or later, source public at github.com/wlemkens/openbreath.

The two recorded singing-bowl sounds in the bundle are the only assets not written by us. Both
are cut from files published by the freesound_community account on Pixabay under CC0 1.0, a
public domain dedication that permits commercial use and requires no attribution or permission:

  - session_end.mp3 — from "025535 singing bowl" (Pixabay 60767)
  - singing_bowl.mp3 — from "singing bowl hit 3" (Pixabay 33366)

Every other sound in the app is synthesised arithmetically at runtime and is not a recording of
anything.
```
