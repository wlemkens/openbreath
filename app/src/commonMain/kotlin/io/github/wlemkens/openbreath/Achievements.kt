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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.time.Clock
import io.github.wlemkens.openbreath.media.Res
import io.github.wlemkens.openbreath.media.*
import org.jetbrains.compose.resources.stringResource

/**
 * What the log adds up to. Every figure here is read back out of the sittings, so a goal set
 * today already shows the run of days behind it rather than starting from nothing.
 */
@Composable
fun AchievementsScreen(goals: List<Goal>, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val store = LocalStore.current
    val history by remember { store.historyFlow() }.collectAsState(initial = emptyList())
    // read only, never written back from here, so a first frame of "none yet" costs a frame and
    // not a milestone — which is the whole of the empty-list trap the storage notes warn about
    val celebrated by remember { store.celebratedFlow() }.collectAsState(initial = 0)
    // for the badges alone, and read here rather than passed in: the colour of a milestone is the
    // colour its fireworks were, and falls back to the theme's until the config has loaded
    val config by remember { store.configFlow() }.collectAsState(initial = null)
    val glow = config?.let { Color(it.cueColor).let { c -> if (it.vividCue) c.vivid() else c } }
        ?: MaterialTheme.colorScheme.primary
    val now = Clock.System.now()

    LazyColumn(
        modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(Res.string.achievements_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onBack) { Text(stringResource(Res.string.action_done)) }
            }
        }

        if (history.isEmpty()) {
            item {
                Text(
                    stringResource(Res.string.achievements_empty),
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
            item { SectionLabel(stringResource(Res.string.achievements_section_goals)) }
            items(goals, key = { it.id }) { goal ->
                val run = history.streak(goal, now)
                val met = goal.reached(history.towards(goal, periodStartMs(goal.period, now)))
                Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Text(goal.description(), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        // a run of nothing is not "0 days in a row", it is a run not started
                        if (run == 0) stringResource(Res.string.achievements_not_yet)
                        else goal.period.run(run) + if (met) " ✓" else "",
                        style = MaterialTheme.typography.bodyMedium,
                        color =
                            if (run == 0) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        // between the goals you set yourself and the tallies that are the same for everybody:
        // these were earned, and unlike the streak above them they are not lost by missing a day
        val earned = milestonesReached(celebrated)
        if (earned.isNotEmpty()) {
            item { SectionLabel(stringResource(Res.string.achievements_section_milestones)) }
            // in the order they were reached
            items(earned, key = { "milestone-$it" }) { days -> MilestoneBadge(days, glow) }
        }

        item { SectionLabel(stringResource(Res.string.achievements_section_practice)) }
        item {
            val days = history.streak(EVERY_DAY, now)
            Stat(stringResource(Res.string.achievements_days_in_row), if (days == 0) "—" else days.toString())
        }
        item { Stat(stringResource(Res.string.achievements_sittings), history.size.toString()) }
        item { Stat(stringResource(Res.string.achievements_breaths), history.tally(GoalMetric.BREATHS).toString()) }
        item { Stat(stringResource(Res.string.achievements_minutes), history.tally(GoalMetric.MINUTES).toString()) }
        item {
            val today = history.towards(
                EVERY_DAY.copy(metric = GoalMetric.MINUTES),
                periodStartMs(GoalPeriod.DAY, now),
            )
            Stat(stringResource(Res.string.achievements_minutes_today), today.toString())
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
