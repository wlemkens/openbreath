package io.github.wlemkens.openbreath

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Instant

/**
 * Everything the app stores, and the pure logic over it. Split out of Prefs.kt for the iOS
 * port: this half names no platform at all, so it compiles for both. What stayed behind in
 * Prefs.kt is the DataStore plumbing and the goal arithmetic that still wants `java.time`.
 *
 * The stored shape is the thing this file is really about. Read the storage section of
 * CLAUDE.md before renaming a constant or moving a field — both have silently eaten a
 * practice log before.
 */

enum class SoundMode(val label: String) {
    /** A sound for the whole phase, riding the breath. Which one is [PhaseSound.voice]. */
    AMBIENT("Ambient"),

    /** One sound at the moment the phase ends. Which one is [PhaseSound.tone]. */
    MARKER("Marker"),
    SILENT("Silent"),
}

/** The continuous sounds. A group under [SoundMode.AMBIENT], as the tones are under MARKER. */
enum class AmbientVoice(val label: String) {
    WAVES("Waves"),
    SOUNDWAVE("Soundwave"),
}

/** The built-in marker sounds, used when the user hasn't pointed a phase at their own mp3. */
enum class MarkerTone(val label: String) {
    BELL("Bell"),

    /** Pitched by phase — see [markerHz]. */
    GONG("Gong"),

    /** A metronome's click, pitched by phase like the gong. */
    TICK("Tick"),
}

/** What the breath cue on the session screen looks like. */
/**
 * Three of these draw the same turning lattice of points and differ only in how one point is
 * drawn — see `PointLook`. The names are stored, so they are added to rather than renamed.
 */
enum class CueStyle(val label: String) {
    /** Points packed into a core at the bottom of the breath, opening into a sphere at the top. */
    CLOUD("Cloud"),

    /** The same cloud, each point a white centre in a tight glow. */
    BUBBLES("Bubbles"),

    /** The same again, the centre harder and the glow wider and fainter around it. */
    STARS("Stars"),

    /** The plain gradient sphere. */
    GLOW("Glow"),
}

@Serializable
data class PhaseSound(
    val mode: SoundMode = SoundMode.AMBIENT,
    val voice: AmbientVoice = AmbientVoice.WAVES,
    /** content:// URI of the user's own mp3. Null falls back to a synthesized [tone]. */
    val markerUri: String? = null,
    val tone: MarkerTone = MarkerTone.BELL,
)

@Serializable
data class Preset(
    val name: String = "Coherence 5.5",
    val inhaleMs: Int = 5500,
    val holdInMs: Int = 0,
    val exhaleMs: Int = 5500,
    val holdOutMs: Int = 0,
    val inhaleSound: PhaseSound = PhaseSound(),
    val holdInSound: PhaseSound = PhaseSound(),
    val exhaleSound: PhaseSound = PhaseSound(),
    val holdOutSound: PhaseSound = PhaseSound(),
) {
    fun soundOf(p: Phase) = when (p) {
        Phase.INHALE -> inhaleSound
        Phase.HOLD_IN -> holdInSound
        Phase.EXHALE -> exhaleSound
        Phase.HOLD_OUT -> holdOutSound
    }

    fun withSound(p: Phase, f: (PhaseSound) -> PhaseSound) = when (p) {
        Phase.INHALE -> copy(inhaleSound = f(inhaleSound))
        Phase.HOLD_IN -> copy(holdInSound = f(holdInSound))
        Phase.EXHALE -> copy(exhaleSound = f(exhaleSound))
        Phase.HOLD_OUT -> copy(holdOutSound = f(holdOutSound))
    }

    fun withDuration(p: Phase, ms: Int) = when (p) {
        Phase.INHALE -> copy(inhaleMs = ms)
        Phase.HOLD_IN -> copy(holdInMs = ms)
        Phase.EXHALE -> copy(exhaleMs = ms)
        Phase.HOLD_OUT -> copy(holdOutMs = ms)
    }

    /** Falls back to the default rather than throwing on a stored preset that adds up to zero. */
    val timing: Timing
        get() = runCatching { Timing(inhaleMs, holdInMs, exhaleMs, holdOutMs) }.getOrElse { Timing() }
}

@Serializable
data class Config(
    val presets: List<Preset> = DEFAULT_PRESETS,
    val activeIndex: Int = 0,
    val limitIsCycles: Boolean = false,
    val durationMs: Long = 5 * 60_000L,
    val cycles: Int = 20,
    val vibrate: Boolean = false,
    /** The torch brightens with the inhale and fades with the exhale. */
    val flashlight: Boolean = false,
    val muteNotifications: Boolean = false,
    /**
     * A bowl at the end of the session. On by default: the whole point of a timed meditation
     * is not having to check whether it is over, and with your eyes shut a silent ending
     * leaves you sitting there wondering.
     */
    val endSound: Boolean = true,
    /** The three progress readouts, each independently hideable for a barer screen. */
    val showTime: Boolean = true,
    val showDots: Boolean = true,
    val showBreaths: Boolean = true,
    val cue: CueStyle = CueStyle.CLOUD,
    /** Opaque ARGB. The bright end of the cue, and the colour of the breath dots. */
    val cueColor: Int = DEFAULT_CUE_COLOR,
    /** Draw the cue past what the picker can express — see `Color.vivid`. */
    val vividCue: Boolean = false,
    /** Which half of the settings screen was last open, so it opens there again. */
    val advancedSettings: Boolean = false,
) {
    val active: Preset get() = presets[activeIndex]

    val limit: Limit
        get() = if (limitIsCycles) Limit.Cycles(cycles) else Limit.Duration(durationMs)

    /** Keeps [active] and [activeIndex] safe to use unguarded everywhere else. */
    fun sane(): Config {
        // a stored index means nothing once the list it referred to is gone
        if (presets.isEmpty()) return copy(presets = DEFAULT_PRESETS, activeIndex = 0)
        return copy(activeIndex = activeIndex.coerceIn(0, presets.size - 1))
    }
}

/** The teal the app shipped with. */
const val DEFAULT_CUE_COLOR: Int = 0xFF5FD6C8.toInt()

internal val DEFAULT_PRESETS = listOf(
    Preset("Coherence 5.5", 5500, 0, 5500, 0),
    Preset("4-7-8", 4000, 7000, 8000, 0),
    Preset("Box 4", 4000, 4000, 4000, 4000),
)

// coerceInputValues so that retiring an enum constant costs that one field its default rather than
// failing the whole decode — the fallback below is all-or-nothing, and losing every saved preset
// over a renamed cue style is not a trade worth making
internal val json = Json {
    ignoreUnknownKeys = true; encodeDefaults = true; coerceInputValues = true
}

/**
 * One number to one decimal place, in whatever the platform calls a decimal separator. An
 * expect because `String.format` is the JVM's alone; a hand-rolled "divide and insert a dot"
 * would quietly write 5.5 to a reader whose keyboard, clock and every other app say 5,5.
 */
expect fun formatOneDecimal(value: Float): String

/**
 * One sitting, however much of it was actually breathed. The timing is copied in rather than
 * referenced: a preset that is renamed or re-timed later must not rewrite what was breathed.
 * The four phases default to 0 for entries logged before they were recorded — see [pattern].
 */
@Serializable
data class Entry(
    /** Epoch ms at which it was started. Doubles as its identity — see [logSession]. */
    val at: Long,
    val durationMs: Long,
    val preset: String,
    val inhaleMs: Int = 0,
    val holdInMs: Int = 0,
    val exhaleMs: Int = 0,
    val holdOutMs: Int = 0,
    /** Whole breaths finished. The tail of a part-breathed cycle is in [durationMs] only. */
    val cycles: Int = 0,
) {
    /** "5.5 – 5.5" or "4 – 7 – 8", in seconds. Empty for an entry logged before timings were. */
    val pattern: String
        get() = listOf(inhaleMs, holdInMs, exhaleMs, holdOutMs)
            // a zero-length phase is one the breath never had, not one that took no time
            .filter { it > 0 }
            // whole seconds written whole; a decimal separator is the locale's business, so
            // trimming a trailing ".0" off the formatted string is not the way to do it
            .joinToString(" – ") { if (it % 1000 == 0) "${it / 1000}" else formatOneDecimal(it / 1000f) }
}

/** What to log for a sitting of [durationMs] on [preset], started at [at]. */
internal fun entryFor(at: Long, durationMs: Long, preset: Preset) = Entry(
    at = at,
    durationMs = durationMs,
    preset = preset.name,
    inhaleMs = preset.inhaleMs,
    holdInMs = preset.holdInMs,
    exhaleMs = preset.exhaleMs,
    holdOutMs = preset.holdOutMs,
    cycles = (durationMs / preset.timing.cycleMs).toInt(),
)

/** Shorter than this was a mistap, not a meditation. */
const val LOGGED_MIN_MS = 20_000L

// ponytail: one JSON blob rewritten per sitting, capped. Years of daily practice fit; move to a
// row store if it ever has to hold tens of thousands.
internal const val HISTORY_MAX = 2000

internal fun decodeHistory(stored: String?): List<Entry> =
    stored?.let { runCatching { json.decodeFromString<List<Entry>>(it) }.getOrNull() } ?: emptyList()

/**
 * The log with [entry] recorded, or unchanged if the sitting was too short to have been one.
 * Keyed on the start time, so pausing, resuming and finishing a single sitting keeps rewriting
 * one entry rather than logging three.
 */
internal fun List<Entry>.logging(entry: Entry): List<Entry> =
    if (entry.durationMs < LOGGED_MIN_MS) this
    else (filterNot { it.at == entry.at } + entry).takeLast(HISTORY_MAX)

/** What a [Goal] counts. The range is the metric's own: ten sittings and ten breaths differ. */
enum class GoalMetric(val label: String, val singular: String, val min: Int, val max: Int, val step: Int) {
    SITTINGS("sittings", "sitting", 1, 10, 1),
    BREATHS("breaths", "breath", 10, 300, 10),
    MINUTES("minutes", "minute", 5, 120, 5),
}

fun GoalMetric.unit(count: Int) = if (count == 1) singular else label

enum class GoalPeriod(val label: String, val heading: String, val each: String, val unit: String) {
    DAY("Each day", "Today", "a day", "day"),
    WEEK("Each week", "This week", "a week", "week"),
}

/** "5 days in a row", the length of a streak said in this period's own unit. */
fun GoalPeriod.run(count: Int) = "$count ${if (count == 1) unit else "${unit}s"} in a row"

/**
 * One thing to aim at, e.g. one sitting a day or a hundred breaths a week. Several can be kept
 * at once, and they are counted from the log rather than tallied as you go — so setting one up
 * credits the practice you had already done, and changing your mind about it costs nothing.
 */
@Serializable
data class Goal(
    val id: Int,
    val metric: GoalMetric = GoalMetric.MINUTES,
    val period: GoalPeriod = GoalPeriod.DAY,
    val target: Int = 10,
) {
    /** "Today: 7 of 10 minutes" — what you have done, against what you said you would. */
    fun headline(done: Int) = "${period.heading}: $done of $target ${metric.unit(target)}"

    /** "1 sitting a day", the goal said plainly. */
    val description: String get() = "$target ${metric.unit(target)} ${period.each}"

    fun reached(done: Int) = done >= target

    /** A target stored against one metric is nonsense against another. */
    fun sane() = copy(target = target.coerceIn(metric.min, metric.max))
}

/** What these sittings add up to in [metric]'s own unit. */
internal fun List<Entry>.tally(metric: GoalMetric): Int = when (metric) {
    GoalMetric.SITTINGS -> size
    // a sitting logged before breaths were counted contributes none, which is honest
    GoalMetric.BREATHS -> sumOf { it.cycles }
    GoalMetric.MINUTES -> (sumOf { it.durationMs } / 60_000L).toInt()
}

/** What the sittings since [since] add up to, in [goal]'s own unit. */
internal fun List<Entry>.towards(goal: Goal, since: Long): Int =
    filter { it.at >= since }.tally(goal.metric)

/**
 * Which day this reader's own calendar starts a week on. The one piece of `java.time` that
 * kotlinx-datetime deliberately does not carry: it holds no locale data at all, so a week
 * boundary has to be asked of the platform. Android reads it from the locale, iOS from
 * NSCalendar — and both are user-settable independently of language, which is the point.
 */
expect fun firstDayOfWeek(): DayOfWeek

/**
 * The date the period containing [date] began. A week starts where the reader's own calendar
 * starts it, the same as the day chips on a reminder.
 */
internal fun periodStartDate(period: GoalPeriod, date: LocalDate): LocalDate = when (period) {
    GoalPeriod.DAY -> date
    // previousOrSame(firstDayOfWeek), spelled out: how far back the week boundary is, which is
    // zero when today already is it
    GoalPeriod.WEEK ->
        date.minus((date.dayOfWeek.isoDayNumber - firstDayOfWeek().isoDayNumber + 7) % 7, DateTimeUnit.DAY)
}

/** The instant the current goal period began. */
internal fun periodStartMs(period: GoalPeriod, now: Instant, zone: TimeZone = TimeZone.currentSystemDefault()): Long =
    periodStartDate(period, now.toLocalDateTime(zone).date).atStartOfDayIn(zone).toEpochMilliseconds()

/**
 * How many periods in a row [goal] has been reached, counting back from now. The period in
 * progress cannot break a streak — a day you have not finished yet is not a day you failed —
 * so a run stands until a whole day or week goes by without reaching it.
 */
internal fun List<Entry>.streak(
    goal: Goal,
    now: Instant,
    zone: TimeZone = TimeZone.currentSystemDefault(),
): Int {
    val reached = groupBy {
        periodStartDate(goal.period, Instant.fromEpochMilliseconds(it.at).toLocalDateTime(zone).date)
    }.filterValues { it.tally(goal.metric) >= goal.target }.keys
    val step = if (goal.period == GoalPeriod.DAY) DateTimeUnit.DAY else DateTimeUnit.WEEK

    var at = periodStartDate(goal.period, now.toLocalDateTime(zone).date)
    if (at !in reached) at = at.minus(1, step)
    var run = 0
    while (at in reached) {
        run++
        at = at.minus(1, step)
    }
    return run
}

/**
 * Whether every goal stands reached as things are now. Having no goals at all is not "done" —
 * a reminder that skips itself because nothing was ever asked for would simply never arrive.
 */
internal fun List<Goal>.allReached(
    history: List<Entry>,
    now: Instant,
    zone: TimeZone = TimeZone.currentSystemDefault(),
): Boolean =
    isNotEmpty() && all { it.reached(history.towards(it, periodStartMs(it.period, now, zone))) }

/**
 * Days in a row on which everything asked for that day was done. Weekly goals are left out:
 * one is unmet for most of its own week, so counting it would make a daily streak impossible.
 * With no daily goals set the bar is simply having practised, which is the figure the
 * achievements screen already calls "days in a row".
 */
internal fun List<Entry>.allReachedStreak(
    goals: List<Goal>,
    now: Instant,
    zone: TimeZone = TimeZone.currentSystemDefault(),
): Int {
    val daily = goals.filter { it.period == GoalPeriod.DAY }.ifEmpty { listOf(EVERY_DAY) }
    val byDay = groupBy { Instant.fromEpochMilliseconds(it.at).toLocalDateTime(zone).date }
    fun met(day: LocalDate) =
        byDay[day].orEmpty().let { entries -> daily.all { entries.tally(it.metric) >= it.target } }

    // today counts if it is already done, and cannot break the run if it is not: the day is
    // not over yet
    var at = now.toLocalDateTime(zone).date
    if (!met(at)) at = at.minus(1, DateTimeUnit.DAY)
    var run = 0
    while (met(at)) {
        run++
        at = at.minus(1, DateTimeUnit.DAY)
    }
    return run
}

/** The lengths worth marking. Past the last of them, every anniversary. */
private val MILESTONE_DAYS = listOf(3, 7, 30, 100, 182, 365, 500)

internal fun isMilestone(days: Int) = days in MILESTONE_DAYS || (days > 500 && days % 365 == 0)

/**
 * The milestone to celebrate for a [days]-long run, given the last one already celebrated —
 * the longest one reached rather than one landed on exactly, so a streak that passes a
 * milestone while the app is closed still gets its moment when it is next opened.
 */
internal fun dueMilestone(days: Int, celebrated: Int): Int? =
    (celebrated + 1..days).lastOrNull { isMilestone(it) }

internal fun milestoneLabel(days: Int): String = when {
    days == 7 -> "A week"
    days == 30 -> "A month"
    days == 182 -> "Half a year"
    days == 365 -> "A year"
    days > 365 && days % 365 == 0 -> "${days / 365} years"
    else -> "$days days"
}

/**
 * Says what was done and what it means. Written per length rather than from a template: a
 * hundred days is not three days with a bigger number in it, and a line that fits both fits
 * neither well.
 */
internal fun milestoneMessage(days: Int): String = when {
    days <= 3 -> "Three days in a row, everything you set yourself done. This is how a practice begins."
    days <= 7 -> "A week of it, every goal met. It is starting to stick."
    days <= 30 -> "A month without missing what you asked of yourself. That is not chance any more."
    days <= 100 -> "A hundred days. Most things do not last that long. This one has."
    days <= 182 -> "Half a year of keeping your word to yourself, one breath at a time."
    days <= 365 -> "A year. Every day, everything you set yourself, done."
    days <= 500 -> "Five hundred days. There is little left to say."
    else -> "${days / 365} years of it, unbroken."
}

/** Practising at all, once a day, is a streak worth keeping whether or not a goal says so. */
internal val EVERY_DAY = Goal(id = 0, metric = GoalMetric.SITTINGS, period = GoalPeriod.DAY, target = 1)

/**
 * How often a [Reminder] comes back. The fortnightly pair go by the ISO week number rather than
 * counting from the day you set them, so "every odd week" means the same weeks to you as it does
 * to a wall planner.
 */
enum class Repeat(val label: String) {
    DAILY("Daily"),
    WEEKLY("Weekly"),
    ODD_WEEKS("Every odd week"),
    EVEN_WEEKS("Every even week"),
}

@Serializable
data class Reminder(
    /** Stable for the life of the reminder: it is also the alarm's request code. */
    val id: Int,
    val name: String = "Breathe",
    val hour: Int = 8,
    val minute: Int = 0,
    val repeat: Repeat = Repeat.DAILY,
    /** ISO days of the week, 1 = Monday. More than one is allowed. Ignored by [Repeat.DAILY]. */
    val days: Set<Int> = setOf(1),
    /** Rings the alarm tone over and over until dismissed, rather than sounding once. */
    val alarm: Boolean = false,
    /** Stays quiet when every goal is already reached — see [allReached]. */
    val onlyIfBehind: Boolean = false,
    val enabled: Boolean = true,
)

internal fun decodeGoals(stored: String?): List<Goal> =
    stored?.let { runCatching { json.decodeFromString<List<Goal>>(it) }.getOrNull() } ?: emptyList()

internal fun decodeReminders(stored: String?): List<Reminder> =
    stored?.let { runCatching { json.decodeFromString<List<Reminder>>(it) }.getOrNull() } ?: emptyList()
