# OpenBreath

An Android app for heart coherence breathing meditations.

## Features

- Presets for the usual patterns, and your own timings for the in, hold, out and hold phases.
- A breath cue to follow with your eyes: a sphere that opens as you breathe in and closes as you
  breathe out, either as a glow or a point cloud, in a colour you pick.
- Soundscapes per phase — waves that rise and fall with the breath, or a soundwave whose
  overtones arrive as you breathe in and dissolve as you breathe out; a marker at each phase change (singing
  bowl, bell, metronome tick, or an mp3 of your own); or silence. The bowl and the tick are
  pitched by the turn they mark, so going into a breath in does not sound like going out.
- A singing bowl at the end of the session, so you don't have to check whether it is over.
- Optional vibration at each phase change.
- Optional flashlight that brightens as you breathe in and fades as you breathe out — the breath
  to follow with your eyes shut.
- Optional silencing of notifications for the length of the session, put back exactly as it was
  afterwards.
- Time remaining, a dot per breath, and a breath count, each hideable for a barer screen.
- A log of every sitting, day by day, with the pattern you breathed and the breaths you finished.
  The ones you cut short count too — anything over twenty seconds.
- Goals: as many as you like — one sitting a day, a hundred breaths a day, seven sittings a week.
  Counted from the log, so a new goal credits the practice you had already done.
- Achievements: days in a row, sittings, minutes, minutes today, and a streak per goal. Read back
  out of the log too, so a goal set today already shows the run of days behind it.
- Milestones at 3 days, a week, a month, 100 days, half a year, a year, 500 days and every year
  after — a run of days with everything you set yourself done. Each one says so once, with the
  breath cue breathing at you.
- Backup: your log, presets, goals and reminders written to one JSON file you keep, and read
  back on any phone. Sittings are merged rather than overwritten — importing an old export onto
  a phone you have kept practising on keeps both sets, so a log can only ever grow.
- A feedback form, opened in your browser. The app sends nothing on its own.
- A support page. The app is free and stays free; if you want to send something, PayPal opens in
  your browser. Nothing is charged from the app, and it is never told whether you did.
- A link to the store listing, for rating it.
- Reminders: as many as you like, each with its own name and time — daily, or on the days you
  pick, every week or in the odd or even weeks of the year. Each one is either a quiet
  notification or an alarm that rings until you dismiss it. Nothing is asked of you until you
  make the first one.

## Installing on a device

You need `adb`; everything else comes down with the Gradle build. Android Studio already installed it
under the SDK, so put that on your PATH rather than `apt install adb` — the packaged one is several
major versions behind and a mismatched `adb` fights the SDK's own server:

```sh
# in ~/.bashrc
export ANDROID_HOME="$HOME/Android/Sdk"
export PATH="$PATH:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator"
```

Open a new shell, then `adb version` should report 37.x or newer. Without Android Studio, grab the
[platform tools](https://developer.android.com/tools/releases/platform-tools) directly. Point
`local.properties` at your SDK (`sdk.dir=/path/to/Android/Sdk`) if it isn't there already.

### Over USB

1. On the phone: Settings → About phone → tap "Build number" seven times, then
   Settings → Developer options → enable **USB debugging**.
2. Plug it in and accept the "Allow USB debugging?" prompt.
3. Check it is seen, then build and install:

```sh
adb devices          # should list your phone as "device", not "unauthorized"
./gradlew installDebug
```

The app appears in the launcher as **OpenBreath**.

### Over Wi-Fi

Same as above, without the cable. Both machines have to be on the same network. On Android 11+,
use Developer options → **Wireless debugging** → "Pair device with pairing code":

```sh
adb pair 192.168.1.42:37105     # host:port and code from the pairing dialog
adb connect 192.168.1.42:5555   # host:port from the wireless debugging screen
./gradlew installDebug
```

### Installing a prebuilt APK

If you already have the APK — from CI, or built elsewhere with `./gradlew assembleDebug`:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

`-r` reinstalls over an existing copy and keeps its data. It fails if the installed app was signed
with a different key — uninstall first (`adb uninstall io.github.wlemkens.openbreath`), which also wipes your
presets.

### More than one device attached

`adb` refuses to guess. Name the target:

```sh
adb devices                      # copy the serial
adb -s <serial> install -r app/build/outputs/apk/debug/app-debug.apk
```

Gradle installs to *every* attached device, so unplug the ones you don't mean or use `adb` directly.

## Running on the emulator

```sh
$ANDROID_HOME/emulator/emulator -list-avds
$ANDROID_HOME/emulator/emulator -avd <name> &
./gradlew installDebug
```

### It segfaults on startup

On a hybrid-graphics laptop it will, and the crash comes right after `Created extended window`.
The emulator picks the discrete GPU for Vulkan but the integrated one for OpenGL, reports
`glInteropSupported: false`, and dies. Put both on the same GPU:

```sh
__NV_PRIME_RENDER_OFFLOAD=1 __GLX_VENDOR_LIBRARY_NAME=nvidia \
  $ANDROID_HOME/emulator/emulator -avd openbreath &
```

`Graphics Adapter Vendor` in the startup log should then name the same vendor as
`Selecting Vulkan device` a few lines above. Without a discrete GPU to offload to, `-gpu
swiftshader_indirect` renders in software instead — slower, but it does not care about any of this.

### `Unable to connect to adb daemon on port: 5037`

The adb server isn't running: `adb start-server`. Any `adb` command starts it, so this usually means
the emulator was the first thing to touch adb in a fresh login session. Harmless once it's up, and
the emulator reconnects on its own.

If `-list-avds` prints nothing, create one first. `minSdk` is 26 and `targetSdk` 36, so an API 36
image is the honest default:

```sh
sdkmanager "system-images;android-36;google_apis;x86_64"
avdmanager create avd -n openbreath -k "system-images;android-36;google_apis;x86_64"
```

Both live in `$ANDROID_HOME/cmdline-tools/latest/bin`. Take the `google_apis` image rather than the
bare one — the plain AOSP image has no Play services and boots into a launcher that hides half the
Settings screens you need for notification-policy access.

## Release builds

Every APK must be signed before a device will install it. Debug builds are signed automatically
with a throwaway key Gradle keeps in `~/.android/debug.keystore`; release builds need one of your
own. The wiring is already in [app/build.gradle.kts](app/build.gradle.kts) — it reads
`keystore.properties` if that file exists, and quietly builds unsigned if it doesn't, so anyone
without the key can still build and check everything else.

### Generating the key

Once, and then never again — see the warning below.

```sh
keytool -genkeypair -v \
  -keystore breath-upload.p12 -storetype PKCS12 \
  -keyalg RSA -keysize 4096 -validity 10000 \
  -alias upload
```

It asks for a store password, then your name and organisation (any of which may be left blank),
then confirmation. Let it prompt rather than passing `-storepass` on the command line, which
would leave the password in your shell history. `-validity 10000` is about 27 years: Play
requires a key valid past 2033, and an expired key cannot sign updates.

Then copy [keystore.properties.example](keystore.properties.example) to `keystore.properties` and
fill in the four values:

```properties
storeFile=breath-upload.p12
storePassword=…
keyAlias=upload
keyPassword=…   # the same value: PKCS12 has only one password
```

PKCS12 keystores cannot hold a key password different from the store password — keytool warns
and ignores the second one — so both properties take the same value. Gradle still wants both.

`storeFile` is relative to the project root, and an absolute path works if you would rather keep
the keystore outside the repository altogether. `keystore.properties` and every keystore extension
(`*.p12`, `*.jks`, `*.keystore`, `*.pfx`) are gitignored, and must stay that way — a release key
that reaches a remote is compromised, and cannot be rotated for an app that has already been
published.

### Building

```sh
./gradlew assembleRelease   # app/build/outputs/apk/release/app-release.apk
./gradlew bundleRelease     # app/build/outputs/bundle/release/app-release.aab — what Play wants
```

Check what came out:

```sh
$ANDROID_HOME/build-tools/*/apksigner verify --print-certs -v app/build/outputs/apk/release/app-release.apk
```

If the filename still says `app-release-unsigned.apk`, `keystore.properties` was not found.

### Two things that bite

**The key is the app's identity, permanently.** Play refuses an update signed by a different key
from the one on the listing, and so does a phone: installing a release build over the debug one
fails with `INSTALL_FAILED_UPDATE_INCOMPATIBLE` until the old one is uninstalled — which takes
your practice log with it. Back the keystore up somewhere you will still have in ten years.

That is less final than it sounds for a new app, because new apps must enrol in **Play App
Signing**: Google holds the actual signing key and the one you just made is only an *upload* key,
which support can reset if you lose it. Losing an app signing key you manage yourself ends the
listing.

**`versionCode` must increase with every upload.** It is `1` in
[app/build.gradle.kts](app/build.gradle.kts); Play rejects a repeat.

## Licence

Copyright (c) 2026 Wim Lemkens.

OpenBreath is free software: you can redistribute it and/or modify it under the terms of the **GNU
General Public License**, either version 3 of the License, or (at your option) any later version —
see [LICENSE](LICENSE). It is distributed in the hope that it will be useful, but WITHOUT ANY
WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.

One additional permission applies, under section 7 of that licence — see
[LICENSE-EXCEPTION](LICENSE-EXCEPTION). It allows conveying OpenBreath through an app store
whose terms restrict recipients in ways the GPL forbids, which is what makes distribution on
the App Store possible at all. It grants nothing over the source: a copy from a store is still
a GPL copy, and the source is still yours to ask for and pass on.

The bundled audio is **not** covered by that grant. `app/src/main/res/raw/*.mp3` are cuts of
files from the
[freesound_community](https://pixabay.com/users/freesound_community-46691455/) Pixabay account,
released under [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/). CC0 asks for no
attribution; the credit is here because it is the decent thing, not because it
is owed.
