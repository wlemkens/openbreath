# Breath

An Android app for heart coherence breathing meditations.

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

Code is MIT — see [LICENSE](LICENSE). The bundled audio is **not** covered by that grant; see
[CLAUDE.md](CLAUDE.md).
