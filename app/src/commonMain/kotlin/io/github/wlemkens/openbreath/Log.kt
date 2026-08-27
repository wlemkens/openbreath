package io.github.wlemkens.openbreath

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** Every sitting worth the name, newest first, under the day it happened on. */
@Composable
fun LogScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val platform = LocalPlatform.current
    val store = LocalStore.current
    val history by remember { store.historyFlow() }.collectAsState(initial = emptyList())
    // the store keeps them in the order they were last written; a log reads newest first
    val byDay = remember(history) {
        history.sortedByDescending { it.at }.groupBy { it.at.zoned().date }
    }

    LazyColumn(
        modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Log", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                TextButton(onClick = onBack) { Text("Done") }
            }
        }

        if (history.isEmpty()) {
            item {
                Text(
                    "Nothing yet. A sitting shows up here once it has run for " +
                        "${LOGGED_MIN_MS / 1000} seconds, finished or not.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        } else {
            item {
                val total = history.sumOf { it.durationMs }
                Text(
                    "${history.size} ${if (history.size == 1) "sitting" else "sittings"}, " +
                        "${hoursMinutes(total)} in all",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        byDay.forEach { (day, sittings) ->
            item(key = "day-$day") { SectionLabel(platform.formats.dayLabel(day)) }
            items(sittings, key = { it.at }) { entry ->
                Column(Modifier.padding(bottom = 6.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        val began = entry.at.zoned()
                        Text(
                            platform.formats.clockTime(began.hour, began.minute),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            entry.preset,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f).padding(start = 12.dp),
                        )
                        Text(mmss(entry.durationMs), style = MaterialTheme.typography.bodyMedium)
                    }
                    // the preset's name is the user's to reuse for anything; the pattern says
                    // what was actually breathed
                    if (entry.pattern.isNotEmpty()) {
                        Text(
                            "${entry.pattern} · ${entry.cycles} " +
                                if (entry.cycles == 1) "breath cycle" else "breath cycles",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private fun Long.zoned() =
    Instant.fromEpochMilliseconds(this).toLocalDateTime(TimeZone.currentSystemDefault())

// the day heading and the time of day both go through Formats, and for the same reason: a log is
// read at a glance and wants the reader's own conventions for date order and for whether a clock
// says 14:00 or 2 PM. kotlinx-datetime answers neither — it carries no locale data at all

private fun hoursMinutes(ms: Long): String {
    val minutes = ms / 60_000
    return if (minutes < 60) "${minutes}m" else "${minutes / 60}h ${minutes % 60}m"
}
