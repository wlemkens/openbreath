package io.github.wlemkens.openbreath

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlin.time.Clock

/**
 * The whole of what this app knows about you, in one file you own.
 *
 * It exists because there is no other way out. `android:allowBackup` carries a log to a new
 * Android phone and nothing carries it to an iPhone, so without this a year of practice is
 * locked to the handset it was breathed on. It is also the answer to the risk that same flag
 * takes on purpose — a restore that quietly puts back an older copy is survivable if you have
 * a file of your own.
 *
 * This is a stored shape like any other, and the rules in CLAUDE.md apply to it in full. A
 * file exported today has to keep opening in every version that comes after. `version` is
 * written so a future reader can tell what it is looking at; nothing reads it yet, because
 * added fields with defaults have not needed it to.
 */
@Serializable
data class Backup(
    val version: Int = BACKUP_VERSION,
    /** Epoch ms, for the reader's benefit only — nothing decides anything on it. */
    val exportedAt: Long = 0L,
    val config: Config = Config(),
    val history: List<Entry> = emptyList(),
    val goals: List<Goal> = emptyList(),
    val celebrated: Int = 0,
    val reminders: List<Reminder> = emptyList(),
)

const val BACKUP_VERSION = 1

fun encodeBackup(backup: Backup): String = json.encodeToString(backup)

/**
 * What to call the file. The date is ISO on purpose and not in the reader's own format: this
 * is a filename, where 2026-08-14 sorts into order in any file manager on earth and 14/08/2026
 * sorts into none of them, and where a slash is not a character a name may contain.
 */
fun backupFileName(): String =
    "openbreath-${Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date}.json"

/**
 * Null when the text is not a backup at all, rather than an exception or an empty one. The
 * caller has to be able to tell "you picked the wrong file" from "your practice log is empty",
 * because those want very different words on screen.
 */
fun decodeBackup(text: String?): Backup? =
    text?.let { runCatching { json.decodeFromString<Backup>(it) }.getOrNull() }

private fun count(n: Int, one: String) = "$n ${if (n == 1) one else "${one}s"}"

/**
 * What is in it, for the dialog that asks whether to import. In commonMain because the
 * question is the same on any platform, and because "1 presets" is the kind of thing that
 * looks like nobody was paying attention.
 */
val Backup.summary: String
    get() = "${count(history.size, "sitting")}, ${count(config.presets.size, "preset")}, " +
        "${count(goals.size, "goal")} and ${count(reminders.size, "reminder")}"

/**
 * This backup applied on top of what is already here.
 *
 * The log is merged and everything else is replaced, and that asymmetry is the whole design.
 * Presets, goals and reminders are a minute's work to set up again; a sitting that is gone is
 * gone. So the log unions on [Entry.at] — the same key [logging] treats as an entry's identity
 * — and can only ever grow, whichever way round the two files are.
 *
 * Importing an older export onto a phone that has since been practised on therefore keeps both
 * sets of sittings, which is what someone moving between two phones actually wants and is the
 * one behaviour here that would be unforgivable to get wrong.
 *
 * [celebrated] takes the larger for the same reason in miniature: the smaller would re-announce
 * a milestone the user has already been congratulated for.
 */
fun Backup.mergedInto(current: Backup): Backup = copy(
    history = (current.history + history)
        .distinctBy { it.at }
        .sortedBy { it.at }
        .takeLast(HISTORY_MAX),
    celebrated = maxOf(celebrated, current.celebrated),
)
