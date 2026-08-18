package io.github.wlemkens.openbreath

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
     * Every boundary is treated alike — the same [EDGE_RAMP_MS] and the same
     * [EDGE_FLOOR_AT_PEAK] floor, whatever the breath is doing. The dip used to shrink toward
     * the bottom of the exhale, on the reasoning that a quiet boundary has less amplitude to
     * travel; in the ear that came out as a gap, because it reached true silence at the one
     * turn the breath should carry through without pause.
     *
     * Capped at a third of the phase, so at least the middle third always plays at full level.
     */
    val edge: Float
        get() {
            val ramp = minOf(EDGE_RAMP_MS, phaseMs / 3)
            if (ramp <= 0) return 1f
            val sinceStart = progress * phaseMs
            val dip = eased((minOf(sinceStart, phaseMs - sinceStart) / ramp).coerceIn(0f, 1f))
            return EDGE_FLOOR_AT_PEAK + (1f - EDGE_FLOOR_AT_PEAK) * dip
        }
}

/** Fade length at every phase boundary. A tuning knob — adjust it by ear. */
const val EDGE_RAMP_MS = 1000

/**
 * How far the fade ducks: 0 would be full silence, 1 no dip at all. A tuning knob — lower it
 * for a starker boundary, raise it for a smoother one. Never 0: the breath should turn without
 * the soundscape ever going away.
 */
const val EDGE_FLOOR_AT_PEAK = 0.3f

/** Raised cosine: 0 at 0, 1 at 1, flat at both ends. The app's one easing curve. */
internal fun eased(p: Float) = ((1.0 - cos(PI * p)) / 2.0).toFloat()

/**
 * Which phases begin at the instant [from] gives way to [next]: [next] itself, plus every
 * zero-length phase sitting between the two.
 *
 * A marker announces the phase you are about to breathe, so it belongs to the phase that starts
 * rather than the one that finishes. A zero-length phase begins and ends on the same instant, and
 * it is included: the user may have put a marker on it, and that instant is the only chance it
 * will ever get to ring.
 */
fun phasesStartingBetween(from: Phase, next: Phase, t: Timing): List<Phase> {
    val order = Phase.entries
    val starting = mutableListOf<Phase>()
    var i = (from.ordinal + 1) % order.size
    while (order[i] != next) {
        if (t.durationOf(order[i]) == 0) starting += order[i]
        i = (i + 1) % order.size
    }
    starting += next
    return starting
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
