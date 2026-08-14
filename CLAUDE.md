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

  The bundled audio is **not** covered by that grant. `app/src/main/res/raw/*.mp3` are cuts
  of files in `media/` from the `freesound_community` Pixabay account, released under
  **CC0 1.0** (public domain dedication). CC0 requires no attribution, so nothing has to
  ship in the app, but we credit the source anyway in README and here:

  - `res/raw/session_end.mp3` — cut from `media/freesound_community-025535_singing-bowl-60767.mp3`
  - `res/raw/singing_bowl.mp3` — cut from `media/freesound_community-singing-bowl-hit-3-33366.mp3`

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
  `src/androidMain`, `src/iosMain` and `src/androidUnitTest` — the old `src/main/java` and
  `src/test/java` are gone, and an editor left open on a moved file will happily recreate it.

  Nothing iOS can be compiled on Linux: the Kotlin/Native link step needs Xcode. The check that
  the port has not broken anything is therefore still the Android one —
  `./gradlew :app:testDebugUnitTest :app:assembleDebug`.

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

  Still Android-only, and why:

  - **`Audio.kt`** — AudioTrack/SoundPool. The DSP is portable; only the sink swaps for
    AVAudioEngine.
  - **`Reminders.kt`** — AlarmManager and a BroadcastReceiver → UNUserNotificationCenter.
  - **`Platform.kt`** — vibration → CoreHaptics, torch → AVCaptureDevice.
  - **`Settings.kt`, `RemindersScreen.kt`** — the file picker, permission prompts and intents.
  - **`Log.kt`** — only `clockTime`, which asks Android whether this user reads 14:00 or 2 PM.
    That is a genuine platform question, so it wants an expect/actual, not a rewrite.

  kotlinx-datetime carries no locale data, so the week's first day is `expect firstDayOfWeek()`
  — Android reads the locale, iOS will read NSCalendar. It is the only calendar fact the
  library would not answer.

  Do-not-disturb has no iOS equivalent and is not getting one — no public API sets a Focus.
  That feature is Android-only by nature, not by omission.

  ## Sounds during the phases
  During hte breathing phases, there are different sound options available.

  The base sound is waves coming ashore. During the breath in, the pitch goes up, during breathing out, the pitch goes down.

  # TODO
  - Generate the release keystore (see README) so `bundleRelease` produces something Play will
    take. The Gradle wiring is in place and falls back to unsigned until the key exists.
  