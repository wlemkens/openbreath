# The review video

What Apple asked for under Guideline 2.1 and what a reply has to contain: a screen recording
**taken on a physical device on the latest iOS**, beginning with the app launching, walking the
typical user through the core features. A simulator recording is the one thing that cannot be
sent — it is why they ask.

**Film it off the build being submitted, never a newer one** — a video of code the reviewer does
not have invites a second round of the same letter. The first take was build 70's; the reminders
build needed its own, because two of the answers changed rather than the wording of them: the
first-run question has two switches now, and adding a reminder raises the app's only permission
prompt. Apple's letter asks for prompts to be in the recording, so a take without it answers
item 1 wrongly.

**A take exists and it worked** — made 2026-08-30 off build 120, it answered the letter and
1.0.120 was approved. It is **not** a recording of the app as it stands: reminders came after it,
so neither the first run's second switch nor the notification prompt is in it, which is what this
page was written to fix and what `review-notes.md` item 1 now declares rather than hides.

Nobody refilmed it, and 1.1 shipped anyway on 2026-09-12. So an ordinary update owes no video,
and the honest reading is that this shot list is for the next time App Review asks — or for the
release someone decides is worth filming properly.

Two notes for that take, so they are not re-derived. Step 5 changed with the one-sound-for-the-
whole-breath work and is updated below. Nothing else did: the milestone badges are earned, and
this take is made on a fresh install, so there are none to show; and the app being in three
languages changes nothing a reviewer filming in English would see.

## Before pressing record

- Install the build through TestFlight and **do not open it** until the camera is rolling. The
  first-run question appears only on a phone the app has never written to, and it is worth being
  in the video: it shows that nothing is decided for the user. If you have already opened it,
  delete the app and install it again from TestFlight. Deleting it also clears the notification
  permission, which step 6 needs to be unanswered.
- Turn the ringer on and the volume up. iOS screen recording captures app audio, and the sound
  per phase is a core feature. Leave the microphone off unless you want to narrate.
- Do Not Disturb on, so no banner lands mid-recording.
- Set the session length to its shortest before you start the take, or the video is four minutes
  of a sphere. One sitting carried to its end is what matters, not a long one.
- Start the recording on the **Home screen**, not inside the app. "The recording must begin with
  launching the app" is meant literally.

## The take

Roughly three minutes. Move deliberately and pause a beat on each screen — a reviewer is
reading, not watching.

1. **Home screen → tap the OpenBreath icon.** Let the launch play out.
2. **The "Welcome" question.** Rest on it for a second, leave **both** switches off, tap
   Continue. There are two now that iOS has reminders — a goal and an evening reminder — and
   item 4 says two. Leaving them off is the answer to "does the app do anything without being
   asked", and it also keeps the permission prompt for step 6, where it can be seen being asked
   for rather than arriving during a first run.
3. **The main screen.** Rest on it. Show the four phase timings — in, hold, out, hold — and
   change one, so it is clear they are the user's. Tap through two presets (coherence 5.5, box
   4) and watch the numbers follow.
4. **Menu (⋮) opened and closed once**, slowly enough to read: Settings, Reminders, Goals,
   Achievements, Log, Feedback, Support the app. This is the whole app in one frame.
5. **Settings.** Three things, or it becomes a tour: the cue shape and colour (change one and
   let it redraw), the **Sound** section — pick the singing bowl, so the marker sounds in the
   take — and scroll to the **Backup** section so Export and Import are seen in passing. Back
   out. Do not tap Export yet; it gets its own beat at step 10.

   Standard sets one sound for the whole breath now, so that is one chip rather than four rows.
   Tapping **Advanced** afterwards and resting a beat on the per-phase list is worth the five
   seconds: it says the simple screen is a choice and not the whole app. Then back to Standard.
6. **Reminders → add one.** The menu item, then +, then a time. Saving it raises the app's only
   permission prompt — the standard iOS notification alert — and **this is the beat item 1 is
   about**, so let it sit on screen before you tap Allow. Apple's letter asks for prompts to be
   in the recording. Then show the reminder in the list and delete it again, which makes the
   point that nothing was scheduled behind anyone's back. Reminders notify once here; the
   ring-until-dismissed switch is Android's and is not on this screen.
7. **Start.** Show the count-in running down, then a full sitting to its end: the sphere opening
   and closing, the marker at each turn, the breath count and time left, and the bowl at the
   end. **Do not cut this short.** It is the app, and everything else on this list is furniture
   around it.
8. **Log.** The sitting just finished is in it, with the pattern breathed. That the log wrote
   itself, unasked, is the point.
9. **Goals**, then **Achievements**. Both will be near-empty on a fresh install and that is
   fine — they are counted from the log, and the log is one sitting old.
10. **Settings → Backup → Export.** Let the system document picker appear, then cancel it. This
    is the only route data takes in or out of the app, and a reviewer seeing the system picker
    sees that there is no server behind it.
11. **Support the app.** Rest on it long enough to read the sentence saying the payment unlocks
    nothing. Tap a €4 button so Safari opens on paypal.me, then come straight back. Filming it
    rather than hiding it is deliberate — see the monetisation note in `review-notes.md`.
12. Back to the main screen. Stop the recording.

## What a screen recording cannot show

Two features are in the description and physically invisible to a capture: the **haptics** at
each phase change, and the **torch** brightening with the breath. Neither asks a permission and
neither has a prompt, so nothing is being hidden — but say so in the Resolution Center reply
rather than leaving a reviewer to notice the gap:

> Two optional features cannot appear in a screen recording because they are not on the screen:
> haptic feedback at each phase change, and the torch brightening and fading with the breath.
> Both are off by default and are switched on in Settings. Neither requests any permission — the
> torch is configuration on the capture device rather than capture, so no capture session is
> created and no camera prompt appears.

If a second camera is to hand, a ten-second clip of the phone's torch pulsing with the sphere
answers it better than a paragraph does.

## Sending it

Resolution Center in App Store Connect, on the rejected submission, as an attachment — and the
seven answers from `review-notes.md` in the same reply. Then paste the same seven into App
Review Information → Notes so the next submission never asks again.
