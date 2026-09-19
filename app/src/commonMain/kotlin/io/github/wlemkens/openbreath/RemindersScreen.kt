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
import io.github.wlemkens.openbreath.media.Res
import io.github.wlemkens.openbreath.media.*
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
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

    // arming and storing in one coroutine: arming reads the words the notification will carry,
    // which is a resource lookup and therefore suspends
    fun save(list: List<Reminder>) = scope.launch {
        scheduler.apply(list)
        store.saveReminders(list)
    }

    LazyColumn(
        modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(Res.string.reminders_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onBack) { Text(stringResource(Res.string.action_done)) }
            }
        }

        if (reminders.isEmpty()) {
            item {
                Text(
                    stringResource(Res.string.reminders_empty),
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
                            (if (reminder.alarm && scheduler.canRingUntilDismissed) {
                                stringResource(Res.string.reminder_tag_alarm)
                            } else {
                                ""
                            }) +
                            (if (reminder.onlyIfBehind) {
                                stringResource(Res.string.reminder_tag_only_behind)
                            } else {
                                ""
                            }),
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
            ) { Text(stringResource(Res.string.reminders_add)) }
        }

        // Android says something here when it has not been given the exact-alarm permission,
        // which it stopped granting by default in 14: the reminder still comes, just not
        // necessarily to the minute. iOS has nothing to say and answers false.
        if (scheduler.late && reminders.isNotEmpty()) {
            item {
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(Res.string.reminder_lateness),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { scheduler.fixLateness() }) { Text(stringResource(Res.string.action_fix)) }
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
        // the name is the user's own text and stays as they typed it
        title = {
            Text(
                if (reminder.name.isBlank()) stringResource(Res.string.reminder_untitled)
                else reminder.name
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = { draft = draft.copy(name = it) },
                    label = { Text(stringResource(Res.string.reminder_name)) },
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
                            label = {
                                val hint = repeat.weekHint(today)
                                Text(
                                    stringResource(repeat.label) +
                                        if (hint != null) stringResource(hint) else ""
                                )
                            },
                        )
                    }
                }
                // Left out entirely where a reminder cannot be made to ring until dismissed,
                // which is iOS — that wants the Critical Alerts entitlement, granted case by
                // case. The rule every other capability here follows: a platform that cannot do
                // something says so, rather than offering a switch that does something quieter
                // than it promises.
                if (scheduler.canRingUntilDismissed) {
                    ToggleRow(stringResource(Res.string.reminder_ring), draft.alarm) { draft = draft.copy(alarm = it) }
                    Text(
                        stringResource(
                            if (draft.alarm) Res.string.reminder_ring_on
                            else Res.string.reminder_ring_off
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                ToggleRow(stringResource(Res.string.reminder_only_behind), draft.onlyIfBehind) {
                    draft = draft.copy(onlyIfBehind = it)
                }
                Text(
                    stringResource(
                        if (draft.onlyIfBehind) Res.string.reminder_only_behind_on
                        else Res.string.reminder_only_behind_off
                    ),
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
                Text(stringResource(Res.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDelete) { Text(stringResource(Res.string.action_delete)) }
        },
    )
}

/** The week starting where the reader's own calendar starts it, which is not Monday everywhere. */
internal fun weekDays(): List<DayOfWeek> {
    val first = firstDayOfWeek().isoDayNumber
    return (0..6).map { DayOfWeek.entries[(first - 1 + it) % 7] }
}
