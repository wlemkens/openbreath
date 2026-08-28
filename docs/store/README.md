# Store assets

Everything a listing needs that lives in the repository rather than in a console.

    listing.md                        copy for both stores, with the character counts checked
    play-icon-512.png                 Play's app icon, from the same 1024 IconGen writes for iOS
    play-feature-graphic-1024x500.png required by Play; no promo video, so nothing overlays it
    android/*.png                     ten phone screenshots, 1080x1920 (9:16, what Play asks for)
    screenshots.py                    what took the Android set, so a release need not tap by hand

`../privacypolicy.html` is the page both stores ask for the URL of. It has to be hosted; the
listing cannot be filled in without it.

**`ios/` is gitignored, and it is where everything Apple wants appears.** Both workflows below write
there, so the same command fills the same directory on a laptop as on a runner:

    ios/iphone/*.png                  nine screenshots, 1320x2868 (the 6.9" set Apple asks for)
    ios/ipad/*.png                    the same nine, 2064x2752 (the 13" set it also asks for)
    ios/{iphone,ipad}-breathing.mp4   the two previews, 22s of the cue
    ios/preview-work/                 what the filming left behind, including the driver's log

Not carried in the repository: 36 MB a regeneration against a 1 GB LFS quota, when the whole set is
one workflow away. The Android screenshots *are* carried, and that asymmetry is worth naming — they
are 2 MB, and they are what `screenshots.py` needs to be checked against. The cost of the choice is
that an iOS set can go stale without a diff to notice it, so the rule below is the only guard there
is.

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

Apple takes **one set per device family** and scales it down for smaller screens itself: 6.9" at
1320x2868 for the phone, and 13" at 2064x2752 for the iPad. Anything smaller is refused at upload, so
a 6.1" phone cannot produce them at all — and framing Android screenshots in an iPhone bezel is a
rejection.

**The iPad set is required because the binary says the app runs on an iPad**, which is Xcode's
default and is now stated explicitly in `iosApp/project.yml`. Device family and this set are one
decision: dropping to family 1 drops the requirement, and the error that asks for it —
"You must upload a screenshot for 13-inch iPad displays" — says nothing about where it came from.
Nothing is laid out for a tablet and nothing needs to be; a large screen gets a bigger sphere.

Nine rather than the Android set's ten. There is no reminders screen on iOS to photograph, which is
the same absence that keeps reminders out of the App Store copy.

They come off a simulator in CI, because nothing else here can tap one:

    gh workflow run build.yml -f screenshots=true
    # on a branch: --ref <branch>, or the input is "unexpected"

Then download the `app-store-screenshots` artifact.

It runs in the `ios` job, and twice over: the newest Pro Max the runner's Xcode knows about, then the
newest 13" iPad Pro. Each is erased so the first-run question exists to be photographed, handed the
same generated log the Android run imports, and driven by `iosApp/StoreScreenshots` — an XCUITest,
because `simctl` can boot, install and photograph a simulator but not touch one.
`.github/ios-screenshots.sh` is the part around the test, and it asserts the pixel size of every shot
rather than leaving App Store Connect to say no.

The inputs are read from the workflow file on the ref being dispatched, and `gh` defaults to the
default branch — so a run asked for from a branch whose workflow file is the only one that has them
answers HTTP 422 rather than anything helpful.

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

## The App Store previews

Optional — a listing goes live without them — and worth having for one screen only: the cue is an
animation, and a still frame cannot show whether it moves.

    gh workflow run build.yml --ref <branch> -f previews=true

**Not kept in the repository**, unlike the screenshots: the two files are 33 MB, every refilm adds
another copy to LFS forever, and a free LFS quota is 1 GB. They are a workflow away whenever they are
wanted, which a screenshot is too — the difference is only that 2 MB of stills is worth the
convenience and 33 MB of video is not. Download the `app-store-previews` artifact and upload from
there.

Twenty-two seconds of the default 5.5/5.5 breath, which is two whole cycles inside Apple's 15-to-30
second window, filmed on the same two device classes as the screenshots: 886x1920 for the phone and
1200x1600 for the iPad, which are the preview sizes and not the screenshot ones.

**The sound is rendered, not recorded, and it is still the app's own.** Apple requires an audio track
— stereo AAC, 256 kbps — and `simctl` records the display and nothing else, so `PreviewAudio` in
desktopTest asks the shared `WaveDsp` for 22 seconds of the same wave bed a phone would play, driven
by the same `phaseAt` easing and the same edge dip at every boundary. A silent track would have
satisfied Apple and misrepresented the app. The video is filmed on a fresh install for the same
reason: waves on every phase is the default, so what the soundtrack claims is what a reader hears.

Three parts have to agree and each is worth knowing when one breaks:

- **`simctl` cannot tap and XCUITest cannot record**, so the run is both at once: `AppPreview` taps
  Start, then writes the host clock at the instant the first inhale begins, and the script trims the
  capture to that number. Guessing the offset instead would mean guessing how long an install took.
- **`-only-testing`** keeps the two test classes apart. Without it a screenshot run would also film
  22 seconds of nothing, and a preview run would take nine pictures nobody wants.
- **ffmpeg** does the scale, the frame rate, the trim and the mux, and is installed by the script if
  the runner has none. The output is checked for size, duration and the presence of sound before the
  job goes green, because a preview refused at upload costs a round trip to learn one number.
