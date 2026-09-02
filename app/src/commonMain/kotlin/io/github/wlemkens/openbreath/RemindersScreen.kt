package io.github.wlemkens.openbreath

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

@Composable
fun RemindersScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val platform = LocalPlatform.current
    val scheduler = platform.reminders
    val store = LocalStore.current
    val scope = rememberCoroutineScope()
    val reminders by remember { store.remindersFlow() }.collectAsState(initial = emptyList())
    var editing by remember { mutableStateOf<Reminder?>(null) }

    fun save(list: List<Reminder>) {
        scheduler.apply(list)
        scope.launch { store.saveReminders(list) }
    }

    LazyColumn(
        modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Reminders", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                TextButton(onClick = onBack) { Text("Done") }
            }
        }

        if (reminders.isEmpty()) {
            item {
                Text(
                    "None yet. A reminder is a notification at a time you choose, nothing more.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
                )
            }
        }

        items(reminders, key = { it.id }) { reminder ->
            Row(
                Modifier.fillMaxWidth().clickable { editing = reminder }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(reminder.name, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        reminder.summary(platform.formats) +
                            (if (reminder.alarm && scheduler.canRingUntilDismissed) " · alarm" else "") +
                            (if (reminder.onlyIfBehind) " · only when behind" else ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = reminder.enabled,
                    onCheckedChange = { on ->
                        val updated = reminder.copy(enabled = on)
                        if (!on) scheduler.cancel(reminder.id)
                        save(reminders.map { if (it.id == reminder.id) updated else it })
                    },
                )
            }
        }

        item {
            Button(
                onClick = {
                    // asked for at the moment the first reminder is made, rather than on a first
                    // run by someone who may never want one
                    scope.launch { scheduler.request() }
                    // ids are handed out above the highest in use, so deleting never reissues one
                    editing = Reminder(id = (reminders.maxOfOrNull { it.id } ?: 0) + 1)
                },
                modifier = Modifier.padding(top = 12.dp),
            ) { Text("Add a reminder") }
        }

        // Android says something here when it has not been given the exact-alarm permission,
        // which it stopped granting by default in 14: the reminder still comes, just not
        // necessarily to the minute. iOS has nothing to say and answers null.
        scheduler.lateness?.takeIf { reminders.isNotEmpty() }?.let { late ->
            item {
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        late,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { scheduler.fixLateness() }) { Text("Fix") }
                }
            }
        }
    }

    editing?.let { reminder ->
        ReminderDialog(
            reminder = reminder,
            onDismiss = { editing = null },
            onDelete = {
                editing = null
                scheduler.cancel(reminder.id)
                save(reminders.filterNot { it.id == reminder.id })
            },
            onSave = { edited ->
                editing = null
                val kept = reminders.filterNot { it.id == edited.id }
                save((kept + edited).sortedBy { it.hour * 60 + it.minute })
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ReminderDialog(
    reminder: Reminder,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onSave: (Reminder) -> Unit,
) {
    var draft by remember { mutableStateOf(reminder) }
    val platform = LocalPlatform.current
    val formats = platform.formats
    val scheduler = platform.reminders
    // TimeInput rather than the dial: it is a text field, so it fits in a dialog next to the
    // rest of the fields instead of filling the screen on its own
    val time = rememberTimePickerState(reminder.hour, reminder.minute, is24Hour = formats.uses24Hour)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (reminder.name.isBlank()) "Reminder" else reminder.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = { draft = draft.copy(name = it) },
                    label = { Text("Name") },
                    singleLine = true,
                )
                TimeInput(time)
                val today = Clock.System.now()
                    .toLocalDateTime(TimeZone.currentSystemDefault()).date
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Repeat.entries.forEach { repeat ->
                        FilterChip(
                            selected = draft.repeat == repeat,
                            onClick = { draft = draft.copy(repeat = repeat) },
                            label = { Text(repeat.label + repeat.weekHint(today)) },
                        )
                    }
                }
                // Left out entirely where a reminder cannot be made to ring until dismissed,
                // which is iOS — that wants the Critical Alerts entitlement, granted case by
                // case. The rule every other capability here follows: a platform that cannot do
                // something says so, rather than offering a switch that does something quieter
                // than it promises.
                if (scheduler.canRingUntilDismissed) {
                    ToggleRow("Ring until dismissed", draft.alarm) { draft = draft.copy(alarm = it) }
                    Text(
                        if (draft.alarm) {
                            "The alarm tone, over and over, heard through a silenced ringer. " +
                                "Dismiss the notification to stop it."
                        } else {
                            "One notification, at the volume everything else arrives at."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                ToggleRow("Only when behind on a goal", draft.onlyIfBehind) {
                    draft = draft.copy(onlyIfBehind = it)
                }
                Text(
                    if (draft.onlyIfBehind) {
                        "Stays quiet once every goal is reached. With no goals set there is " +
                            "nothing to be ahead of, so it comes as usual."
                    } else {
                        "Comes whether or not you have already practised."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (draft.repeat != Repeat.DAILY) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        weekDays().forEach { day ->
                            FilterChip(
                                selected = day.isoDayNumber in draft.days,
                                onClick = {
                                    val days =
                                        if (day.isoDayNumber in draft.days) draft.days - day.isoDayNumber
                                        else draft.days + day.isoDayNumber
                                    // the last day cannot be turned off: a weekly reminder with
                                    // no day is one that never comes
                                    if (days.isNotEmpty()) draft = draft.copy(days = days)
                                },
                                label = { Text(formats.shortDayName(day)) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(draft.copy(hour = time.hour, minute = time.minute)) }) {
                Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = onDelete) { Text("Delete") } },
    )
}

/** The week starting where the reader's own calendar starts it, which is not Monday everywhere. */
private fun weekDays(): List<DayOfWeek> {
    val first = firstDayOfWeek().isoDayNumber
    return (0..6).map { DayOfWeek.entries[(first - 1 + it) % 7] }
}

/** "Weekly Mon, Thu · 8:00 AM", in the reader's own conventions throughout. */
internal fun Reminder.summary(formats: Formats): String {
    val at = formats.clockTime(hour, minute)
    val on =
        if (repeat == Repeat.DAILY) ""
        else " " + weekDays().filter { it.isoDayNumber in days }
            .joinToString(", ") { formats.shortDayName(it) }
    return "${repeat.label}$on · $at"
}
