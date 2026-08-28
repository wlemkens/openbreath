# OpenBreath

An app for heart coherence breathing meditations, for Android, iOS, Windows, macOS and Linux.

Free, no ads, no tracking, no account. It makes no network call at all.

## What it does

- Presets for the usual patterns, and your own timings for the in, hold, out and hold phases.
- A breath cue to follow with your eyes: a sphere that opens as you breathe in and closes as you
  breathe out, either as a glow or a point cloud, in a colour you pick.
- Soundscapes per phase — waves that rise and fall with the breath, or a soundwave whose overtones
  arrive as you breathe in and dissolve as you breathe out; a marker at each phase change (singing
  bowl, bell, metronome tick, or an mp3 of your own); or silence. The bowl and the tick are pitched
  by the turn they mark, so going into a breath in does not sound like going out.
- A singing bowl at the end of the session, so you don't have to check whether it is over.
- Optional vibration at each phase change, and an optional flashlight that brightens as you breathe
  in and fades as you breathe out — the breath to follow with your eyes shut.
- Optional silencing of notifications for the length of the session, put back exactly as it was.
- A log of every sitting, with goals, achievements and milestones counted out of it — so a goal set
  today already credits the practice behind it.
- Reminders, as many as you like, each a quiet notification or an alarm that rings until dismissed.
- Backup to one JSON file you keep. Sittings merge rather than overwrite, so a log can only grow.

The Android build is the complete one. iOS has everything but reminders. The desktop builds have
everything but reminders, vibration, the flashlight and the silencing of notifications — a desktop
has no vibrator, no flashlight and no Focus a program may set, and each is left out of the settings
screen rather than shown as a switch that does nothing.

## Getting it

Until the stores have it, the desktop installers are built by CI on every push and kept at fixed
URLs on the [latest](https://github.com/wlemkens/openbreath/releases/tag/latest) prerelease:

- [OpenBreath.deb](https://github.com/wlemkens/openbreath/releases/download/latest/OpenBreath.deb) — Linux
- [OpenBreath.msi](https://github.com/wlemkens/openbreath/releases/download/latest/OpenBreath.msi) — Windows
- [OpenBreath.dmg](https://github.com/wlemkens/openbreath/releases/download/latest/OpenBreath.dmg) — macOS

All three are **unsigned**, so SmartScreen warns and Gatekeeper refuses a double-click — on macOS,
right-click → Open. That is a missing certificate, not a broken build.

## Building it

```sh
./gradlew :app:run              # the desktop app
./gradlew :app:desktopTest      # the shared tests, no Android SDK needed
./gradlew installDebug          # onto an attached phone or a running emulator
```

Everything else — emulators, signing keys, TestFlight, sideloading, publishing to Play — is in
[docs/BUILDING.md](docs/BUILDING.md).

The code is Kotlin Multiplatform with Compose Multiplatform. `app/src/commonMain` holds the session
engine, the breath cue, the stored types, the audio DSP and all but one of the screens;
`androidMain`, `iosMain` and `desktopMain` hold only what genuinely differs. [CLAUDE.md](CLAUDE.md)
is the long-form record of why things are the way they are.

The bundled mp3s are in Git LFS. Clone without git-lfs and you get 130-byte pointers, the build
succeeds, and the only symptom is that the singing bowls never sound — `git lfs pull` fixes it.

### And on Windows Phone?

No, and not for want of trying: the platform was discontinued in 2017, the store closed to new apps
in 2019, there is no supported SDK, and Kotlin has no target for it. Nothing can be built that
anybody could install.

## Licence

Copyright (c) 2026 Wim Lemkens.

OpenBreath is free software: you can redistribute it and/or modify it under the terms of the **GNU
General Public License**, either version 3 of the License, or (at your option) any later version —
see [LICENSE](LICENSE). It is distributed in the hope that it will be useful, but WITHOUT ANY
WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.

One additional permission applies, under section 7 of that licence — see
[LICENSE-EXCEPTION](LICENSE-EXCEPTION). It allows conveying OpenBreath through an app store whose
terms restrict recipients in ways the GPL forbids, which is what makes distribution on the App Store
possible at all. It grants nothing over the source: a copy from a store is still a GPL copy, and the
source is still yours to ask for and pass on.

The bundled audio is **not** covered by that grant. `app/src/commonMain/composeResources/files/*.mp3`
are cuts of files from the
[freesound_community](https://pixabay.com/users/freesound_community-46691455/) Pixabay account,
released under [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/). CC0 asks for no
attribution; the credit is here because it is the decent thing, not because it is owed.

Every dependency has to stay GPL-compatible. The AndroidX and Kotlin stack is Apache-2.0, which
flows one way into GPLv3. One is not: **JLayer**, which decodes mp3 on the desktop because the JVM
cannot, is LGPL-2.1 — also GPL-compatible in that direction, and the only non-Apache dependency in
the tree.
