# Breath

An Android app for heart coherence breathing meditations.

## Features

- Presets for the usual patterns, and your own timings for the in, hold, out and hold phases.
- A breath cue to follow with your eyes: a sphere that opens as you breathe in and closes as you
  breathe out, either as a glow or a point cloud, in a colour you pick.
- Soundscapes per phase — waves that rise and fall with the breath, a marker at each phase change
  (singing bowl, bell, or an mp3 of your own), or silence.
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

The app appears in the launcher as **Breath**.

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
with a different key — uninstall first (`adb uninstall io.github.wlemkens.breath`), which also wipes your
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
  $ANDROID_HOME/emulator/emulator -avd breath &
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
avdmanager create avd -n breath -k "system-images;android-36;google_apis;x86_64"
```

Both live in `$ANDROID_HOME/cmdline-tools/latest/bin`. Take the `google_apis` image rather than the
bare one — the plain AOSP image has no Play services and boots into a launcher that hides half the
Settings screens you need for notification-policy access.

## Release builds

`./gradlew assembleRelease` produces an **unsigned** APK, which no device will install: there is no
`signingConfig` in [app/build.gradle.kts](app/build.gradle.kts) yet. Adding one means generating a
keystore and referencing it from a `keystore.properties` that stays out of git (both are already
gitignored). Until then, debug builds are the way onto a device.

## Licence

Copyright (c) 2026 Wim Lemkens.

Breath is free software: you can redistribute it and/or modify it under the terms of the **GNU
General Public License**, either version 3 of the License, or (at your option) any later version —
see [LICENSE](LICENSE). It is distributed in the hope that it will be useful, but WITHOUT ANY
WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.

The bundled audio is **not** covered by that grant. `app/src/main/res/raw/*.mp3` are cuts of files
from the [freesound_community](https://pixabay.com/users/freesound_community-46691455/) Pixabay
account, released under [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/). CC0 asks for
no attribution; the credit is here because it is the decent thing, not because it is owed.
