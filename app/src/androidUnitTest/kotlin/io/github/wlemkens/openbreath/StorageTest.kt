package io.github.wlemkens.openbreath

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What older versions of the app actually wrote to DataStore, decoded by today's code.
 *
 * A practice log and the goals behind it are the only things in this app that cannot be
 * recreated — presets and settings are a minute's work, but a year of sittings is a year. So
 * these strings are copies of real stored values and must keep decoding forever. Add to them
 * whenever the shape of what is stored changes; never edit one to make a failing test pass,
 * because the phone in someone's pocket still holds the old string.
 *
 * Two ways this has already been broken, both of which these tests would have caught:
 * renaming a stored enum constant, and moving a stored field to a different key.
 */
class StorageTest {
    /** The first shape the log ever had: no phase timings, no breath count. */
    private val historyV1 =
        """[{"at":1786345207163,"durationMs":300160,"preset":"Coherence 5.5"}]"""

    /** Once the pattern breathed was recorded alongside it. */
    private val historyV2 =
        """[{"at":1786345207163,"durationMs":300160,"preset":"Coherence 5.5","inhaleMs":5500,""" +
            """"holdInMs":0,"exhaleMs":5500,"holdOutMs":0,"cycles":27}]"""

    @Test
    fun `a log written by any older version still reads`() {
        for (stored in listOf(historyV1, historyV2)) {
            val log = decodeHistory(stored)
            assertEquals("an entry was dropped from $stored", 1, log.size)
            assertEquals(1786345207163L, log[0].at)
            assertEquals(300160L, log[0].durationMs)
            assertEquals("Coherence 5.5", log[0].preset)
        }
        // the older shape simply has no breaths to report, rather than failing to decode
        assertEquals(0, decodeHistory(historyV1)[0].cycles)
        assertEquals(27, decodeHistory(historyV2)[0].cycles)
    }

    @Test
    fun `goals written by an older version still read`() {
        val goals = decodeGoals(
            """[{"id":1,"metric":"SITTINGS","period":"DAY","target":1},""" +
                """{"id":2,"metric":"BREATHS","period":"WEEK","target":300}]"""
        )
        assertEquals(2, goals.size)
        assertEquals(GoalMetric.SITTINGS, goals[0].metric)
        assertEquals(1, goals[0].target)
        assertEquals(GoalPeriod.WEEK, goals[1].period)
        assertEquals(300, goals[1].target)
    }

    @Test
    fun `a single unreadable entry never costs the whole log`() {
        // decoding is all-or-nothing, so anything that can throw takes every sitting with it.
        // Whatever changes here, this has to keep returning what it can rather than nothing
        val log = decodeHistory("""[{"at":1,"durationMs":2,"preset":"x","unknownFutureField":9}]""")
        assertEquals(1, log.size)
        // and outright rubbish is an empty log, not a crash on the way to the log screen
        assertTrue(decodeHistory("not json at all").isEmpty())
        assertTrue(decodeGoals("").isEmpty())
        assertTrue(decodeHistory(null).isEmpty())
    }

    @Test
    fun `a config naming things this version has never heard of still keeps its presets`() {
        // "WAVES" was renamed to AMBIENT and "DRONE" to SOUNDWAVE. A stored preset that names
        // either must still load: losing the sound choice is a shrug, losing the preset is not
        val stored = """{"presets":[{"name":"Mine","inhaleMs":6000,"holdInMs":2000,""" +
            """"exhaleMs":7000,"holdOutMs":0,"inhaleSound":{"mode":"WAVES","voice":"DRONE"},""" +
            """"exhaleSound":{"mode":"MARKER","tone":"GONG"}}],"activeIndex":0,"durationMs":600000}"""
        val config = json.decodeFromString<Config>(stored).sane()
        assertEquals(1, config.presets.size)
        assertEquals("Mine", config.active.name)
        assertEquals(6000, config.active.inhaleMs)
        assertEquals(2000, config.active.holdInMs)
        assertEquals(600000L, config.durationMs)
        // the retired names fall back to a default rather than taking the preset down
        assertEquals(SoundMode.AMBIENT, config.active.soundOf(Phase.INHALE).mode)
        assertEquals(AmbientVoice.WAVES, config.active.soundOf(Phase.INHALE).voice)
        // and a name this version does still know is untouched
        assertEquals(MarkerTone.GONG, config.active.soundOf(Phase.EXHALE).tone)
    }
}
