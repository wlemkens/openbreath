package io.github.wlemkens.openbreath

import kotlin.math.roundToInt

/**
 * The breath as a torch strength, 0 meaning off.
 *
 * Shared, because both platforms have the same two problems and neither of them is the API. A
 * torch has to be written in steps or it is written sixty times a second at values no eye can
 * tell apart, which on Android hammers the camera service and on iOS means locking the capture
 * device that often. And a phone whose torch has no levels at all has to still give a breath
 * worth following.
 *
 * [maxLevel] is what the platform can actually do: Android reports it per device and only from
 * API 33, iOS takes a float that this quantises into steps of its own. One or less means the
 * torch is a switch, and the answer is then on for the inhale and off for the exhale.
 */
internal fun torchLevel(state: PhaseState, maxLevel: Int): Int =
    // a hold continues the phase before it: lit through hold-in, dark through hold-out
    if (maxLevel <= 1) {
        if (state.phase == Phase.INHALE || state.phase == Phase.HOLD_IN) 1 else 0
    } else {
        // rounding over the whole range rather than reserving level 1 for "barely lit": a
        // finished exhale has to leave you in the dark, not at the dimmest setting
        (maxLevel * state.openness.coerceIn(0f, 1f)).roundToInt()
    }
