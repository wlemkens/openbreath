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
  `app/src/test/java/.../StorageTest.kt` holds strings that older versions really wrote and
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

  ## Sounds during the phases
  During hte breathing phases, there are different sound options available.

  The base sound is waves coming ashore. During the breath in, the pitch goes up, during breathing out, the pitch goes down.

  # TODO
  - Decide `android:allowBackup`. It defaults to true, so Android may restore an older copy of
    the log and goals over a fresh install — the one way practice history can be lost that the
    app itself cannot prevent. Turning it off protects against that but gives up restoring a
    real user's history onto a new phone.
  - Before publishing, re-read Play's Payments policy against the Support screen. It permits
    the PayPal link today (see Monetisation above); policies move, and this one decides whether
    the app can be listed at all.
  - Generate the release keystore (see README) so `bundleRelease` produces something Play will
    take. The Gradle wiring is in place and falls back to unsigned until the key exists.
  - Rename to OpenBreath