package io.github.wlemkens.openbreath

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking

/**
 * The first-run reminder has to ask before it arms, and the order is the whole of it: iOS drops a
 * notification it has no authorisation for silently, so an `apply` before a `request` is a
 * reminder that never arrives and never says why. It shipped that way — no prompt when the switch
 * was turned on, and no notification afterwards.
 *
 * In desktopTest rather than commonTest because driving a `suspend` function needs a runner, and
 * commonTest has only `kotlin("test")` — `runBlocking` is the JVM's. It is the same reach into
 * commonMain's `internal` that DesktopAudioTest makes for MARKER_PEAK, and the desktop-linux job
 * runs it on every push.
 */
class FirstRunReminderTest {

    private class RecordingScheduler(private val allow: Boolean) : ReminderScheduler {
        val calls = mutableListOf<String>()
        override val supported = true
        override val canRingUntilDismissed = false
        override val lateness: String? = null
        override fun fixLateness() = Unit
        override suspend fun permitted() = allow
        override suspend fun request(): Boolean {
            calls += "request"
            return allow
        }
        override fun apply(reminders: List<Reminder>) {
            calls += "apply ${reminders.map { it.id }}"
        }
        override fun cancel(id: Int) {
            calls += "cancel"
        }
    }

    @Test
    fun `the first-run reminder asks for permission before it arms`() = runBlocking {
        val scheduler = RecordingScheduler(allow = true)
        armSetupReminder(scheduler, setupReminder())
        assertEquals(listOf("request", "apply [1]"), scheduler.calls)
    }

    // A refusal is not a reason to throw the reminder away: apply runs again on every launch, so
    // permission granted later starts it working. Dropping it would lose what was asked for.
    @Test
    fun `a refused permission still arms the reminder`() = runBlocking {
        val scheduler = RecordingScheduler(allow = false)
        armSetupReminder(scheduler, setupReminder())
        assertEquals(listOf("request", "apply [1]"), scheduler.calls)
    }

    // the desktop answers NoReminders, and the question leaves the reminder half out there — so
    // nothing should be asked for and nothing armed
    @Test
    fun `no scheduler means nothing is asked and nothing armed`() = runBlocking {
        armSetupReminder(null, setupReminder())
    }
}
