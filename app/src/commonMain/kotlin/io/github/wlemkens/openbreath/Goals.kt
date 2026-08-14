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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.time.Clock
import kotlin.math.roundToInt

@Composable
fun GoalsScreen(
    goals: List<Goal>,
    onChange: (List<Goal>) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val store = LocalStore.current
    val history by remember { store.historyFlow() }.collectAsState(initial = emptyList())
    var editing by remember { mutableStateOf<Goal?>(null) }

    fun save(edited: List<Goal>) =
        onChange(edited.sortedWith(compareBy({ it.period }, { it.metric })))

    LazyColumn(
        modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Goals", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                TextButton(onClick = onBack) { Text("Done") }
            }
        }

        if (goals.isEmpty()) {
            item {
                Text(
                    "None yet. A goal is an amount to reach in a day or a week — one sitting a " +
                        "day, a hundred breaths, twenty minutes. Keep as many as you like.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
                )
            }
        }

        items(goals, key = { it.id }) { goal ->
            GoalProgress(
                goal = goal,
                history = history,
                modifier = Modifier.clickable { editing = goal }.padding(vertical = 8.dp),
            )
        }

        item {
            Button(
                onClick = { editing = Goal(id = (goals.maxOfOrNull { it.id } ?: 0) + 1) },
                modifier = Modifier.padding(top = 12.dp),
            ) { Text("Add a goal") }
        }
    }

    editing?.let { goal ->
        GoalDialog(
            goal = goal,
            onDismiss = { editing = null },
            onDelete = {
                editing = null
                save(goals.filterNot { it.id == goal.id })
            },
            onSave = { edited ->
                editing = null
                save(goals.filterNot { it.id == edited.id } + edited)
            },
        )
    }
}

/** One goal and how far into it you are. Shown wherever a goal is worth seeing. */
@Composable
fun GoalProgress(goal: Goal, history: List<Entry>, modifier: Modifier = Modifier) {
    // read on every recomposition rather than remembered: the period it counts rolls over while
    // the screen is open, at midnight if nothing else
    val done = history.towards(goal, periodStartMs(goal.period, Clock.System.now()))
    val reached = goal.reached(done)
    Column(modifier.fillMaxWidth()) {
        Text(
            goal.headline(done) + if (reached) " ✓" else "",
            style = MaterialTheme.typography.titleMedium,
            color =
                if (reached) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
        )
        LinearProgressIndicator(
            progress = { (done.toFloat() / goal.target).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GoalDialog(
    goal: Goal,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onSave: (Goal) -> Unit,
) {
    // sane() on every edit, so switching metric drags the target into what that metric can mean
    var draft by remember { mutableStateOf(goal) }
    fun edit(f: (Goal) -> Goal) {
        draft = f(draft).sane()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${draft.target} ${draft.metric.label}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GoalMetric.entries.forEach { metric ->
                        FilterChip(
                            selected = draft.metric == metric,
                            onClick = { edit { it.copy(metric = metric) } },
                            label = { Text(metric.label.replaceFirstChar(Char::uppercase)) },
                        )
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GoalPeriod.entries.forEach { period ->
                        FilterChip(
                            selected = draft.period == period,
                            onClick = { edit { it.copy(period = period) } },
                            label = { Text(period.label) },
                        )
                    }
                }
                LabelledSlider(
                    label = "Target",
                    value = draft.target.toFloat() / draft.metric.step,
                    range = (draft.metric.min.toFloat() / draft.metric.step)..
                        (draft.metric.max.toFloat() / draft.metric.step),
                    steps = (draft.metric.max - draft.metric.min) / draft.metric.step - 1,
                    readout = "${draft.target} ${draft.metric.label}",
                    onChange = { steps -> edit { it.copy(target = steps.roundToInt() * it.metric.step) } },
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(draft) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDelete) { Text("Delete") } },
    )
}
