package io.github.wlemkens.openbreath

/**
 * How high each end-of-phase marker rings. Pure arithmetic over [Phase] and [MarkerTone], which
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
 * Low at the top of the breath and high at the bottom, because a marker announces the
 * direction the breath is about to take rather than where it is: the end of the in-hold leads
 * into an exhale (downward), the end of the out-hold into an inhale (upward). The inhale
 * shares the low pitch, so a zero-length hold between them collapses to a single strike.
 */
private const val GONG_LOW_RATE = 0.6f

fun gongRate(phase: Phase): Float = when (phase) {
    Phase.INHALE, Phase.HOLD_IN -> GONG_LOW_RATE
    Phase.EXHALE, Phase.HOLD_OUT -> 1f
}

/**
 * The tick that starts an inhale rings a fifth above the one that starts an exhale, so a breath
 * reads as a downbeat and an offbeat rather than as a row of identical clicks.
 *
 * Grouped exactly as [gongRate] is, and for the same reason: two phases ending on one instant —
 * which is what a zero-length hold means — have to agree on a pitch, or they sound twice.
 */
fun tickRate(phase: Phase): Float = when (phase) {
    Phase.INHALE, Phase.HOLD_IN -> 1f
    Phase.EXHALE, Phase.HOLD_OUT -> 1.5f
}

/** The bell has one pitch wherever it rings; the other two follow the breath. */
fun markerRate(tone: MarkerTone, phase: Phase): Float = when (tone) {
    MarkerTone.GONG -> gongRate(phase)
    MarkerTone.TICK -> tickRate(phase)
    MarkerTone.BELL -> 1f
}
