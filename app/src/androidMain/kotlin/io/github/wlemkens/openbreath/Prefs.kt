package io.github.wlemkens.openbreath

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale

/**
 * Where the stored shape actually meets the disk. The shape itself — every enum, every
 * `@Serializable` class, the Json instance and the decoders — lives in commonMain/Model.kt so
 * that iOS can read the same bytes. What is left here is the two things that still name a
 * platform: DataStore, and the goal arithmetic that runs on `java.time`.
 *
 * The `java.time` half is the port's remaining debt. It needs kotlinx-datetime before goals,
 * streaks or milestones can be shown on iOS, and it is the part to move most carefully — a
 * streak that miscounts is a year of practice reported wrong.
 */

actual fun formatOneDecimal(value: Float): String = "%.1f".format(value)

private val Context.store by preferencesDataStore("breath")
private val CONFIG = stringPreferencesKey("config")

/** A config written by an older build must not crash the app — fall back to defaults. */
fun Context.configFlow(): Flow<Config> = store.data.map { prefs ->
    val stored = prefs[CONFIG]?.let { runCatching { json.decodeFromString<Config>(it) }.getOrNull() }
    (stored ?: Config()).sane()
}

suspend fun Context.saveConfig(config: Config) {
    val encoded = json.encodeToString(config.sane())
    store.edit { it[CONFIG] = encoded }
}

private val HISTORY = stringPreferencesKey("history")

fun Context.historyFlow(): Flow<List<Entry>> = store.data.map { decodeHistory(it[HISTORY]) }

/** Records what was breathed. Every stop calls this, including the ones that logged nothing. */
suspend fun Context.logSession(at: Long, durationMs: Long, preset: Preset) {
    store.edit { prefs ->
        val old = decodeHistory(prefs[HISTORY])
        val new = old.logging(entryFor(at, durationMs, preset))
        // logging returns the list it was given when there was nothing to record; not rewriting
        // the store then keeps a tap on Start and Pause from churning it
        if (new !== old) prefs[HISTORY] = json.encodeToString(new)
    }
}

/**
 * The date the period containing [date] began. A week starts where the reader's own calendar
 * starts it, the same as the day chips on a reminder.
 */
internal fun periodStartDate(period: GoalPeriod, date: LocalDate): LocalDate = when (period) {
    GoalPeriod.DAY -> date
    GoalPeriod.WEEK ->
        date.with(TemporalAdjusters.previousOrSame(WeekFields.of(Locale.getDefault()).firstDayOfWeek))
}

/** The instant the current goal period began. */
internal fun periodStartMs(period: GoalPeriod, now: ZonedDateTime): Long =
    periodStartDate(period, now.toLocalDate()).atStartOfDay(now.zone).toInstant().toEpochMilli()

/**
 * How many periods in a row [goal] has been reached, counting back from now. The period in
 * progress cannot break a streak — a day you have not finished yet is not a day you failed —
 * so a run stands until a whole day or week goes by without reaching it.
 */
internal fun List<Entry>.streak(goal: Goal, now: ZonedDateTime): Int {
    val reached = groupBy {
        periodStartDate(goal.period, Instant.ofEpochMilli(it.at).atZone(now.zone).toLocalDate())
    }.filterValues { it.tally(goal.metric) >= goal.target }.keys
    val step = if (goal.period == GoalPeriod.DAY) Period.ofDays(1) else Period.ofWeeks(1)

    var at = periodStartDate(goal.period, now.toLocalDate())
    if (at !in reached) at = at.minus(step)
    var run = 0
    while (at in reached) {
        run++
        at = at.minus(step)
    }
    return run
}

/**
 * Whether every goal stands reached as things are now. Having no goals at all is not "done" —
 * a reminder that skips itself because nothing was ever asked for would simply never arrive.
 */
internal fun List<Goal>.allReached(history: List<Entry>, now: ZonedDateTime): Boolean =
    isNotEmpty() && all { it.reached(history.towards(it, periodStartMs(it.period, now))) }

/**
 * Days in a row on which everything asked for that day was done. Weekly goals are left out:
 * one is unmet for most of its own week, so counting it would make a daily streak impossible.
 * With no daily goals set the bar is simply having practised, which is the figure the
 * achievements screen already calls "days in a row".
 */
internal fun List<Entry>.allReachedStreak(goals: List<Goal>, now: ZonedDateTime): Int {
    val daily = goals.filter { it.period == GoalPeriod.DAY }.ifEmpty { listOf(EVERY_DAY) }
    val byDay = groupBy { Instant.ofEpochMilli(it.at).atZone(now.zone).toLocalDate() }
    fun met(day: LocalDate) =
        byDay[day].orEmpty().let { entries -> daily.all { entries.tally(it.metric) >= it.target } }

    // today counts if it is already done, and cannot break the run if it is not: the day is
    // not over yet
    var at = now.toLocalDate()
    if (!met(at)) at = at.minusDays(1)
    var run = 0
    while (met(at)) {
        run++
        at = at.minusDays(1)
    }
    return run
}

// its own key rather than a field of Config: goals and settings are edited on different screens,
// and a whole-Config write from one would roll back what the other had just saved
private val GOALS = stringPreferencesKey("goals")

fun Context.goalsFlow(): Flow<List<Goal>> = store.data.map { decodeGoals(it[GOALS]).map { g -> g.sane() } }

suspend fun Context.saveGoals(goals: List<Goal>) {
    val encoded = json.encodeToString(goals.map { it.sane() })
    store.edit { it[GOALS] = encoded }
}

/** The longest milestone already shown, so a run is celebrated once rather than every launch. */
private val CELEBRATED = intPreferencesKey("celebrated")

fun Context.celebratedFlow(): Flow<Int> = store.data.map { it[CELEBRATED] ?: 0 }

suspend fun Context.saveCelebrated(days: Int) {
    store.edit { it[CELEBRATED] = days }
}

private val REMINDERS = stringPreferencesKey("reminders")

fun Context.remindersFlow(): Flow<List<Reminder>> = store.data.map { decodeReminders(it[REMINDERS]) }

suspend fun Context.saveReminders(reminders: List<Reminder>) {
    val encoded = json.encodeToString(reminders)
    store.edit { it[REMINDERS] = encoded }
}

/** Human-readable name for a picked mp3; the raw URI's last segment is usually a document id. */
fun Context.displayName(uri: Uri): String =
    runCatching {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment ?: "sound"
