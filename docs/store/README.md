# Store assets

Everything a listing needs that lives in the repository rather than in a console.

    listing.md                        copy for both stores, with the character counts checked
    play-icon-512.png                 Play's app icon, from the same 1024 IconGen writes for iOS
    play-feature-graphic-1024x500.png required by Play; no promo video, so nothing overlays it
    android/*.png                     ten phone screenshots, 1080x1920 (9:16, what Play asks for)
    screenshots.py                    what took them, so the next release need not tap by hand

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

## What is not here

**The App Store screenshots.** Apple wants one 6.9" iPhone set (1320x2868), and there is no way to
take them from Linux: `simctl` can boot and film a simulator but cannot tap one, and the cue is
still until Start is pressed. So they need a Mac with Xcode 26 — the same constraint as linking
the app at all — or a phone. Framing Android screenshots in an iPhone bezel is a rejection.
