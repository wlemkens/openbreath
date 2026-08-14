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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlin.time.Clock

/**
 * What the log adds up to. Every figure here is read back out of the sittings, so a goal set
 * today already shows the run of days behind it rather than starting from nothing.
 */
@Composable
fun AchievementsScreen(goals: List<Goal>, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val history by remember { context.historyFlow() }.collectAsState(initial = emptyList())
    val now = Clock.System.now()

    LazyColumn(
        modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Achievements",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onBack) { Text("Done") }
            }
        }

        if (history.isEmpty()) {
            item {
                Text(
                    "Nothing to show yet. Sit for a while and this fills itself in.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            return@LazyColumn
        }

        // your own goals first: they are the ones you chose, and the tallies below are the same
        // for everybody
        if (goals.isNotEmpty()) {
            item { SectionLabel("Goals") }
            items(goals, key = { it.id }) { goal ->
                val run = history.streak(goal, now)
                val met = goal.reached(history.towards(goal, periodStartMs(goal.period, now)))
                Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Text(goal.description, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        // a run of nothing is not "0 days in a row", it is a run not started
                        if (run == 0) "Not yet" else goal.period.run(run) + if (met) " ✓" else "",
                        style = MaterialTheme.typography.bodyMedium,
                        color =
                            if (run == 0) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        item { SectionLabel("Practice") }
        item {
            val days = history.streak(EVERY_DAY, now)
            Stat("Days in a row", if (days == 0) "—" else days.toString())
        }
        item { Stat("Sittings", history.size.toString()) }
        item { Stat("Minutes", history.tally(GoalMetric.MINUTES).toString()) }
        item {
            val today = history.towards(
                EVERY_DAY.copy(metric = GoalMetric.MINUTES),
                periodStartMs(GoalPeriod.DAY, now),
            )
            Stat("Minutes today", today.toString())
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}
