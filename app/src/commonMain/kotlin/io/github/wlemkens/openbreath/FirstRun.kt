package io.github.wlemkens.openbreath

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * What the very first launch offers: a goal, and a reminder for the evenings it has not been
 * met. Both start off — the recommendation is said out loud, but a default that sets goals for
 * someone who never asked is the app deciding how they should practise.
 *
 * Offered once, and only to a phone that has never stored anything. See [Store.untouchedFlow]
 * for why that is not a flag of its own.
 *
 * The reminder half is armed through the platform's own scheduler, and is left out where there is no
 * such thing yet — iOS, where the question then asks about the goal alone rather than promising
 * a notification that would never come.
 */
@Composable
fun FirstRunSetup() {
    val store = LocalStore.current
    val platform = LocalPlatform.current
    // null where the platform has no scheduler, which leaves the reminder half of the question
    // out rather than offering to set one that could never arrive
    val onReminder = platform.reminders.takeIf { it.supported }
    val scope = rememberCoroutineScope()
    val untouched by remember { store.untouchedFlow() }.collectAsState(initial = null)

    var wantGoal by remember { mutableStateOf(false) }
    var wantReminder by remember { mutableStateOf(false) }

    // null while DataStore is still reading, and false forever after the answer is stored — which
    // is also what closes the dialog, so there is no "shown already" state to keep
    if (untouched != true) return

    fun finish() {
        scope.launch {
            if (wantGoal) store.saveGoals(listOf(setupGoal()))
            if (wantReminder) {
                val reminder = setupReminder()
                store.saveReminders(listOf(reminder))
                armSetupReminder(onReminder, reminder)
            }
            // written last and always: two noes are an answer, and one that must not be asked again
            store.markSetupDone()
        }
    }

    AlertDialog(
        // tapping outside is an answer too, and applies whatever is switched on: a dialog that
        // comes back tomorrow because it was dismissed the wrong way is nagging
        onDismissRequest = ::finish,
        title = { Text("Welcome") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // the sentence follows the switches. Promising "a reminder for the evenings it has
                // not happened yet" on a platform with no reminders is the app claiming a feature
                // it does not have, in the one place a reader has no way to check — and it read
                // that way on iOS from the day the question was written
                Text(
                    if (onReminder != null) {
                        "A breathing meditation every day is what we would recommend. We can set " +
                            "that up as a goal, and a reminder for the evenings it has not " +
                            "happened yet. Neither is needed, and both can be changed or dropped " +
                            "later."
                    } else {
                        "A breathing meditation every day is what we would recommend. We can set " +
                            "that up as a goal. It is not needed, and it can be changed or " +
                            "dropped later."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                ToggleRow("A goal of one sitting a day", wantGoal) { wantGoal = it }
                if (onReminder != null) {
                    val at = platform.formats.clockTime(SETUP_REMINDER_HOUR, 0)
                    ToggleRow("A reminder at $at when it is still undone", wantReminder) {
                        wantReminder = it
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = ::finish) { Text("Continue") } },
    )
}

/**
 * Arms the first-run reminder, and **asks for permission first**.
 *
 * Saying yes here used to go straight to [ReminderScheduler.apply], which arms it into nothing:
 * iOS drops a notification it has no authorisation for without a word, and Android 13+ does the
 * same without POST_NOTIFICATIONS. So the symptom was not a missing dialog, it was a reminder
 * that never arrived — silently, for anyone who took the offer here rather than making their
 * first reminder on the Reminders screen, which has always asked.
 *
 * Switching it on *is* the moment to ask: that screen's own comment says permission is wanted
 * "at the moment the first reminder is made, rather than on a first run by someone who may never
 * want one", and someone who has just turned this on is no longer that person.
 *
 * A refusal still arms it. [ReminderScheduler.apply] runs again on every launch, so permission
 * granted later starts it working, where dropping the reminder would quietly lose what was asked
 * for. Nothing reads [ReminderScheduler.permitted] yet, which is the other half of this and is
 * still owed: a refusal is invisible on both paths.
 */
internal suspend fun armSetupReminder(scheduler: ReminderScheduler?, reminder: Reminder) {
    scheduler ?: return
    scheduler.request()
    scheduler.apply(listOf(reminder))
}

/**
 * One sitting a day: the same goal "Add a goal" starts from, because the first goal anyone sets
 * is the one most worth being able to keep.
 */
internal fun setupGoal() = newGoal(emptyList())

private const val SETUP_REMINDER_HOUR = 20

/**
 * Evening, daily, and quiet once the day's goal is reached — the reminder is there for the days
 * it did not happen, not to announce a day that went fine.
 *
 * With the goal declined there is nothing to be behind on, and [Reminder.onlyIfBehind] then
 * lets it through as a plain evening reminder. That is the honest reading of "remind me when I
 * have not practised" from someone who would not say how much practising counts.
 */
internal fun setupReminder() =
    Reminder(id = 1, hour = SETUP_REMINDER_HOUR, minute = 0, onlyIfBehind = true)
