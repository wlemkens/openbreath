package io.github.wlemkens.openbreath

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
            // kotlin.test takes the message last, where JUnit took it first
            assertEquals(1, log.size, "an entry was dropped from $stored")
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

    /**
     * A real exported file, the first shape it had. A backup someone saved today has to keep
     * opening in every version that follows — it is the only copy of their log that is not on
     * the phone, and the whole reason the feature exists.
     */
    private val backupV1 =
        """{"version":1,"exportedAt":1786345207163,"config":{"presets":[{"name":"Coherence 5.5",""" +
            """"inhaleMs":5500,"holdInMs":0,"exhaleMs":5500,"holdOutMs":0}],"activeIndex":0},""" +
            """"history":[{"at":1786345207163,"durationMs":300160,"preset":"Coherence 5.5"}],""" +
            """"goals":[{"id":1,"metric":"SITTINGS","period":"DAY","target":1}],"celebrated":7,""" +
            """"reminders":[{"id":1,"name":"Breathe","hour":8,"minute":0}]}"""

    @Test
    fun `a backup file written by an older version still opens`() {
        val backup = decodeBackup(backupV1)!!
        assertEquals(1, backup.version)
        assertEquals(1, backup.history.size)
        assertEquals(1786345207163L, backup.history[0].at)
        assertEquals("Coherence 5.5", backup.config.active.name)
        assertEquals(1, backup.goals.size)
        assertEquals(7, backup.celebrated)
        assertEquals("Breathe", backup.reminders[0].name)
    }

    @Test
    fun `a backup survives the round trip`() {
        val original = Backup(
            exportedAt = 1786345207163L,
            config = Config(durationMs = 600_000L),
            history = listOf(Entry(1L, 300_000L, "Coherence 5.5", cycles = 27)),
            goals = listOf(Goal(id = 1, metric = GoalMetric.MINUTES, target = 10)),
            celebrated = 30,
            reminders = listOf(Reminder(id = 1)),
        )
        assertEquals(original, decodeBackup(encodeBackup(original)))
    }

    @Test
    fun `anything that is not a backup is refused rather than mistaken for an empty one`() {
        // the caller has to be able to say "wrong file" instead of silently importing nothing
        assertEquals(null, decodeBackup("not json at all"))
        assertEquals(null, decodeBackup(null))
        assertEquals(null, decodeBackup(""))
    }

    @Test
    fun `a backup says what is in it without writing '1 sittings'`() {
        val one = Backup(
            config = Config(presets = listOf(Preset())),
            history = listOf(Entry(1L, 300_000L, "x")),
            goals = listOf(Goal(id = 1)),
            reminders = listOf(Reminder(id = 1)),
        )
        assertEquals("1 sitting, 1 preset, 1 goal and 1 reminder", one.summary)
        // and nothing at all reads as nothing, not as an error
        assertEquals("0 sittings, 0 presets, 0 goals and 0 reminders",
            Backup(config = Config(presets = emptyList())).summary)
    }

    @Test
    fun `importing merges the log both ways and never drops a sitting`() {
        fun sitting(at: Long) = Entry(at, 300_000L, "Coherence 5.5", cycles = 27)
        val onThisPhone = Backup(history = listOf(sitting(2), sitting(3)), celebrated = 30)
        val fromTheFile = Backup(history = listOf(sitting(1), sitting(2)), celebrated = 7)

        val merged = fromTheFile.mergedInto(onThisPhone)
        // the union of both, in order, with the sitting they share counted once
        assertEquals(listOf(1L, 2L, 3L), merged.history.map { it.at })
        // and the milestone already celebrated is not announced a second time
        assertEquals(30, merged.celebrated)

        // the other way round is the same log: which phone you import onto cannot matter
        assertEquals(
            merged.history.map { it.at },
            onThisPhone.mergedInto(fromTheFile).history.map { it.at },
        )
    }

    @Test
    fun `importing replaces what can be typed again in a minute`() {
        val onThisPhone = Backup(
            config = Config(durationMs = 60_000L),
            goals = listOf(Goal(id = 9, target = 5)),
            reminders = listOf(Reminder(id = 9)),
        )
        val fromTheFile = Backup(
            config = Config(durationMs = 600_000L),
            goals = listOf(Goal(id = 1, target = 10)),
            reminders = listOf(Reminder(id = 1, name = "Sit")),
        )
        val merged = fromTheFile.mergedInto(onThisPhone)
        assertEquals(600_000L, merged.config.durationMs)
        assertEquals(listOf(1), merged.goals.map { it.id })
        assertEquals(listOf("Sit"), merged.reminders.map { it.name })
    }

    @Test
    fun `a merged log is capped without losing the newest sittings`() {
        fun sitting(at: Long) = Entry(at, 300_000L, "Coherence 5.5")
        val onThisPhone = Backup(history = (1L..HISTORY_MAX).map { sitting(it) })
        val fromTheFile = Backup(history = (1L..500L).map { sitting(it + HISTORY_MAX) })

        val merged = fromTheFile.mergedInto(onThisPhone)
        assertEquals(HISTORY_MAX, merged.history.size)
        // the cap drops the oldest, never the most recent
        assertEquals(HISTORY_MAX + 500L, merged.history.last().at)
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
        // settings added after this config was written take their defaults, not a decode failure
        assertEquals(false, config.vividCue)
        assertEquals(false, config.advancedSettings)
        assertEquals(4000, config.leadInMs)
        // the retired names fall back to a default rather than taking the preset down
        assertEquals(SoundMode.AMBIENT, config.active.soundOf(Phase.INHALE).mode)
        assertEquals(AmbientVoice.WAVES, config.active.soundOf(Phase.INHALE).voice)
        // and a name this version does still know is untouched
        assertEquals(MarkerTone.GONG, config.active.soundOf(Phase.EXHALE).tone)
    }
}
