package io.github.wlemkens.breath

import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos

enum class Phase(val label: String) {
    INHALE("Breathe in"),
    HOLD_IN("Hold"),
    EXHALE("Breathe out"),
    HOLD_OUT("Hold"),
}

/** Phase lengths in ms. A phase of 0 is skipped entirely. */
data class Timing(
    val inhaleMs: Int = 5500,
    val holdInMs: Int = 0,
    val exhaleMs: Int = 5500,
    val holdOutMs: Int = 0,
) {
    val cycleMs: Int = inhaleMs + holdInMs + exhaleMs + holdOutMs

    init {
        require(inhaleMs >= 0 && holdInMs >= 0 && exhaleMs >= 0 && holdOutMs >= 0) {
            "phase lengths cannot be negative"
        }
        require(cycleMs > 0) { "a breath cycle needs at least one non-zero phase" }
    }

    fun durationOf(p: Phase) = when (p) {
        Phase.INHALE -> inhaleMs
        Phase.HOLD_IN -> holdInMs
        Phase.EXHALE -> exhaleMs
        Phase.HOLD_OUT -> holdOutMs
    }
}

/** How a session ends. Both collapse to a total length via [totalMs]. */
sealed interface Limit {
    data class Cycles(val count: Int) : Limit

    data class Duration(val ms: Long) : Limit
}

/**
 * Sessions always end on a cycle boundary — being cut off mid-inhale is jarring — so a
 * duration limit rounds *up* to the next whole cycle.
 */
fun Limit.totalMs(t: Timing): Long = when (this) {
    is Limit.Cycles -> count.toLong() * t.cycleMs
    is Limit.Duration -> ceil(ms.toDouble() / t.cycleMs).toLong() * t.cycleMs
}

data class PhaseState(
    val phase: Phase,
    /** 0..1 through the current phase. */
    val progress: Float,
    /** 0-based cycle index. */
    val cycle: Int,
    /** Length of the current phase, so callers can work in real time and not just 0..1. */
    val phaseMs: Int,
) {
    /**
     * 0 = fully exhaled, 1 = fully inhaled. Drives both the sphere radius and the wave
     * filter cutoff, so the visual and the sound can never disagree. Eased with a raised
     * cosine — linear breathing looks and sounds mechanical.
     */
    val openness: Float
        get() = when (phase) {
            Phase.INHALE -> eased(progress)
            Phase.HOLD_IN -> 1f
            Phase.EXHALE -> 1f - eased(progress)
            Phase.HOLD_OUT -> 0f
        }

    /**
     * Silent at a phase boundary, 1 through the middle: a short dip that makes the end of
     * each phase audible even when the next phase plays the same sound.
     *
     * Symmetric on purpose. Fading out alone would leave the next phase starting at full
     * volume, which is a click — worse than the unclear boundary it set out to fix.
     *
     * Eased with the same raised cosine as [openness]: a linear amplitude ramp sounds abrupt
     * at both ends, because loudness is perceived closer to logarithmically than linearly.
     *
     * Both the length and the depth scale with [openness], because a boundary at peak inhale
     * has far more amplitude to travel than one at the bottom of the exhale:
     *
     *  - the ramp stretches to twice [EDGE_MS] at the top of the breath, halving its slope;
     *  - and it bottoms out at [EDGE_FLOOR_AT_PEAK] rather than silence, because a hole of
     *    silence punched into the loudest part of the soundscape is what reads as harsh.
     *
     * At the bottom of the breath the floor is 0, so that boundary still fades to true
     * silence — it already sounded right and this deliberately leaves it alone.
     *
     * Capped at a third of the phase, so at least the middle third always plays at full level.
     */
    val edge: Float
        get() {
            val ramp = minOf((EDGE_MS * (1f + openness)).toInt(), phaseMs / 3)
            if (ramp <= 0) return 1f
            val sinceStart = progress * phaseMs
            val dip = eased((minOf(sinceStart, phaseMs - sinceStart) / ramp).coerceIn(0f, 1f))
            val floor = EDGE_FLOOR_AT_PEAK * openness
            return floor + (1f - floor) * dip
        }
}

/**
 * Fade length at the quiet end of the breath; doubles towards the loud end. A tuning knob —
 * adjust it by ear.
 */
const val EDGE_MS = 500

/**
 * How far the fade ducks at the top of the breath: 0 would be full silence, 1 no dip at all.
 * A tuning knob — lower it for a starker boundary, raise it for a smoother one.
 */
const val EDGE_FLOOR_AT_PEAK = 0.3f

private fun eased(p: Float) = ((1.0 - cos(PI * p)) / 2.0).toFloat()

/**
 * Which phases end at the instant [from] gives way to [next]: [from] itself, plus every
 * zero-length phase sitting between the two.
 *
 * A phase of 0 never becomes the current phase — [phaseAt] skips it — but the user can still
 * put a marker on it, and the instant it would have occupied is a genuine boundary. With a
 * 5.5/0/5.5/0 breath, the moment the inhale ends is also the moment the skipped hold ends,
 * so a marker on that hold has to ring there or it never rings at all.
 */
fun phasesEndingBetween(from: Phase, next: Phase, t: Timing): List<Phase> {
    val order = Phase.entries
    val ended = mutableListOf(from)
    var i = (from.ordinal + 1) % order.size
    while (order[i] != next) {
        if (t.durationOf(order[i]) == 0) ended += order[i]
        i = (i + 1) % order.size
    }
    return ended
}

/**
 * Pure: elapsed time -> where in the breath we are. The single source of truth for a
 * session; the clock, the sphere and the audio thread all read this and nothing else.
 */
fun phaseAt(elapsedMs: Long, t: Timing): PhaseState {
    val clamped = elapsedMs.coerceAtLeast(0L)
    val cycle = (clamped / t.cycleMs).toInt()
    var rest = clamped % t.cycleMs
    for (phase in Phase.entries) {
        val dur = t.durationOf(phase)
        if (dur > 0 && rest < dur) return PhaseState(phase, rest.toFloat() / dur, cycle, dur)
        rest -= dur
    }
    error("unreachable: phase durations sum to cycleMs")
}
