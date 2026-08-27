# Store assets

Everything a listing needs that lives in the repository rather than in a console.

    listing.md                        copy for both stores, with the character counts checked
    play-icon-512.png                 Play's app icon, from the same 1024 IconGen writes for iOS
    play-feature-graphic-1024x500.png required by Play; no promo video, so nothing overlays it
    android/*.png                     ten phone screenshots, 1080x1920 (9:16, what Play asks for)
    ios/*.png                         nine, 1320x2868 (6.9", the one size Apple now asks for)
    screenshots.py                    what took the Android set, so a release need not tap by hand

`../privacypolicy.html` is the page both stores ask for the URL of. It has to be hosted; the
listing cannot be filled in without it.

## Retaking the screenshots

    ./gradlew :app:installDebug && python3 docs/store/screenshots.py

It clears the app's data and imports a generated log of seven months' practice, because the
achievements screen is only worth photographing with a real streak behind it and a real one cannot
be arranged on purpose. Run it on an emulator. The demo data is what makes the numbers on the log,
goals, achievements and milestone shots consistent with each other.

Play takes two to eight phone screenshots. Of these ten, the four that carry the app are
`session-breathe-in`, `log`, `achievements` and `sound-per-phase`; `firstrun` is there because it
shows that nothing is decided for you, and `milestone` because it is the only screen that says
what the log is for.

## The App Store screenshots

Apple takes **one** iPhone set and scales it down for smaller phones itself: 6.9", 1320x2868, or
1290x2796 in the same slot. Anything smaller is refused at upload, so a 6.1" phone cannot produce
them at all — and framing Android screenshots in an iPhone bezel is a rejection.

Nine rather than the Android set's ten. There is no reminders screen on iOS to photograph, which is
the same absence that keeps reminders out of the App Store copy.

They come off a simulator in CI, because nothing else here can tap one:

    gh workflow run build.yml -f screenshots=true
    # on a branch: --ref <branch>, or the input is "unexpected"

Then download the `app-store-screenshots` artifact. The inputs are read from the workflow file on
the ref being dispatched, and `gh` defaults to the default branch — so a run asked for from a branch
whose workflow file is the only one that has them answers HTTP 422 rather than anything helpful. It runs in the `ios` job, boots the newest Pro
Max the runner's Xcode knows about, erases it, hands the app the same generated log the Android run
imports, and drives it with `iosApp/StoreScreenshots` — an XCUITest, because `simctl` can boot,
install and photograph a simulator but not touch one. `.github/ios-screenshots.sh` is the part
around the test, and it asserts the pixel size of every shot rather than leaving App Store Connect
to say no.

**It is behind an input, and so is the App Store upload.** A plain manual run does neither now.

The two ways this breaks are worth knowing before reading a red log. It taps by accessibility label,
so **renaming a button breaks the run rather than the app** — exactly as the Android script does, and
the annotation says which label vanished. And the demo log arrives in the app's launch environment
rather than through the file picker, because a picker needs the file somewhere it can browse to; the
hook that reads it is in `iosMain/MainViewController.kt` and does nothing without the variable.

### By hand on a phone, if it comes to that

It has to be a Pro Max or Plus for the size above. A smaller phone can be upscaled into the slot —
an iPhone 15 is 1179x2556, 9% under 1290x2796 — which uploads and passes review, and softens every
letter on the listing. An escape hatch, not a preference.

The log has to be seeded there too, or the achievements and milestone shots photograph an empty app:

    python3 docs/store/screenshots.py --demo-json /tmp/openbreath-demo.json

Hand that file to the phone (AirDrop, or Files) and import it through Settings ▸ Advanced ▸ Import,
which is the route the Android run drives. It replaces presets, goals and reminders and merges the
log, so use a phone with no practice worth keeping — the same warning as the emulator.
