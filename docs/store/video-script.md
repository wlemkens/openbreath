# The review video

What Apple asked for under Guideline 2.1 and what a reply has to contain: a screen recording
**taken on a physical device on the latest iOS**, beginning with the app launching, walking the
typical user through the core features. A simulator recording is the one thing that cannot be
sent — it is why they ask.

Filmed off TestFlight, build 70, which is the binary under review. Not off a newer build: a
video of code the reviewer does not have invites a second round of the same letter.

## Before pressing record

- Install build 70 through TestFlight and **do not open it** until the camera is rolling. The
  first-run question appears only on a phone the app has never written to, and it is worth being
  in the video: it shows that nothing is decided for the user. If you have already opened it,
  delete the app and install it again from TestFlight.
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
2. **The "Welcome" question.** Rest on it for a second, leave the switch off, tap Continue. On
   iOS there is one switch, not the two Android shows — the reminder offer is Android's alone,
   and `review-notes.md` says one because that is what a reviewer sees here. This is the answer
   to "does the app do anything without being asked" and it is worth two seconds.
3. **The main screen.** Rest on it. Show the four phase timings — in, hold, out, hold — and
   change one, so it is clear they are the user's. Tap through two presets (coherence 5.5, box
   4) and watch the numbers follow.
4. **Menu (⋮) opened and closed once**, slowly enough to read: Settings, Goals, Achievements,
   Log, Feedback, Support the app. This is the whole app in one frame.
5. **Settings.** Three things, or it becomes a tour: the cue shape and colour (change one and
   let it redraw), the sound for a phase — pick the singing bowl, so the marker sounds in the
   take — and scroll to the **Backup** section so Export and Import are seen in passing. Back
   out. Do not tap Export yet; it gets its own beat at step 9.
6. **Start.** Show the count-in running down, then a full sitting to its end: the sphere opening
   and closing, the marker at each turn, the breath count and time left, and the bowl at the
   end. **Do not cut this short.** It is the app, and everything else on this list is furniture
   around it.
7. **Log.** The sitting just finished is in it, with the pattern breathed. That the log wrote
   itself, unasked, is the point.
8. **Goals**, then **Achievements**. Both will be near-empty on a fresh install and that is
   fine — they are counted from the log, and the log is one sitting old.
9. **Settings → Backup → Export.** Let the system document picker appear, then cancel it. This
   is the only route data takes in or out of the app, and a reviewer seeing the system picker
   sees that there is no server behind it.
10. **Support the app.** Rest on it long enough to read the sentence saying the payment unlocks
    nothing. Tap a €4 button so Safari opens on paypal.me, then come straight back. Filming it
    rather than hiding it is deliberate — see the monetisation note in `review-notes.md`.
11. Back to the main screen. Stop the recording.

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
