package io.github.wlemkens.breath

import android.app.NotificationManager.INTERRUPTION_FILTER_ALARMS
import android.app.NotificationManager.INTERRUPTION_FILTER_ALL
import android.app.NotificationManager.INTERRUPTION_FILTER_PRIORITY
import android.app.NotificationManager.INTERRUPTION_FILTER_UNKNOWN
import org.junit.Assert.assertEquals
import org.junit.Test

class PlatformTest {
    @Test
    fun `an unknown filter is restored as all, never written back as unknown`() {
        assertEquals(INTERRUPTION_FILTER_ALL, restorableFilter(INTERRUPTION_FILTER_UNKNOWN))
    }

    @Test
    fun `a real filter is kept, so a phone already in DND stays there afterwards`() {
        assertEquals(INTERRUPTION_FILTER_ALL, restorableFilter(INTERRUPTION_FILTER_ALL))
        assertEquals(INTERRUPTION_FILTER_PRIORITY, restorableFilter(INTERRUPTION_FILTER_PRIORITY))
        assertEquals(INTERRUPTION_FILTER_ALARMS, restorableFilter(INTERRUPTION_FILTER_ALARMS))
    }
}
