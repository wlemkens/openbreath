package io.github.wlemkens.openbreath

/**
 * How high each marker rings when its phase begins. Pure arithmetic over [Phase] and [MarkerTone], which
 * is why it is here and not beside the AudioTrack and SoundPool that play the result: a rate is
 * a rate on either platform, and the rule that a zero-length hold must not sound twice is part
 * of the app rather than part of Android.
 *
 * The playback that consumes these stays platform-side — androidMain/Audio.kt today, an
 * AVAudioEngine beside it later.
 */

/**
 * The recorded bowl is the high tone as struck. The low tone is the same bowl played slower,
 * which drops its pitch — additive synthesis of a gong came out flat by comparison, so this
 * is a real recording rather than a model of one.
 *
 * A marker announces the direction the breath is about to take, and it rings as that phase
 * begins: high going up, low going down. So an inhale opens high and an exhale opens low.
 *
 * The pairing is what makes a zero-length hold work. A hold shares the pitch of the phase it
 * runs into — the in-hold with the exhale that follows it, the out-hold with the inhale — so
 * when a hold is set to 0 and both begin on the same instant, the two collapse to a single
 * strike rather than sounding twice at different pitches.
 */
private const val GONG_LOW_RATE = 0.6f

fun gongRate(phase: Phase): Float = when (phase) {
    Phase.HOLD_OUT, Phase.INHALE -> 1f
    Phase.HOLD_IN, Phase.EXHALE -> GONG_LOW_RATE
}

/**
 * The tick that starts an inhale rings a fifth above the one that starts an exhale, so a breath
 * reads as a downbeat and an offbeat rather than as a row of identical clicks.
 *
 * Grouped exactly as [gongRate] is, and for the same reason: two phases beginning on one instant
 * — which is what a zero-length hold means — have to agree on a pitch, or they sound twice.
 */
fun tickRate(phase: Phase): Float = when (phase) {
    Phase.HOLD_OUT, Phase.INHALE -> 1.5f
    Phase.HOLD_IN, Phase.EXHALE -> 1f
}

/** The bell has one pitch wherever it rings; the other two follow the breath. */
fun markerRate(tone: MarkerTone, phase: Phase): Float = when (tone) {
    MarkerTone.GONG -> gongRate(phase)
    MarkerTone.TICK -> tickRate(phase)
    MarkerTone.BELL -> 1f
}
