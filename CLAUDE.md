# Overview
This project is an application for doing heart coherence breathing meditations, for Android, iOS
and the desktop (Windows, macOS, Linux).

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

  **One dependency is not Apache-2.0, and it is worth knowing which.** `com.googlecode.soundlibs:jlayer`
  is LGPL-2.1, and it is in the tree because the JVM has no mp3 decoder at all — without it the
  two bundled bowls and a reader's own file could not sound on the desktop. LGPL flows one way into
  GPLv3 exactly as Apache does, so nothing about distribution changes; it is named here so that
  "everything is Apache" does not become a thing anyone believes without checking.

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

  ### The store screenshots follow the interface
  `docs/store/android/*.png` are what the listing shows, and a screenshot is a claim about what the
  app looks like — the one part of a listing a reader believes without reading. So **any change to a
  screen that appears there is not finished until the screenshot is retaken**, in the same commit
  as the change. The ten shots and which screens they are are listed in `docs/store/README.md`.

  It costs one command, which is the whole reason the rule can be absolute:

      ./gradlew :app:installDebug && python3 docs/store/screenshots.py

  It clears the app's data and imports a generated log, so run it on an emulator and never on a
  phone that holds real practice. It drives the UI by the labels `uiautomator` reports, which means
  **renaming a button can break the run rather than the app** — if it exits with `no 'X' on
  screen`, the script is out of date with the interface it photographs, and that is the same rule
  asking to be applied.

  The copy is a claim too. Adding or dropping a feature means `docs/store/listing.md` changes with
  it, and the App Store half may only name what iOS actually has — reminders and the silencing of
  notifications are still Android's alone.

  **The iOS set is taken the same way now**, which it was not: it used to be hand-taken on a Mac
  because `simctl` cannot tap a simulator, and the tapping is an XCUITest — `iosApp/StoreScreenshots`
  driven by `.github/ios-screenshots.sh` — so it runs in CI on a 6.9" simulator and the rule above
  covers both stores:

      gh workflow run build.yml -f screenshots=true

  **The Apple assets are gitignored**, unlike Play's: they land in `docs/store/ios/`, which is where
  the workflow writes them and where a laptop finds them, and 36 MB a regeneration is not worth
  carrying against a 1 GB LFS quota. The price is that the "not finished until the screenshot is
  retaken" rule cannot be enforced by a diff on that half — nothing goes red, and nobody notices.
  **So say out loud, whenever a shared screen changes, that the Apple set needs refilming**, exactly
  as this file used to say about a hand-taken set. The command is the difference; the discipline is
  not.

  Nine shots rather than ten: no reminders screen. **Twice over**, because `TARGETED_DEVICE_FAMILY`
  is `1,2` and App Store Connect then refuses a listing without a 13" iPad set as well as the 6.9"
  phone one — the device family and the screenshot script are one decision, and the error that asks
  for it says nothing about where it came from. Nothing is laid out for a tablet and nothing needs to
  be: the cue takes the space it is given.

  Two things it inherits from the Android script.
  It taps by accessibility label, so **renaming a button breaks the run rather than the app**. And
  the demo log arrives in the app's launch environment instead of through the file picker — the hook
  is in `iosMain/MainViewController.kt`, inert without the variable, and it is the one piece of
  shipping code that exists for the screenshots. Deleting it silently empties the log, achievements
  and milestone shots.

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

  Compiling for iOS does **not** need Xcode, and does not need a Mac either: Kotlin/Native
  downloads a prebuilt distribution for whatever host it is on, so `compileKotlinIosArm64` and
  `compileTestKotlinIosArm64` run on a Mac with only the Command Line Tools — an Intel one
  included — **and on Linux**. Only *linking* a framework and running a simulator need Xcode.
  Type-check iOS code locally with those two tasks wherever you are; do not wait for CI to find a
  typo.

  **Linking needs Xcode 26, not merely some Xcode.** Compose Multiplatform 1.11.1 is built
  against the iOS 26 SDK: `ui-uikit` references `UIViewLayoutRegion`, which does not exist in
  Xcode 16, and the link fails on an undefined Objective-C class inside a Compose object file
  rather than on anything here. GitHub's macos-15 image carries several Xcode 26.x but still
  defaults to 16.4, so the workflow selects one.

  That is what finally settles the hardware question. Xcode 26 needs macOS 15, which no Intel Mac
  before 2019 can run, so the split is not a preference: type-check locally on whatever Mac is to
  hand, link and test on CI. An Apple Silicon machine collapses the two, and nothing else does.

  **The Apple targets do compile on Linux**, which this file used to say they did not. Verified on
  2026-08-27: `:app:compileKotlinIosArm64` and `:app:compileTestKotlinIosArm64` both succeed on a
  Linux host — Kotlin/Native downloads a cross-compiling distribution and the whole type-check runs.
  So the shared code *can* be checked against iOS here, and there is no reason to wait for CI to
  find a typo in `iosMain`.
  Linking a framework and booting a simulator still need Xcode on a Mac; that part is unchanged.

  The check that the port has not broken anything:

      ./gradlew :app:testDebugUnitTest :app:assembleDebug :app:desktopTest \
                :app:compileKotlinIosArm64 :app:compileTestKotlinIosArm64

  The Android half alone is not enough any more. `compileTestKotlinIosArm64` is what catches
  shared code reaching into `androidMain` — `commonTest` compiled against Android resolves such a
  call happily and says nothing.

  `:app:desktopTest` runs the whole of `commonTest` on the JVM, and the first three of these run
  on Linux — so on a machine with no Mac the shared code is now checked by two compilers rather
  than the one. It is also the fastest of them, which makes it the right thing to run first.

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

  ### Importing an Objective-C method in Kotlin/Native
  This has cost three round trips, each one a single wrong import line, so it is worth stating
  properly rather than guessing again.

  Whether a method needs an import depends on where Objective-C declares it, not on whether it is
  an instance method:

  - **Declared in a category** — most of Foundation — becomes a Kotlin *extension* and must be
    imported: `NSData.writeToFile`, `NSData.dataWithContentsOfFile`, `NSString.writeToURL`,
    `NSURL.URLByAppendingPathComponent`, `NSData.create`.
  - **Declared on the class interface itself** — most of AVFoundation — becomes a *member* and
    must not be: `AVAudioPlayer.setVolume`, `AVCaptureDevice.lockForConfiguration`,
    `AVCaptureDevice.unlockForConfiguration`.

  Guessing which is which is a waste of a fifteen-minute CI round trip, and the compiler is
  unambiguous about it if you read *which line* the error is on. `Unresolved reference` on the
  call means the import is missing; the same words on the `import` line mean it should not be
  there. Both are one-line fixes in opposite directions.

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

  A step whose output is not tee'd to a file cannot be annotated, because `print-test-failures.sh`
  reads a log rather than the console. That is how a Kotlin compile failure once reached the job
  log and nowhere else — the one place a token is needed. Every step that can fail interestingly
  pipes through `tee`.

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
    UNUserNotificationCenter, plus the permission prompt. Now the only feature missing from *two*
    platforms rather than one; the desktop needs its own answer again (a tray notification, or the
    platform's own scheduler), so this is two ports and not one.
  Settings has since moved to commonMain, and with it the mp3 picker, the colour picker and
  backup export and import — `IosFiles` answers all three, so an iPhone reads a backup written on
  Android. Reminders is the only screen left behind.

  **Locale data is the recurring seam.** kotlinx-datetime carries none, on purpose, so every
  question about how a reader writes something goes to the platform. `firstDayOfWeek()` is an
  `expect` because it is a plain value; the day heading and the time of day are on the `Formats`
  interface instead, because Android cannot answer either without a Context. `uses24Hour` joins
  them with the reminders port. Anything reaching for `java.time.format` or `String.format` in
  commonMain is this seam being crossed by accident.

  ### Signing, and where the pieces live
  Stanistil VZW is the App Store publisher; Play stays under Wim Lemkens personally. Team
  `AD8Y56HX64`, app `6805899911`, bundle id `io.github.wlemkens.openbreath`.

  Nothing was signed on a Mac. The certificate was issued by posting a CSR generated with
  `openssl` to the App Store Connect API, and the profile the same way — `signing/` holds the
  private key, the certificate, the `.p12` and the profile, and is gitignored. **The private key
  in `signing/distribution.key` is the half Apple cannot reissue**: lose it and the certificate is
  waste, which costs a revoke and a new one rather than anything worse, but back it up somewhere
  that is not this repository.

  CI signs from seven repository secrets — the `.p12` and its password, the profile, the App Store
  Connect key with its id and issuer, and the team id. The `app-store` job needs
  **`-f app_store=true` on a manual run**: a build that reaches App Store Connect burns a build
  number that can never be reused and mails every TestFlight tester, which should not happen because
  someone fixed a typo. It was every `workflow_dispatch` until the screenshots gave a second reason
  to run the workflow by hand, at which point "manual" stopped being enough of a statement of intent
  — both are now inputs that default to false.

  The signing settings are on the `xcodebuild` command line rather than in `project.yml`, because
  that file has to keep producing the *unsigned* ipa the sideloading job publishes. Command-line
  settings win, so one project file serves both.

  **A `.p12` for macOS must be the legacy PKCS#12, `openssl pkcs12 -export -legacy`.** OpenSSL 3
  defaults to AES-256 with a SHA-256 MAC and macOS's Security framework reads neither. It fails
  twice over, with two different messages, and both point away from the answer:

  - SHA-256 MAC → "MAC verification failed during PKCS12 import (wrong password?)", when the
    password is right.
  - AES/PBES2 encryption → "Unknown format in import", when the file is a perfectly good p12.

  `-legacy` gives 3DES for the key, RC2-40 for the certificates and a SHA-1 MAC. That is weak
  crypto by any modern reading and it is what `security import` parses, so it is what the file has
  to be. It sits in `signing/`, gitignored, and never leaves as anything but a GitHub secret.

  Reading it back on Linux then needs `-legacy` too — which is what led to "fixing" it into a
  modern format that macOS rejected. The local read is not the constraint; the runner is.

  And set the password secret with `printf '%s'` rather than from a file written by `echo`. A
  trailing newline is inside the secret, the import fails, and the message is the same one.

  **The `.p8` is downloadable exactly once.** A leaked one is revoked and reissued, never rotated
  quietly, which is why `.gitignore` covers it, the `.cer`, the profile and `signing/` — the key
  sat untracked but unignored in the root of a public repository for a while, one `git add -A`
  from being published.

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

  **A file that is gone falls back to the phase's tone**, on both platforms — usually the bell,
  since that is the default and the tone chips are hidden while a file is chosen. Android has
  always done this (`play` looks the URI up in the loaded samples and drops through), and iOS now
  matches. It matters more than it sounds: a preset can name a file this phone never had, because
  a backup carried from Android names `content://` URIs that mean nothing on an iPhone, and a
  boundary that makes no sound at all reads as a broken app rather than a missing file.

  The tone is therefore prepared even for a phase pointing at a file, on both sides. Otherwise the
  fallback would be the strike that builds itself, which is the bug the marker-readiness fix was
  about.

  **The torch needs no camera permission — tested on a device, no prompt appears.** Torch control
  is configuration on the capture device rather than capture, so no `AVCaptureSession` is created
  and `NSCameraUsageDescription` is not required. If a prompt ever does appear, something has
  started a session and the cause is that, not the torch.

  Do-not-disturb has no iOS equivalent and is not getting one — no public API sets a Focus.
  That feature is Android-only by nature, not by omission.

  ## The desktop port
  `jvm("desktop")` beside `androidTarget()` and the two iOS targets, so the source sets are
  `desktopMain` and `desktopTest`. Named rather than left as a bare `jvm()`, because `jvmMain`
  would read as a set Android shares and it does not.

  **Windows, macOS and Linux are one implementation and not three.** Nothing in `desktopMain`
  branches on the operating system except `appDir` in `Prefs.desktop.kt`, which is the one genuine
  per-OS question — `%APPDATA%`, Application Support, or the XDG data directory. Everything else it
  needs is plain JVM API that behaves the same everywhere: `javax.sound.sampled` for the audio,
  `java.awt.FileDialog` for the pickers, `Desktop.browse` for the links, `java.time.format` for the
  locale seam. That is the whole difference from the android/ios split, where the APIs really are
  different, and it is why a third `Platform` implementation cost one afternoon and the iOS one cost
  weeks.

  What *is* per-OS is only packaging: `jpackage` builds an installer for the machine it is run on
  and no other, so `packageDeb`, `packageMsi` and `packageDmg` are three CI jobs over one source
  set. Asking Linux for an `.msi` fails, and that is jpackage's limit rather than a choice made
  here. All three land on the same rolling `latest` prerelease the ipa uses, under fixed names —
  `OpenBreath.deb`, `.msi`, `.dmg`, via `.github/publish-desktop.sh` — because an upload-artifact
  needs a GitHub login to download and a release asset does not, and the README links them. Fixed
  names rather than the versioned ones jpackage writes: a URL that moves every push is not a
  download link, and only SideStore needs a per-version filename. All three installers are
  **unsigned**, so SmartScreen warns and Gatekeeper refuses on a
  double-click — the same missing-certificate problem the iOS TODO carries, said out loud in the
  README so it arrives as a known edge rather than a surprise.

  Absent on the desktop, and each one a fact about a desktop rather than something owed: reminders
  (as on iOS), vibration, the flashlight, and do-not-disturb. Every one is gated on a flag the
  screens already read — `Haptics.supported` was added for exactly this and joins
  `TorchLight.available`, `FocusGuard.supported`, `Files.canPickAudio` and `Links.canRate`. The rule
  those all share: a platform that cannot do something says so, and the row is left out rather than
  shown as a switch that does nothing. Adding a feature one platform lacks means adding a flag, not
  a branch in the screen.

  **Windows Phone is not a target and cannot be one.** Discontinued 2017, store closed to new apps
  2019, no supported SDK, and Kotlin has no backend for it. It is asked about often enough that the
  answer is written down here rather than re-derived.

  ### One navigator, finally
  `Breath` is in `commonMain/App.kt` now. It was the same fifty-line `when` copied into
  `MainActivity.kt` and `MainViewController.kt`, both of which said in a comment that they would
  collapse when the screens ported — and a desktop would have been a third copy. Six of the seven
  destinations are shared; Reminders is passed in as a composable slot, null where the platform has
  none, exactly as `FirstRunSetup(onReminder)` already worked. Android is the only caller that
  passes anything.

  This is the shape to keep: an entry point provides a `Store` and a `Platform` and hands over.
  A platform-specific navigator is now a sign that something belongs behind a flag instead.

  ### The sound, a third time
  `DesktopWaveSynth` is a daemon thread writing a `SourceDataLine` — the same division
  `androidMain/Audio.kt` and `iosMain/IosAudio.kt` keep, where `WaveDsp.kt` and `MarkerSynth.kt`
  hold every filter and every ear-picked constant and the platform holds only where the samples go.

  **The sample rate is a parameter here too**, and `supportedRate()` asks before anything is
  opened: 44100 if the machine takes it, since that is what the constants were tuned at, 48000
  otherwise. `isLineSupported` is a query and not a reservation, which is what lets the rate be
  known before `WaveDsp` is constructed.

  The line is opened inside the render thread and not held in a field, which matters: it is closed
  when the fade reaches silence, and a field would then hold a closed line the next Start could not
  reopen. Android's `WaveSynth` does the same for the same reason.

  **One markers class where iOS has two**, and that is not laziness. iOS splits `IosMarkers` from
  `IosBowls` because a recording there wants an `AVAudioPlayer` and a synthesis wants the engine;
  here a recording is decoded to a `FloatArray` first, after which a bowl plays through exactly the
  code the bell does. There is nothing left for a second class to be.

  ### Mp3.kt, and the only non-Apache dependency
  `javax.sound.sampled` reads WAV, AIFF and AU and no mp3 at all, so the desktop is the one
  platform that needs a decoder in the tree: JLayer, LGPL-2.1, see the licensing note above.

  `decodeAudio` tries JLayer first and `AudioSystem` second, so a picked WAV works too — both other
  platforms accept one, and the desktop quietly falling back to a bell for a wav is precisely the
  failure this file's own comment warns about.

  `resample` does two jobs in one pass: a recording's rate onto the line's rate, and the pitch the
  phase asks for. They are the same arithmetic, and `gongRate`'s 0.6 means the same thing here as
  it does reaching SoundPool and `AVAudioPlayer.rate`. It is linearly interpolated rather than
  nearest-sample, which is the difference between a bowl and a bowl with a hiss on it, and it is
  the one piece of the desktop audio that can be checked without a speaker — `ResampleTest` and
  `Mp3Test` in `desktopTest` are that check. `Mp3Test` also catches an LFS pointer, since JLayer
  refuses one.

  ### The icons are generated too
  `IconGen` writes `app/desktopIcons/icon.png`, `.ico` and `.icns` beside the launcher and iOS
  icons, from the same point lattice, under the same `-Picons=true`. jpackage takes only the
  platform's own container and puts the Java coffee cup there without one — which is not cosmetic,
  it is the icon a reader has to find the window by. Both containers are a PNG in a small wrapper,
  which is why writing them cost a dozen lines each rather than a dependency.

  `.ico` and `.icns` joined `*.png` in `.gitattributes`, so they are LFS, and
  `.github/check-media.sh` covers them: jpackage refuses a 130-byte icon, and the error it gives
  talks about an unrecognised file format rather than about a checkout that skipped LFS.

  ### Still owed on the desktop
  - **Check what `LifecycleEventEffect(ON_STOP)` means here.** `SessionScreen` pauses the sitting on
    it, which is right on a phone — leaving the app should not play on in the background. On the
    desktop it depends on what Compose maps the event to. Minimising the window is a fine reason to
    pause; *losing focus* is not, and a meditation that stops because you clicked your browser is a
    bug. Untested either way: verifying it needs a hand on the window. If it turns out to be focus,
    the fix is to gate that one effect rather than to change the shared screen.
  - **Keeping the display awake.** `KeepAwake` is a no-op: the JVM has no API for it, so a
    screensaver may arrive mid-sitting. `java.awt.Robot` nudging the pointer is the usual trick and
    takes over the pointer; doing it honestly means JNA and three per-OS calls. Worth doing the day
    someone reports it, and marked `ponytail:` in `DesktopPlatform.kt` until then.
  - **Signing.** Same problem as iOS, three more certificates.
  - **A store listing, if ever.** `docs/store/listing.md` is Play and the App Store. If a Microsoft
    Store record is ever wanted, the same rule applies as for Apple: the copy may only name what
    that platform actually has, and reminders are not it.

  ## Sounds during the phases
  During hte breathing phases, there are different sound options available.

  The base sound is waves coming ashore. During the breath in, the pitch goes up, during breathing out, the pitch goes down.

  # TODO
  - **Reminders on iOS.** The last unported screen: AlarmManager and a BroadcastReceiver become
    UNUserNotificationCenter, plus the permission prompt and `uses24Hour` for the picker.
    Deliberately deferred until after the first App Store submission. Worth knowing when that is
    revisited: local notifications need no App Store id and no entitlement, so this *could* be
    built sooner — the timing is a choice about effort, not a technical gate. Until it lands, the
    store listing must not promise reminders.
  - ~~**Ask Apple whether a Belgian VZW is eligible for the fee waiver.**~~ Answered: yes.
    Stanistil VZW is enrolled as an organisation, team `AD8Y56HX64`, D-U-N-S 37-171-7333, KBO
    0719.384.464. **Still worth confirming once that the waiver was actually applied** rather
    than the fee merely deferred — membership details will say, and it is the sort of thing that
    surfaces as a charge a year later.

    The condition it carries is standing, not one-off: the waiver requires that the Paid
    Applications Agreement has *never* been signed. Accepting it in App Store Connect — which
    the UI offers freely — voids the waiver. The free apps agreement is the only one wanted.

    So the price is the **Free** price point on both stores, and that is close to a one-way door:
    charging for the app later would mean signing the agreement that voids the waiver, and on Play a
    free app can never become paid at all — only a new listing can. Which suits an app that is free
    by design, and is one more reason the tip link has to stay a gift rather than a price.
  - **The App Store listing.** The record exists — app `6805899911`, bundle
    `io.github.wlemkens.openbreath`, and build 70 uploaded and VALID, so the pipeline is proven.
    What a submission still wants is all copy and pictures: category, age rating, description and
    keywords, support URL, the privacy policy URL (live, above), screenshots at Apple's sizes, and
    the App Privacy answers — which are "no data collected", matching `PrivacyInfo.xcprivacy`.

    The description may only name what iOS actually has. Reminders are not it.
  - **The rate link, on release day.** The id was there all along — Apple assigns it when the
    record is created, not at submission — so `IosPlatform` now holds it and builds the URL. What
    it waits for is the listing being live, because an App Store page 404s until then. One
    constant: `LISTING_IS_LIVE` to true.
  - ~~**Publish the privacy policy.**~~ Live at
    https://wlemkens.github.io/openbreath/privacypolicy.html, which is the URL to give both
    stores — they ask for one and Play checks it resolves. Not `raw.githubusercontent.com`: it
    serves `text/plain`, so a reviewer would see the markup rather than the page. Either store's
    URL can be changed later, so this does not foreclose stanistil.be.

    **GitHub Pages serves it from the `ios-port` branch, `/docs` — and `main` has no `docs/`
    at all.** Merging and deleting that branch takes the privacy policy off the web, and the
    first sign of it is a store record pointing at a 404. Move the source in the same breath as
    the merge:

        gh api -X PUT repos/wlemkens/openbreath/pages -f 'source[branch]=main' -f 'source[path]=/docs'

    Publishing `/docs` puts `docs/store/` on the web too, which costs nothing on a public
    repository.
  - **The Play listing.** Copy for both stores is in `docs/store/listing.md`, the phone
    screenshots and the two Play graphics beside it. The keystore now exists. Still missing: the
    Play Console record itself, and — for a personal developer account — the 12-tester, 14-day
    closed test that has to run before production opens.

    Uploading is wired: gradle-play-publisher, `./gradlew :app:publishBundle`, `docs/BUILDING.md` has the
    service-account setup. Release notes are generated from the commit subjects on the way, which
    makes a commit subject user-facing copy — one more reason they are written the way they are.
    They land in `app/src/main/play/`, which is AGP's source-set name and not the KMP one, and is
    the only thing left under `src/main` now that the code has moved. Two things that constrain it. **The API cannot create the app** — the
    first bundle, the listing and the track go up by hand, and only updates come from here. And
    **the plugin is pinned to 3.13.0 because every 4.x needs Gradle 9.1**, while this build is on
    8.14.3 for AGP and Kotlin; the pin is a toolchain fact, not a preference, so it moves when
    Gradle does and not before.
  - ~~**Screenshots for the App Store.**~~ Done, and by the same discipline as Android's:
    `gh workflow run build.yml -f screenshots=true` drives a 6.9" simulator through
    `iosApp/StoreScreenshots` and downloads as the `app-store-screenshots` artifact. **Never run and
    therefore never yet proven** — the first run is the one that finds out whether Compose exposes
    every label the test taps, and it says which one it could not find when it does not.
