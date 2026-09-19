# Store listing copy

Two listings, deliberately not the same text, and the gap has narrowed to one feature. **Reminders
are on iOS now**, so the App Store copy names them — but only as a notification: Android's alarm
that rings until dismissed needs a tone that repeats, and the iOS equivalent is the Critical Alerts
entitlement, granted case by case. `canRingUntilDismissed` is what the app itself asks; the copy has
to agree with it. The **silencing of notifications** is still Android's alone and always will be:
no public API sets a Focus.

A listing may only name what that platform actually has, which is why this file has two of
everything. Nothing here claims a health benefit: coherence breathing is described as what it is,
a paced breath, and no store copy says it treats anything.

Neither listing mentions the support link. The app gives nothing in return for a payment and the
listing has no reason to raise the subject.

**`listing.nl.md` and `listing.fr.md` are this file in Dutch and French**, since the app is. Every
rule above applies to each of them — the platform split, no health claim, no support link, and the
four claims of the short description, which a translation is four claims of. Adding or dropping a
feature means all three change together, and a listing that names something one language's app
does not have is the same mistake as naming something one platform does not have.

One thing the translations do that this file cannot: the App Store subtitle is **one line** in
both, which is the fix noted below and never applied here.

## Google Play

**App name** (30 max)

    OpenBreath

**Short description** (80 max)

    Free paced breathing.
    No costs, no ads, no tracking, no catch.

**Full description** (4000 max)

    A breathing app for coherence practice: you set how long to breathe in, hold, breathe out and
    hold, and follow a sphere that opens and closes with the breath.

    Nothing is asked of you first. There is no account, no sign-in and no network, no tracking — the app makes
    no connection of any kind, so your practice log is on your phone and nowhere else.

    THE BREATH
    • Presets for the usual patterns — coherence 5.5, 4-6, 4-7-8, box 4 — and any timing of your own.
    • A sphere that opens as you breathe in and closes as you breathe out, as a cloud of points,
      bubbles, stars or a glow, in a colour you pick.
    • Sound per phase: waves that rise and fall with the breath, a soundwave whose overtones
      gather and dissolve, a marker at each turn — singing bowl, bell or metronome tick — an mp3
      of your own, or silence. The bowl and the tick are pitched by the turn they mark, so going
      into a breath in does not sound like going out.
    • A singing bowl when the session ends, so you can sit with your eyes shut and not wonder
      whether it is over.
    • Optional vibration at each phase change.
    • Optional flashlight that brightens as you breathe in and fades as you breathe out — the
      breath to follow with your eyes closed.
    • Optional silencing of notifications for the length of a sitting, put back exactly as it was
      afterwards.
    • A count in after you press start — four seconds by default, as long as you like — to put
      the phone down and shut your eyes before the first breath.
    • Time left, a dot per breath and a breath count, each hideable for a barer screen.

    WHAT IT REMEMBERS
    • Every sitting, day by day, with the pattern you breathed and the breaths you finished. The
      ones you cut short count too.
    • Goals, as many as you like: one sitting a day, a hundred breaths a day, an hour a week.
      They are counted from the log, so a goal set today already credits the practice behind it.
    • Streaks, totals and milestones at 3 days, a week, a month, 100 days, half a year and a
      year — each said once, quietly.
    • Reminders, as many as you like, each with its own name and time: daily, on the days you
      pick, weekly or in alternate weeks. Either a quiet notification or an alarm that rings
      until you dismiss it. One can be set to arrive only when the day's goal is still undone.
    • A backup of the lot — log, presets, goals and reminders — written to one file you keep and
      read back on any phone. Sittings merge rather than overwrite, so importing an old backup
      onto a phone you have kept practising on keeps both sets. The log can only grow.

    FREE, AND FREE SOFTWARE
    No ads, no accounts, no subscriptions, no in-app purchases and nothing to unlock. OpenBreath
    is released under the GNU General Public License v3 or later; the source is public.

## App Store

**Name** (30 max)

    OpenBreath

**Subtitle** (30 max) — **too long as written: 62 characters in a 30-character field**

    Free paced breathing.
    No costs, no ads, no tracking, no catch.

App Store Connect cannot take this, so whatever is on the live listing is something else and this
file has never matched it. Play's short description is the same two lines and fits, which is
probably how it went unnoticed. `Free paced breathing.` is 21 and would fit; the second line is
where the promotional text above now lives, and that field has room for it.

**Promotional text** (170 max, changeable without review) — **fixed copy, do not reword**

    Free paced breathing.
    No costs, no ads, no tracking, no catch.

62 characters. **This one is not to be rewritten** — not for length, not for polish, and not to
announce a new feature, which is what promotional text is usually used for and what would quietly
erode it. It is four claims and nothing else, and the only reason to touch it is that one of them
has stopped being true: the app is no longer free, or takes a payment for something, or carries an
advertisement, or collects anything. Each of those is a change this repository could not make
quietly anyway — see the Monetisation section of CLAUDE.md, which is the same check from the other
side. Anything short of that, leave it alone.

**Keywords** (100 max, commas, no spaces)

    breathing,coherence,breath,paced,meditation,calm,pranayama,timer,streak,offline,bowl

**Description** (4000 max)

    A breathing app for coherence practice: you set how long to breathe in, hold, breathe out and
    hold, and follow a sphere that opens and closes with the breath.

    Nothing is asked of you first. There is no account, no sign-in and no network — the app makes
    no connection of any kind, so your practice log is on your iPhone and nowhere else.

    THE BREATH
    • Presets for the usual patterns — coherence 5.5, 4-6, 4-7-8, box 4 — and any timing of your own.
    • A sphere that opens as you breathe in and closes as you breathe out, as a cloud of points,
      bubbles, stars or a glow, in a colour you pick.
    • Sound per phase: waves that rise and fall with the breath, a soundwave whose overtones
      gather and dissolve, a marker at each turn — singing bowl, bell or metronome tick — an mp3
      of your own, or silence. The bowl and the tick are pitched by the turn they mark, so going
      into a breath in does not sound like going out.
    • A singing bowl when the session ends, so you can sit with your eyes shut and not wonder
      whether it is over.
    • Optional haptics at each phase change.
    • Optional torch that brightens as you breathe in and fades as you breathe out — the breath
      to follow with your eyes closed.
    • A count in after you press start — four seconds by default, as long as you like — to put
      the phone down and shut your eyes before the first breath.
    • Time left, a dot per breath and a breath count, each hideable for a barer screen.

    WHAT IT REMEMBERS
    • Every sitting, day by day, with the pattern you breathed and the breaths you finished. The
      ones you cut short count too.
    • Goals, as many as you like: one sitting a day, a hundred breaths a day, an hour a week.
      They are counted from the log, so a goal set today already credits the practice behind it.
    • Streaks, totals and milestones at 3 days, a week, a month, 100 days, half a year and a
      year — each said once, quietly.
    • Reminders, as many as you like, each with its own name and time: daily, on the days you
      pick, weekly or in alternate weeks. One can be set to arrive only when the day's goal is
      still undone.
    • A backup of the lot — log, presets, goals and reminders — written to one file you keep and
      read back later, on an iPhone or an Android phone. Sittings merge rather than overwrite, so
      importing an old backup onto a phone you have kept practising on keeps both sets. The log
      can only grow.

    FREE, AND FREE SOFTWARE
    No ads, no accounts, no subscriptions, no in-app purchases and nothing to unlock. OpenBreath
    is released under the GNU General Public License v3 or later; the source is public.

**What's New** / **Release notes** (500 max on Play, 4000 on the App Store — write to Play's
limit and the same text serves both)

**This release — reminders on iPhone.** What goes in the App Store's What's New for the build
that carries them:

    Reminders. Set as many as you like, each with its own name and time: daily, on the days
    you pick, weekly or in alternate weeks. One can arrive only when the day's goal is still
    undone, so a day you have already practised stays quiet.

**The first release, kept because the Play listing still shows it:**

    First release.

    Set how long to breathe in, hold, breathe out and hold, and follow a sphere that opens
    and closes with the breath. Choose a sound for each phase, or none at all.

    Every sitting is kept on your phone: a log, goals you set yourself, streaks and
    milestones. No account, no network, no ads, nothing to unlock.

321 characters. Paste it unwrapped — Play preserves line breaks, so the hard wrapping above
would show as ragged mid-sentence breaks. A release says what changed and nothing else; the
first one had no changes to describe, so it said what the app is.

Play's are generated from the commit subjects by gradle-play-publisher and land in
`app/src/main/play/`, so this section is the App Store's copy — the one place a human writes it.
