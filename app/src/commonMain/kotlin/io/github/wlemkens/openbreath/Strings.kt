package io.github.wlemkens.openbreath

import androidx.compose.runtime.Composable
import kotlinx.datetime.isoDayNumber
import io.github.wlemkens.openbreath.media.Res
import io.github.wlemkens.openbreath.media.*
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Where a stored constant meets the word for it, and where the sentences built out of several
 * words are assembled.
 *
 * The enums keep their names and lose their labels: a name is stored and must never change, a
 * label is read and changes with the language. Keeping the two apart is the whole point of this
 * file — a `label = "Waves"` inside an enum is one rename away from being both at once.
 *
 * Nothing here is stored. Renaming a key costs a compile error; renaming an enum constant still
 * costs a practice log, exactly as the storage notes in CLAUDE.md say.
 */

internal val Phase.label: StringResource
    get() = when (this) {
        Phase.INHALE -> Res.string.phase_inhale
        Phase.HOLD_IN, Phase.HOLD_OUT -> Res.string.phase_hold
        Phase.EXHALE -> Res.string.phase_exhale
    }

/** Settings lists all four at once, where "Hold" twice says nothing. */
internal val Phase.longLabel: StringResource
    get() = when (this) {
        Phase.HOLD_IN -> Res.string.phase_hold_in
        Phase.HOLD_OUT -> Res.string.phase_hold_out
        else -> label
    }

internal val SoundMode.label: StringResource
    get() = when (this) {
        SoundMode.AMBIENT -> Res.string.sound_ambient
        SoundMode.MARKER -> Res.string.sound_marker
        SoundMode.SILENT -> Res.string.sound_silent
    }

internal val AmbientVoice.label: StringResource
    get() = when (this) {
        AmbientVoice.WAVES -> Res.string.voice_waves
        AmbientVoice.SOUNDWAVE -> Res.string.voice_soundwave
    }

internal val MarkerTone.label: StringResource
    get() = when (this) {
        MarkerTone.BELL -> Res.string.tone_bell
        MarkerTone.GONG -> Res.string.tone_gong
        MarkerTone.TICK -> Res.string.tone_tick
    }

internal val CueStyle.label: StringResource
    get() = when (this) {
        CueStyle.CLOUD -> Res.string.cue_cloud
        CueStyle.BUBBLES -> Res.string.cue_bubbles
        CueStyle.STARS -> Res.string.cue_stars
        CueStyle.GLOW -> Res.string.cue_glow
    }

internal val Repeat.label: StringResource
    get() = when (this) {
        Repeat.DAILY -> Res.string.repeat_daily
        Repeat.WEEKLY -> Res.string.repeat_weekly
        Repeat.ODD_WEEKS -> Res.string.repeat_odd_weeks
        Repeat.EVEN_WEEKS -> Res.string.repeat_even_weeks
    }

/** "sitting"/"sittings", the word alone: the sentence around it puts the number where it goes. */
internal val GoalMetric.plural: PluralStringResource
    get() = when (this) {
        GoalMetric.SITTINGS -> Res.plurals.sittings
        GoalMetric.BREATHS -> Res.plurals.breaths
        GoalMetric.MINUTES -> Res.plurals.minutes
    }

@Composable
internal fun GoalMetric.unit(count: Int): String = pluralStringResource(plural, count)

/** The chip in the goal dialog: the plural form, capitalised — "Sittings", "Séances". */
@Composable
internal fun GoalMetric.chipLabel(): String =
    pluralStringResource(plural, 2).replaceFirstChar { it.uppercase() }

internal val GoalPeriod.label: StringResource
    get() = if (this == GoalPeriod.DAY) Res.string.goal_each_day else Res.string.goal_each_week

internal val GoalPeriod.heading: StringResource
    get() = if (this == GoalPeriod.DAY) Res.string.goal_today else Res.string.goal_this_week

internal val GoalPeriod.each: StringResource
    get() = if (this == GoalPeriod.DAY) Res.string.goal_per_day else Res.string.goal_per_week

/** "5 days in a row", the length of a streak said in this period's own unit. */
@Composable
internal fun GoalPeriod.run(count: Int): String = pluralStringResource(
    if (this == GoalPeriod.DAY) Res.plurals.streak_days else Res.plurals.streak_weeks,
    count,
    count,
)

/** "Today: 7 of 10 minutes" — what you have done, against what you said you would. */
@Composable
internal fun Goal.headline(done: Int): String =
    stringResource(Res.string.goal_headline, stringResource(period.heading), done, target, metric.unit(target))

/** "1 sitting a day", the goal said plainly. */
@Composable
internal fun Goal.description(): String =
    stringResource(Res.string.goal_description, target, metric.unit(target), stringResource(period.each))

@Composable
internal fun milestoneLabel(days: Int): String = when {
    days == 7 -> stringResource(Res.string.milestone_week)
    days == 30 -> stringResource(Res.string.milestone_month)
    days == 182 -> stringResource(Res.string.milestone_half_year)
    days == 365 -> stringResource(Res.string.milestone_year)
    days > 365 && days % 365 == 0 ->
        pluralStringResource(Res.plurals.milestone_years_label, days / 365, days / 365)

    else -> pluralStringResource(Res.plurals.milestone_days, days, days)
}

/**
 * Says what was done and what it means. Written per length rather than from a template: a
 * hundred days is not three days with a bigger number in it, and a line that fits both fits
 * neither well.
 */
@Composable
internal fun milestoneMessage(days: Int): String = when {
    days <= 3 -> stringResource(Res.string.milestone_3)
    days <= 7 -> stringResource(Res.string.milestone_7)
    days <= 30 -> stringResource(Res.string.milestone_30)
    days <= 100 -> stringResource(Res.string.milestone_100)
    days <= 182 -> stringResource(Res.string.milestone_182)
    days <= 365 -> stringResource(Res.string.milestone_365)
    days <= 500 -> stringResource(Res.string.milestone_500)
    else -> stringResource(Res.string.milestone_years, days / 365)
}

/** "Weekly Mon, Thu · 8:00 AM", in the reader's own conventions throughout. */
@Composable
internal fun Reminder.summary(formats: Formats): String = stringResource(
    Res.string.reminder_summary,
    stringResource(repeat.label),
    if (repeat == Repeat.DAILY) {
        ""
    } else {
        " " + weekDays().filter { it.isoDayNumber in days }
            .joinToString(", ") { formats.shortDayName(it) }
    },
    formats.clockTime(hour, minute),
)

/**
 * What is in a backup, for the dialog that asks whether to import it and for the line that says
 * it was written. Suspend rather than composable because only one of those two is a composition —
 * `produceState` bridges the other way round, where a second copy of the sentence would drift.
 */
internal suspend fun Backup.summary(): String = getString(
    Res.string.backup_summary,
    count(history.size, Res.plurals.sittings),
    count(config.presets.size, Res.plurals.presets),
    count(goals.size, Res.plurals.goals),
    count(reminders.size, Res.plurals.reminders),
)

private suspend fun count(n: Int, word: PluralStringResource) = "$n ${getPluralString(word, n)}"
