# Overview
This project is an Android apllication for doing heart coherence breathing meditations.

Functionality includes:
- custom timing of the in, hold, out and hold phases.
- custom soundscapes for the different phases:
  - a sound duing each phase
  - or a sound at the end of each phase: singing bowl, bell or metronome tick, pitched by the
    turn it marks so going into a breath in does not sound like going out
  - the end sounds can be the users own mp3s
- optional automatic disabling of notifications during the meditation
- optional vibration during at the end of each phase
- a visual queue for breathing in and out. 
  - during the breathing in phase it is an expanding sphere
  - during the breathing out, the sphere gets smaller
- Some progress tracking, including streaks and badges
- Alarm to remind you to take the exersises. Several alarms can be configured.

  # Licensing
  The code is GPL-3.0-or-later — see LICENSE. Copyright (c) 2026 Wim Lemkens.

  `LICENSE-EXCEPTION` adds one permission under GPLv3 §7: conveying through an app store
  whose terms bind recipients in ways the GPL forbids. Without it the App Store build is a
  licence breach — that is what VLC was pulled over — and only the copyright holder can
  grant it. It is written for any store, not for Apple by name, and it gives away nothing
  about the source. Leave it in place; an iOS release depends on it.

  Every dependency has to stay GPL-compatible: the AndroidX and Kotlin stack is Apache-2.0,
  which is fine one-way into GPLv3. Adding anything under a proprietary or GPL-incompatible
  licence would make the combined app undistributable, so check before pulling one in.

  The bundled audio is **not** covered by that grant. `app/src/commonMain/composeResources/files/*.mp3` are cuts
  of files in `media/` from the `freesound_community` Pixabay account, released under
  **CC0 1.0** (public domain dedication). CC0 requires no attribution, so nothing has to
  ship in the app, but we credit the source anyway in README and here:

  - `files/session_end.mp3` — cut from `media/freesound_community-025535_singing-bowl-60767.mp3`
  - `files/singing_bowl.mp3` — cut from `media/freesound_community-singing-bowl-hit-3-33366.mp3`

  `media/alex_jauk-zen-gong-199844.mp3` is by a different Pixabay uploader and is **not**
  shipped in the APK. Confirm its terms separately before using it.

  # Details
  ## Monetisation
  - The app must never give anything in return for a payment. Not a feature, not a theme, not
    a badge, not a thank-you that only donors see. OpenBreath is free and stays free, and the
    Support screen asks without offering.

  This is also the whole of what keeps the in-app PayPal link legal on Play. Its Payments
  policy treats a tip as a peer-to-peer payment needing no Play Billing *only* while 100% goes
  to the developer and the payment "does not grant access to any digital content or services
  (including stickers, badges, special emojis etc.)"
  — https://support.google.com/googleplay/android-developer/answer/10281818, and
  https://support.google.com/googleplay/android-developer/answer/9858738 in the policy centre.

  So it is a standing check, not a one-off: any feature that rewards, unlocks, thanks or ranks
  by payment breaks it, and any wording that hints at one invites the question. Whenever the
  Support screen, the store listing, or anything resembling a perk is touched, say out loud
  whether it still holds — and if a paid tier is ever genuinely wanted, that is Play Billing,
  not a link.

  ### The same link on the App Store
  The rule that governs it is **3.2.1(vii)**, the monetary-gift one, and not the charitable
  donation rules — a tip to the developer is a gift between people, not a fundraiser for a cause:

  > Apps may enable individual users to give a monetary gift to another individual without using
  > in-app purchase, provided that (a) the gift is a completely optional choice by the giver, and
  > (b) 100% of the funds go to the receiver of the gift. However, a gift that is connected to or
  > associated at any point in time with receiving digital content or services must use in-app
  > purchase.

  Read the two conditions and then read the Monetisation rule above: they are the same rule.
  Optional, all of it to the receiver, nothing given back. Play's peer-to-peer exemption asks for
  exactly this, word for word in places, which is why one standing check covers both stores.

  Two things follow that are specific to iOS.

  **A gift is to an individual.** If the VZW ever publishes the app and the money goes to the
  VZW, this guideline stops applying — an organisation is not "another individual" — and the app
  lands in 3.2.1(vi)/3.2.2(iv) charity territory instead, which needs approved-nonprofit status,
  Apple Pay support, a disclosure of how funds are used and donor tax receipts. The publisher
  decision and the Support screen are therefore the same decision. See the iOS port notes.

  **Outside the US storefront**, 3.1.1(a) bars "buttons, external links, or other calls to action
  that direct customers to purchasing mechanisms other than in-app purchase". A gift is not a
  purchase and 3.2.1(vii) permits it without in-app purchase, so the link stands — but this is
  the one sentence a reviewer could reach for, and it is worth knowing before an appeal rather
  than during one.

  In-app purchase is *also* allowed for tipping — 3.1.1 says apps "may use in-app purchase
  currencies to enable customers to 'tip' the developer". It is not wanted here: Apple would take
  its cut, so 100% would no longer reach the receiver, and the Play side rests on that 100%.

  What must not happen is a payment sheet of our own inside the app. The moment money is taken
  in the app by any mechanism that is not Apple's, both stores stop reading it as a gift.

  Guideline numbers move between revisions — check the text rather than the number, at
  https://developer.apple.com/app-store/review/guidelines/

  ## Technical
  - History, especcialy goals and progress should always be kept intact over updates.

  Presets and settings can be re-entered in a minute. A year of sittings cannot, so the log,
  the goals and the celebrated-milestone mark outrank every other consideration in this file.
  `app/src/androidUnitTest/kotlin/.../StorageTest.kt` holds strings that older versions really wrote and
  decodes them with today's code. Add to it whenever the stored shape changes, and never edit
  an existing string to make a red test pass — the phone in someone's pocket still holds it.

  Three ways this has actually been broken, all of them silent:

  - **Renaming a stored enum constant.** `SoundMode.WAVES` → `AMBIENT` and
    `AmbientVoice.DRONE` → `SOUNDWAVE` both turned every stored value into the default,
    because `coerceInputValues` cannot know what the old name meant. Renaming a constant that
    reaches DataStore means writing a migration, or accepting the loss on purpose.
  - **Moving a field to another key.** Goals lived in the `config` blob before they got their
    own `goals` key, and everything stored under the old name was simply gone. Read the old
    key, write the new one, and only then stop reading the old.
  - **Building a list on a value that has not loaded yet.** `collectAsState(initial = emptyList())`
    hands a screen "no goals" a frame before the real ones arrive, and saving from that state
    writes the emptiness over the lot. Start such state at null and wait.

  Adding a field with a default is always safe; `ignoreUnknownKeys` and per-field defaults do
  the rest. Removing or renaming one never is.

  One gap is left open on purpose: `android:allowBackup` is true, so Android may restore an
  older copy of the log and goals onto a fresh install, and nothing in the app can detect it.
  That was weighed against carrying a practice log to a new phone, and the carrying won. It is
  a decision, not an oversight — the manifest says so too.

  ### Export and import
  `Backup.kt` writes the log, config, goals, celebrated mark and reminders to one JSON file the
  user keeps. It is the only way anything leaves the phone, and the only route to an iPhone,
  since `allowBackup` carries a log to a new Android handset and nowhere else. It also answers
  the gap above: a restore that quietly puts back an older copy is survivable if there is a
  file of your own.

  An exported file is a stored shape, and every rule in this section applies to it. A backup
  saved today must keep opening forever — `StorageTest` holds a literal exported file for the
  same reason it holds the DataStore strings.

  **The log merges, everything else replaces.** That asymmetry is the design, not an
  oversight. Sittings union on `Entry.at` and the result is order-independent, so importing an
  old export onto a phone that has since been practised on keeps both sets and the log can only
  grow. `celebrated` takes the larger, or an already-congratulated milestone is announced
  twice. Presets, goals and reminders are a minute's work to retype, so the file wins.

  Import is one `edit` for the lot. A half-applied import that took the settings and lost the
  log is the worst outcome the app can produce, so it is all of it or none.

  ### The first run
  `FirstRun.kt` asks once whether to set a goal of one sitting a day and an evening reminder for
  the days it has not happened. Both switches start **off**: the recommendation is said, not
  applied, and an app that sets goals for someone who never asked has decided how they practise.

  A first run is "nothing has ever been stored in this DataStore" — `Store.untouchedFlow()` — and
  not a flag of its own, because a phone updating from a version that predates the question has no
  flag either, and asking someone with a year of sittings whether they would like to begin is
  worse than never asking. The `setupDone` key exists only to be written; nothing reads it.

  The two offers are one offer. The reminder is `onlyIfBehind`, which is worth having because the
  goal defines behind; declining the goal leaves it a plain evening reminder, which is the honest
  reading. A test in `SessionTest` holds them together.

  ## The iOS port
  The module is Kotlin Multiplatform with Compose Multiplatform, targeting `androidTarget()`
  plus `iosArm64` and `iosSimulatorArm64`. There is no `iosX64`: Compose Multiplatform stopped
  publishing it at 1.11, so the Intel-Mac simulator is gone. Sources live in `src/commonMain`,
  `src/androidMain`, `src/iosMain`, `src/commonTest` and `src/androidUnitTest` — the old
  `src/main/java` and `src/test/java` are gone, and an editor left open on a moved file will
  happily recreate it.

  **Kotlin must stay at 2.3.20 or above, and that is an iOS constraint alone.** Every Compose
  Multiplatform 1.11.1 klib — runtime, ui, foundation, animation — is ABI 2.3.0, built by the
  2.3.20 compiler, and a Kotlin below that refuses to read them. Android is unaffected because it
  consumes the `.aar`, where JVM bytecode carries no such gate, which is exactly why the mismatch
  sat in the build file unnoticed for as long as the iOS targets were declared but never once
  compiled. Downgrading Kotlin, or upgrading Compose past what the Kotlin version can read, breaks
  the phone build and nothing else — so it fails only in the job most likely to be skipped.

  Compiling for iOS does **not** need Xcode: Kotlin/Native ships a prebuilt
  `kotlin-native-prebuilt-macos-*` distribution, so `compileKotlinIosArm64` and
  `compileTestKotlinIosArm64` run on a Mac with only the Command Line Tools — an Intel one
  included. Only *linking* a framework and running a simulator need Xcode. Type-check iOS code
  locally with those two tasks; do not wait for CI to find a typo.

  **Linking needs Xcode 26, not merely some Xcode.** Compose Multiplatform 1.11.1 is built
  against the iOS 26 SDK: `ui-uikit` references `UIViewLayoutRegion`, which does not exist in
  Xcode 16, and the link fails on an undefined Objective-C class inside a Compose object file
  rather than on anything here. GitHub's macos-15 image carries several Xcode 26.x but still
  defaults to 16.4, so the workflow selects one.

  That is what finally settles the hardware question. Xcode 26 needs macOS 15, which no Intel Mac
  before 2019 can run, so the split is not a preference: type-check locally on whatever Mac is to
  hand, link and test on CI. An Apple Silicon machine collapses the two, and nothing else does.

  Kotlin/Native builds Apple targets on a macOS host **only** — on Linux the targets are not
  merely broken, they are absent, so there is no iOS feedback there at all.

  The check that the port has not broken anything:

      ./gradlew :app:testDebugUnitTest :app:assembleDebug \
                :app:compileKotlinIosArm64 :app:compileTestKotlinIosArm64

  The Android half alone is not enough any more. `compileTestKotlinIosArm64` is what catches
  shared code reaching into `androidMain` — `commonTest` compiled against Android resolves such a
  call happily and says nothing.

  Two Kotlin/Native rules that only bite in `commonTest`, both of which cost a red build here:

  - **A backtick test name cannot contain a comma.** They become Objective-C symbols. `"` is
    likewise flagged, for Windows. Eight names had to be rewritten when the tests moved.
  - **`kotlin.test` takes its message last, where JUnit took it first.** `assertEquals(msg, a, b)`
    silently becomes `assertEquals(expected = msg, …)`. Grep for the multi-line calls too — an
    `assertEquals(` alone on its line hides the string on the next one.

  commonMain holds the session engine, the cue, every stored type and the whole of the goal
  arithmetic (`Model.kt`), all reads and writes (`Store.kt`), the two shared widgets, and the
  goals, achievements and milestone screens. Dates are kotlinx-datetime, not `java.time`.

  Two rules the port established, both worth keeping:

  - **Storage takes its `DataStore`, it does not find one.** `Store` is constructed with a
    `DataStore<Preferences>` and knows nothing about where the file is. Android still opens it
    through the `preferencesDataStore("breath")` delegate, at exactly the path every released
    version has written to. A `Store` that built its own path would read an empty log on every
    phone that updated, and nothing would report an error.
  - **Screens read `LocalStore`, not `LocalContext`.** The Context was only ever a way to reach
    the store; where a screen still takes one it is for something else, and that is the signal
    it cannot move yet.

  ### The sound
  The sound and the playing of it are separate, and have to stay that way. `WaveDsp.kt` and
  `MarkerSynth.kt` in commonMain hold every filter, every ear-picked constant and both syntheses;
  `androidMain/Audio.kt` and `iosMain/IosAudio.kt` hold only where the samples go. Forty tuning
  constants in two copies would have drifted, and a drift in a constant picked by ear is a bug
  invisible in a diff.

  **The sample rate is a parameter, never a constant.** It was 44100 baked into five filter
  coefficients while AudioTrack was the only sink; iOS returns whatever its output node runs at,
  commonly 48000. A synth built for one played at the other is the same sound a semitone sharp
  with its filter corners moved to match.

  iOS schedules buffers instead of installing an `AVAudioSourceNode`. A source node's render block
  runs on the realtime audio thread and Kotlin/Native's runtime does not belong there — a
  collection inside a render callback is a dropout you can hear.

  Still owed on iOS: the two recorded bowls and a user's own mp3. They are deliberately **silent**
  rather than falling back to the synthesised bell — pick the bowl, hear a bell, and there is no
  way to tell which of the two is the bug.

  ### The two bowls are resources, not res/raw
  `commonMain/composeResources/files/*.mp3`, read on both platforms through the generated `Res`
  in `Bowls.kt`. They were Android resources, which is the whole reason the gong was silent on
  iOS: `res/raw` is Android machinery and Kotlin/Native has no way into it.

  They are the only sounds that are not arithmetic. The bell and the tick are synthesised from
  `MarkerSynth.kt` on both platforms and cost nothing to share; a recording has to be bundled
  somewhere both can read. Android cannot hand SoundPool bytes, so it spills them to its cache
  once and loads the path — the cache is right, since losing the copy costs one decode and the
  resource is still in the APK.

  `GONG_FADE_START_MS` and `GONG_FADE_MS` live in `Bowls.kt` for the reason every other ear-picked
  constant does: two copies drift, and a drift you can hear is invisible in a diff.

  ### Reading a CI failure without a token
  Job logs are a 403 without authentication, even on a public repository. Annotations are not:

      # the check runs for a commit, then the annotations on the failing one
      curl -s https://api.github.com/repos/wlemkens/openbreath/commits/<sha>/check-runs
      curl -s https://api.github.com/repos/wlemkens/openbreath/check-runs/<id>/annotations

  That is the whole reason the scripts in `.github` print `::error title=…::` rather than plain
  text on failure: a workflow-command line becomes an annotation, and an annotation can be read
  back by anyone. `print-test-failures.sh` and `record-simulator.sh` both put the part worth
  reading — the failing assertions, the app's console tail — through that channel deliberately.
  Keep doing it; a failure that only exists in the job log cannot be diagnosed from here.

  The unauthenticated API allows 60 requests an hour per IP, which a polling loop eats quickly.
  Poll on the order of minutes, not seconds.

  ### The bundled audio is in Git LFS
  `.gitattributes` puts every audio format in LFS, and **anything that clones or checks out without
  git-lfs gets a 130-byte pointer file instead of the mp3.** The build succeeds, the APK packages
  the pointer, and the only symptom is that the bowls never sound — which `Audio.kt` itself calls
  the failure that is "invisible without this". Every APK CI built before this was noticed had it.

  So `actions/checkout` sets `lfs: true` and `.github/check-media.sh` fails the build if any
  bundled mp3 is still a pointer. If you clone this and the bowls are silent, that is the first
  thing to check — `git lfs pull`.

  Still Android-only, and why:

  - **`Reminders.kt`, `RemindersScreen.kt`** — AlarmManager and a BroadcastReceiver →
    UNUserNotificationCenter, plus the permission prompt.
  - **`Settings.kt`** — the mp3 file picker, and a colour picker built on `android.graphics.Color`.
    Backup export and import live inside it, which is why an iPhone cannot yet read a backup —
    and that file is the only route a practice log has to reach one.

  **Locale data is the recurring seam.** kotlinx-datetime carries none, on purpose, so every
  question about how a reader writes something goes to the platform. `firstDayOfWeek()` is an
  `expect` because it is a plain value; the day heading and the time of day are on the `Formats`
  interface instead, because Android cannot answer either without a Context. `uses24Hour` joins
  them with the reminders port. Anything reaching for `java.time.format` or `String.format` in
  commonMain is this seam being crossed by accident.

  ### What the App Store still wants
  `PrivacyInfo.xcprivacy` declares no tracking and no collected data, and one required-reason
  API: `NSPrivacyAccessedAPICategoryFileTimestamp` with reason `C617.1`. That is DataStore
  reading the metadata of its own file in the app container to write atomically — nobody thinks
  of it as a privacy API, which is exactly why it is the one that gets a submission rejected.
  `ITSAppUsesNonExemptEncryption: false` answers export compliance once instead of on every
  upload; it is only true because the app makes no network call at all.

  **A picked mp3 is copied in, not bookmarked.** Android keeps a `content://` URI alive with a
  persistable permission grant; iOS could do the same with a security-scoped bookmark and
  deliberately does not. A bookmark still points at someone else's file, so the marker goes silent
  the day they move it, delete it, or leave it in an iCloud folder that is not on the phone when a
  phase ends. The copy lives in `Documents/markers`, beside the log and for the same reason —
  Caches is emptied whenever the system likes.

  A consequence worth knowing: a backup carried from Android names `content://` URIs that mean
  nothing here, so those phases fall silent rather than erroring. That is the same thing they did
  before a file could be picked at all.

  **The torch needs no camera permission — tested on a device, no prompt appears.** Torch control
  is configuration on the capture device rather than capture, so no `AVCaptureSession` is created
  and `NSCameraUsageDescription` is not required. If a prompt ever does appear, something has
  started a session and the cause is that, not the torch.

  Do-not-disturb has no iOS equivalent and is not getting one — no public API sets a Focus.
  That feature is Android-only by nature, not by omission.

  ## Sounds during the phases
  During hte breathing phases, there are different sound options available.

  The base sound is waves coming ashore. During the breath in, the pitch goes up, during breathing out, the pitch goes down.

  # TODO
  - iOS markers are all the same (all have the bell effect)
  - **Reminders on iOS.** The last unported screen: AlarmManager and a BroadcastReceiver become
    UNUserNotificationCenter, plus the permission prompt and `uses24Hour` for the picker.
    Deliberately deferred until after the first App Store submission. Worth knowing when that is
    revisited: local notifications need no App Store id and no entitlement, so this *could* be
    built sooner — the timing is a choice about effort, not a technical gate. Until it lands, the
    store listing must not promise reminders.
  - **Ask Apple whether a Belgian VZW is eligible for the fee waiver.** Everything about
    publishing under the VZW rests on it, and the answer is not published anywhere: the old
    country list excluded Belgium, the current page names no list at all. Enrolling as an
    organisation also needs a D-U-N-S number for the VZW, which is free but slow.
  - **Signing.** CI builds unsigned on purpose, which is right for sideloading and useless for
    the store: a distribution certificate, an App Store provisioning profile and a
    `DEVELOPMENT_TEAM` are all still missing, and none can exist before the account does.
  - **The App Store Connect record**: bundle id, name, category, age rating, description,
    keywords, support URL, privacy policy URL, screenshots at Apple's sizes.
  - **The rate link.** `IosPlatform.canRate` is false because the numeric App Store id does not
    exist until first submission. Fill it in once it does — that one really is gated on
    submitting.
