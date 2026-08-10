# Overview
This project is an Android apllication for doing heart coherence breathing meditations.

Functionality includes:
- custom timing of the in, hold, out and hold phases.
- custom soundscapes for the different phases:
  - a sound duing each phase
  - or a sound at the end of each phase
  - the end sounds can be the users own mp3s
- optional automatic disabling of notifications during the meditation
- optional vibration during at the end of each phase
- a visual queue for breathing in and out. 
  - during the breathing in phase it is an expanding sphere
  - during the breathing out, the sphere gets smaller
- Some progress tracking, including streaks and badges
- Alarm to remind you to take the exersises. Several alarms can be configured.

  # Licensing
  The code is MIT licensed — see LICENSE.

  The bundled audio is **not** covered by that grant. `app/src/main/res/raw/*.mp3` are cuts
  of files in `media/` from the `freesound_community` Pixabay account, released under
  **CC0 1.0** (public domain dedication). CC0 requires no attribution, so nothing has to
  ship in the app, but we credit the source anyway in README and here:

  - `res/raw/session_end.mp3` — cut from `media/freesound_community-025535_singing-bowl-60767.mp3`
  - `res/raw/singing_bowl.mp3` — cut from `media/freesound_community-singing-bowl-hit-3-33366.mp3`

  `media/alex_jauk-zen-gong-199844.mp3` is by a different Pixabay uploader and is **not**
  shipped in the APK. Confirm its terms separately before using it.

  # Details
  ## Sounds during the phases
  During hte breathing phases, there are different sound options available.

  The base sound is waves coming ashore. During the breath in, the pitch goes up, during breathing out, the pitch goes down.

  # TODO
  - A support button (€4, €20, €50 euro) to Paypal?
  - Reminders : recurring reminders
    - daily, weekly, bi-weekly
    - at a certain time
    - with a configurable name
  - Goals
    - n amount of practices/breaths/time per day/week
  - A history of all the (partial) excercises done
  - Achievements
    - days in a row
    - total excercices
    - total minutes
    - minutes today
  - Feedback via a google form