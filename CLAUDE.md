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
  of the files in `media/`, which came from the `freesound_community` Pixabay account, and
  their terms have not been confirmed. That normally means CC0 or the Pixabay Content
  License rather than MIT. Worth settling before publishing, because whichever it is may
  oblige us to ship an attribution notice in the app, and an MIT repo containing
  third-party assets under unstated terms is a mismatch waiting to be noticed.

  # Details
  ## Sounds during the phases
  During hte breathing phases, there are different sound options available.

  The base sound is waves coming ashore. During the breath in, the pitch goes up, during breathing out, the pitch goes down.

  ## 