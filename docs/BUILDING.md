# Building, testing and releasing OpenBreath

Everything operational lives here: running the app on an emulator, a phone or a desktop, and
getting a build into Play, TestFlight or a sideloader. [The README](../README.md) is the short
version.

## The whole check, before anything else

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:desktopTest \
          :app:compileKotlinIosArm64 :app:compileTestKotlinIosArm64
```

All five run on Linux — Kotlin/Native downloads a cross-compiling distribution, so iOS code is
type-checked here and there is no reason to wait for CI to find a typo. Only *linking* a framework
and booting a simulator need Xcode on a Mac.

`:app:desktopTest` runs the whole shared suite — session engine, stored shapes, breath cue, DSP —
on the JVM, needs no Android SDK, and is the fastest of the five. Run it first.
`compileTestKotlinIosArm64` is the one that catches shared code reaching into `androidMain`.

## The desktop

```sh
./gradlew :app:run
```

One target for all three desktops; nothing branches on the operating system except where the log is
stored. What is per-platform is only the installer, because `jpackage` builds for the machine it
runs on and no other:

```sh
./gradlew :app:packageDeb    # on Linux
./gradlew :app:packageMsi    # on Windows, needs WiX
./gradlew :app:packageDmg    # on macOS
```

CI builds all three on every push, as the `OpenBreath-deb`, `OpenBreath-msi` and `OpenBreath-dmg`
artifacts.

**They are unsigned**, which you will notice. SmartScreen warns about the `.msi` and wants "More
info" → "Run anyway"; Gatekeeper refuses the `.dmg` on a double-click and wants right-click → Open.
Both need a code-signing certificate, which does not exist yet. Nothing is wrong with the installer.

The log, the presets and the goals live where the platform keeps such things —
`%APPDATA%\OpenBreath` on Windows, `~/Library/Application Support/OpenBreath` on macOS,
`~/.local/share/openbreath` on Linux — and a backup file moves them between any two machines,
phones included.

## Android

You need `adb`; everything else comes down with the Gradle build. Android Studio already installed
it under the SDK, so put that on your PATH rather than `apt install adb` — the packaged one is
several major versions behind and a mismatched `adb` fights the SDK's own server:

```sh
# in ~/.bashrc
export ANDROID_HOME="$HOME/Android/Sdk"
export PATH="$PATH:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator"
```

Open a new shell, then `adb version` should report 37.x or newer. Without Android Studio, grab the
[platform tools](https://developer.android.com/tools/releases/platform-tools) directly. Point
`local.properties` at your SDK (`sdk.dir=/path/to/Android/Sdk`) if it isn't there already.

### On the emulator

```sh
$ANDROID_HOME/emulator/emulator -list-avds
$ANDROID_HOME/emulator/emulator -avd <name> &
./gradlew installDebug
```

If `-list-avds` prints nothing, create one. `minSdk` is 26 and `targetSdk` 36, so an API 36 image is
the honest default:

```sh
sdkmanager "system-images;android-36;google_apis;x86_64"
avdmanager create avd -n openbreath -k "system-images;android-36;google_apis;x86_64"
```

Both live in `$ANDROID_HOME/cmdline-tools/latest/bin`. Take the `google_apis` image rather than the
bare one — the plain AOSP image has no Play services and boots into a launcher that hides half the
Settings screens you need for notification-policy access.

**It segfaults on startup** on a hybrid-graphics laptop, right after `Created extended window`. The
emulator picks the discrete GPU for Vulkan but the integrated one for OpenGL, reports
`glInteropSupported: false`, and dies. Put both on the same GPU:

```sh
__NV_PRIME_RENDER_OFFLOAD=1 __GLX_VENDOR_LIBRARY_NAME=nvidia \
  $ANDROID_HOME/emulator/emulator -avd openbreath &
```

`Graphics Adapter Vendor` in the startup log should then name the same vendor as `Selecting Vulkan
device` a few lines above. Without a discrete GPU to offload to, `-gpu swiftshader_indirect` renders
in software instead — slower, but it does not care about any of this.

**`Unable to connect to adb daemon on port: 5037`** means the adb server isn't running:
`adb start-server`. Any `adb` command starts it, so this usually means the emulator was the first
thing to touch adb in a fresh login session. Harmless once it's up, and the emulator reconnects on
its own.

### On a phone, over USB

1. On the phone: Settings → About phone → tap "Build number" seven times, then Settings →
   Developer options → enable **USB debugging**.
2. Plug it in and accept the "Allow USB debugging?" prompt.
3. Check it is seen, then build and install:

```sh
adb devices          # should list your phone as "device", not "unauthorized"
./gradlew installDebug
```

The app appears in the launcher as **OpenBreath**.

### On a phone, over Wi-Fi

Same, without the cable, both machines on the same network. On Android 11+, use Developer options →
**Wireless debugging** → "Pair device with pairing code":

```sh
adb pair 192.168.1.42:37105     # host:port and code from the pairing dialog
adb connect 192.168.1.42:5555   # host:port from the wireless debugging screen
./gradlew installDebug
```

### A prebuilt APK, and more than one device

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

`-r` reinstalls over an existing copy and keeps its data. It fails if the installed app was signed
with a different key — uninstall first (`adb uninstall io.github.wlemkens.openbreath`), which also
wipes your presets.

With several devices attached `adb` refuses to guess, so name the target with `adb -s <serial>`.
Gradle installs to *every* attached device: unplug the ones you don't mean, or use `adb` directly.

### Store screenshots

`docs/store/android/*.png` are what the Play listing shows, and a screenshot is a claim about what
the app looks like. **A change to a screen that appears there is not finished until the screenshot
is retaken**, in the same commit:

```sh
./gradlew :app:installDebug && python3 docs/store/screenshots.py
```

It clears the app's data and imports a generated log, so run it on an emulator and never on a phone
that holds real practice. It taps by the labels `uiautomator` reports, so renaming a button can
break the run rather than the app: `no 'X' on screen` means the script is out of date with the
interface it photographs. The ten shots are listed in [docs/store/README.md](store/README.md).

The Apple set is an XCUITest instead — `gh workflow run build.yml -f screenshots=true` drives a 6.9"
simulator and a 13" iPad in CI. Its output is gitignored, so nothing goes red when it is stale; say
out loud that it needs refilming whenever a shared screen changes.

## Signing an Android release

Every APK must be signed before a device will install it. Debug builds are signed automatically with
a throwaway key Gradle keeps in `~/.android/debug.keystore`; release builds need one of your own.
The wiring is already in [app/build.gradle.kts](../app/build.gradle.kts) — it reads
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

It asks for a store password, then your name and organisation (any of which may be left blank), then
confirmation. Let it prompt rather than passing `-storepass` on the command line, which would leave
the password in your shell history. `-validity 10000` is about 27 years: Play requires a key valid
past 2033, and an expired key cannot sign updates.

Then copy [keystore.properties.example](../keystore.properties.example) to `keystore.properties`:

```properties
storeFile=breath-upload.p12
storePassword=…
keyAlias=upload
keyPassword=…   # the same value: PKCS12 has only one password
```

PKCS12 keystores cannot hold a key password different from the store password — keytool warns and
ignores the second one — so both properties take the same value. Gradle still wants both.

`storeFile` is relative to the project root, and an absolute path works if you would rather keep the
keystore outside the repository altogether. `keystore.properties` and every keystore extension
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
$ANDROID_HOME/build-tools/*/apksigner verify --print-certs -v \
  app/build/outputs/apk/release/app-release.apk
```

If the filename still says `app-release-unsigned.apk`, `keystore.properties` was not found.

### Two things that bite

**The key is the app's identity, permanently.** Play refuses an update signed by a different key
from the one on the listing, and so does a phone: installing a release build over the debug one
fails with `INSTALL_FAILED_UPDATE_INCOMPATIBLE` until the old one is uninstalled — which takes your
practice log with it. Back the keystore up somewhere you will still have in ten years.

That is less final than it sounds for a new app, because new apps must enrol in **Play App
Signing**: Google holds the actual signing key and the one you just made is only an *upload* key,
which support can reset if you lose it. Losing an app signing key you manage yourself ends the
listing.

**`versionCode` must increase with every upload,** and Play rejects a repeat. It looks after itself:
[app/build.gradle.kts](../app/build.gradle.kts) takes `OPENBREATH_BUILD` when CI sets it and counts
the commits otherwise, so `1.0.89 (89)` is the eighty-ninth commit and there is no number to
remember to bump. `git rev-list --count HEAD` says what the next build will call itself.

The one thing to know is what follows from it: **a second upload needs a second commit.** Rebuild
the same commit and Play is handed a version it has already taken — which is the right answer, since
it is also the same app. Commit the change you are shipping, then build.

## Publishing to Play

Once set up, shipping a build to the closed test is one command:

```sh
./gradlew :app:publishBundle                        # closed testing ("alpha")
./gradlew :app:publishBundle -PplayTrack=internal   # internal testing
```

It builds, signs and uploads in one go. Run the aggregate `publishBundle`: it drives the per-variant
`publishReleaseBundle`, which the plugin registers too late for Gradle to select by name off the
command line.

**"alpha" is closed testing.** The Console says "Closed testing", the API has always said alpha, and
they are the same track. A closed track created by hand carries whatever name it was given —
`-PplayTrack=` for that. This is the one setting that can put a tester build in front of the wrong
audience, so read it twice before a release that matters.

**It cannot create the app.** The Developer API uploads to an app that already exists on Play, and
nothing more. The first bundle, the listing, the content declarations and the closed-testing track
itself go up by hand through the Console. After that first manual upload, every update is the
command above.

### The release notes write themselves

`generateReleaseNotes` runs first, and puts the recent commit subjects — newest first, until Play's
500-character limit runs out — in `src/main/play/release-notes/en-US/default.txt`. That file is
generated and gitignored; `./gradlew :app:generateReleaseNotes` on its own prints what it would say,
which is the way to read the notes before they go anywhere. **A commit subject is therefore
user-facing copy.**

Two things follow from taking them verbatim, both by design:

- **A commit about the build appears too.** "Count the commits for the version code" means nothing
  to a tester. Filtering to commits that touch app sources is possible and is not done, because a
  heuristic that silently drops a real change is worse than a line of noise.
- **It says "the most recent changes", not "since your last build".** Two releases close together
  repeat a line, since nothing records what was published last.

`src/main/play`, not `src/androidMain/play` — the plugin uses AGP's source-set name and not the
Kotlin Multiplatform one. Getting it wrong loses the notes without an error.

### The service account

The upload needs a Google Cloud service account that Play trusts. It is made in the **Google Cloud
Console**, not the Play Console — the Play Console has no page for creating one, and the only step
that happens there is the invite in 5.

Do not go looking for *Setup → API access* in the Play Console. That page is **gone**: Google dropped
the requirement to link a developer account to a Cloud project, and took the page with it. Most
walkthroughs still describe it, which is what makes this confusing rather than hard.

Done once:

1. **console.cloud.google.com** → create a project, or select one. Any project will do; it is only a
   container. **Nothing below appears until a project is selected** — this is why *Service accounts*
   can seem to be missing from the menu entirely.
2. *APIs & Services → Library* → search **Google Play Android Developer API** → **Enable**. Easy to
   skip, and skipping it fails the upload later on a permission error that never mentions it.
3. *IAM & Admin → Service accounts* → **Create service account**. Name it anything. It needs **no
   Cloud roles** — Play grants its own rights in 5, and the Owner role some guides tell you to add
   and then remove is not needed at any point.
4. On that account: *Keys → Add key → Create new key → JSON*. It downloads once and cannot be
   fetched again — a lost key means making another. Save it as `play-service-account.json` in the
   project root. It is gitignored and must stay that way: it can ship an update to every installed
   phone, which makes it as sensitive as the keystore even though it signs nothing.
5. **Play Console** → *Users and permissions* → **Invite new users** → paste the service account's
   email (`…@….iam.gserviceaccount.com`) → grant it, for this app only: *Release to testing tracks*,
   and *Release to production* only if you want that from here too. Give it a few minutes; the grant
   is not instant, and a publish attempted straight away can fail once and then work.

Without the file the publish tasks are simply switched off, so a clone with no credentials builds
and tests exactly as before — same arrangement as `keystore.properties`.

## Getting a build onto an iPhone

Two routes. TestFlight is better for anyone with the team credentials; sideloading is for handing a
build to someone who has none.

### TestFlight, if you are on the team

Nothing about this waits for the App Store listing. **Internal testers need no App Review** — that
is only external testers — so the App Store Connect users on team `AD8Y56HX64` can install a build
the moment it is uploaded and processed. No pairing file, no VPN, no seven-day refresh, no cable.

1. Run the build workflow by hand with the upload asked for — `gh workflow run build.yml -f
   app_store=true`, or the checkbox in the Actions tab. It defaults to off because this is the only
   thing here that burns a build number, and it mails every tester.
2. Wait for the build to leave "Processing" in App Store Connect → TestFlight. Export compliance is
   already answered by `ITSAppUsesNonExemptEncryption` in the Info.plist, so it should not stop to
   ask.
3. TestFlight → **Internal Testing** → a group → add yourself, then install TestFlight from the App
   Store on the phone and accept.

Two things to know. A build **expires 90 days** after upload, so a phone that has sat unused wants
the workflow run again rather than debugging. And do not accept the Paid Applications Agreement that
App Store Connect keeps offering — it voids the fee waiver, and TestFlight does not need it.

**TestFlight installs under the phone's Media & Purchases account, and there is no separate
TestFlight login.** So the invited address has to be the Apple Account the App Store on that device
is signed into — not merely an address whose mail you can read. Read it off the phone before
inviting anyone: App Store → profile picture, top right, where the address is shown under the name.
Going through Instellingen → Media en aankopen shows the same thing but puts **Log uit** under your
thumb, which is not a button to offer someone on a borrowed phone.

That is the whole difficulty with a phone you do not own. The account signed in is the owner's, its
mail goes somewhere you cannot read, and switching Media & Purchases to an account of your own locks
the device out of associating with another for 90 days. Do not do it — use a public link below,
which redeems against whatever account is already signed in and needs no email at all.

### Someone else's phone: external testers, not internal

An **internal** tester is a user on the App Store Connect account, and inviting one means the
phone's owner has to accept an App Store Connect invitation, hold an Apple Account for exactly the
invited address, and have two-factor set up on it. When that Apple Account is not ready the sign-in
page simply returns to itself, which reads as a broken invite and is really an unfinished account.
It also gives a tester access to the account, which is the wrong trade for someone who only wants to
try the app.

**External testers need none of that** — no team access, no App Store Connect login, just an email,
the TestFlight app and a tap. Add a group under TestFlight → External Testing, fill the Test
Information (feedback email, what to test, beta description), and submit the build for **Beta App
Review**: about a day, per version rather than per build, and far lighter than App Store review. Up
to 10,000 testers.

**Enable the public link on that group and even the email goes away.** The link redeems against
whichever Apple Account the phone is already signed into, so nothing has to be invited, accepted or
matched — which is what makes it the answer for a borrowed phone. The price is the Beta App Review
wait that internal testing skips, so it buys a day and spends nobody's account.

If an internal invite is already stuck, remove the tester from Users as well as from the group
before re-inviting them externally.

### Sideloading, for a phone with no team access

CI publishes everything this needs. Every push builds an unsigned `.ipa` and writes a
SideStore/AltStore *source* beside it on the rolling `latest` prerelease, at two URLs that never
change:

- source: `https://github.com/wlemkens/openbreath/releases/download/latest/apps.json`
- ipa: the same tag, `OpenBreath-<version>.ipa`

The ipa is unsigned on purpose; a sideloader re-signs it with the Apple ID it is given, so a free one
is enough and no Mac is involved. Add the source URL once in [SideStore](https://sidestore.io/) or
[AltStore](https://altstore.io/), and every later push appears there as an update.

**Linux installs SideStore fine**, which is worth knowing because most guides assume Windows or a
Mac. [iloader](https://github.com/nab138/iloader/releases) ships a deb, an rpm and an AppImage for
x86_64 and aarch64, and it makes the pairing file itself:

```sh
sudo apt install usbmuxd fuse curl
sudo dpkg -i iloader-*.deb
```

Then USB, trust the computer, sign in with an Apple Account, install SideStore. The phone needs a
passcode and iOS 15+. The helper VPN has to be connected any time SideStore installs or refreshes
anything.

**This route rots, and not on our side.** Every step above is something Apple can break, and has:
the helper VPN was StosVPN until it was pulled from the App Store, then StikDebug, and the docs now
say **LocalDevVPN** — so check [SideStore's own
prerequisites](https://docs.sidestore.io/docs/installation/prerequisites) rather than this
paragraph, which is a snapshot of August 2026 and nothing more. AltStore wants AltServer reachable
on the same network, and an iOS update can knock over either. A free Apple ID also expires the app
after **seven days** and allows only three sideloaded apps at once. If the phone says the store is
unavailable or refuses to refresh, suspect the chain before the ipa.

**Turn off Offload Unused Apps** — Settings → Apps → App Store on iOS 18+, where it moved from the
top level, and also under General → iPhone Storage. Offloading deletes the binary and leaves the
icon, which then tries to re-download from the App Store; a sideloaded app is not there, so tapping
it says the app is no longer available. There is no recovery but installing again, and it applies to
a sideloaded OpenBreath exactly as it does to the sideloader itself.

`gh release download latest --pattern 'OpenBreath-*.ipa'` gets the ipa directly, for Sideloadly, or
for `ideviceinstaller` over USB from Linux once it is signed with a development profile.

## Reading a CI failure without a token

Job logs are a 403 without authentication, even on a public repository. Annotations are not:

```sh
curl -s https://api.github.com/repos/wlemkens/openbreath/commits/<sha>/check-runs
curl -s https://api.github.com/repos/wlemkens/openbreath/check-runs/<id>/annotations
```

That is why the scripts in `.github` print `::error title=…::` rather than plain text on failure: a
workflow-command line becomes an annotation, and an annotation can be read back by anyone. The
unauthenticated API allows 60 requests an hour per IP, so poll on the order of minutes.
