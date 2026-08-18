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
  It is already allowed, and by the plainest route Apple offers. Guideline **3.2.2(iv)** bars
  "collecting funds within the app for charities and fundraisers" and then says what to do
  instead: such apps "must be free on the App Store and may only collect funds outside of the
  app, such as via Safari or SMS". `Links.openPayPal` opens a URL in the browser and the app is
  free, so that is the rule followed rather than worked around, and no entitlement is involved.

  The other road, **3.2.1(vi)**, lets an *approved nonprofit* fundraise inside its own app. It is
  not the cheaper one: it requires Apple Pay support, a disclosure of how the funds will be used,
  and tax receipts available to donors — which in Belgium means the selectively granted
  recognition, not merely being a VZW. Taking it would also turn a tip to the developer into a
  donation to an organisation, which is a different thing on Play as well, where the peer-to-peer
  exemption rests on 100% reaching the developer.

  So the two stores agree here for once, and for the same underlying reason: the money buys
  nothing. Keep it that way and the link needs no permission from either. The guideline numbers
  do move between revisions — check the text rather than the number if it ever matters.

  What must not happen is an in-app payment sheet of any kind. The moment money is taken inside
  the app, both stores stop reading it as a tip and start reading it as a purchase, and a purchase
  has to go through their billing.

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

  Do-not-disturb has no iOS equivalent and is not getting one — no public API sets a Focus.
  That feature is Android-only by nature, not by omission.

  ## Sounds during the phases
  During hte breathing phases, there are different sound options available.

  The base sound is waves coming ashore. During the breath in, the pitch goes up, during breathing out, the pitch goes down.

  # TODO
  - iOS markers are all the same (all have the bell effect)
  