package io.github.wlemkens.openbreath

import io.github.wlemkens.openbreath.media.Res
import io.github.wlemkens.openbreath.media.backup_import_body
import io.github.wlemkens.openbreath.media.backup_summary
import io.github.wlemkens.openbreath.media.goal_headline
import io.github.wlemkens.openbreath.media.goal_description
import io.github.wlemkens.openbreath.media.goal_today
import io.github.wlemkens.openbreath.media.minutes
import io.github.wlemkens.openbreath.media.sittings
import io.github.wlemkens.openbreath.media.support_p1
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * That the three languages actually resolve, and that the four things the catalogue relies on
 * survive the round trip through compose resources: plural selection, positional arguments that
 * a translation reorders, a typographic apostrophe, and an escaped newline.
 *
 * Desktop rather than commonTest because the locale has to be pinned. Every sentence the app
 * assembles out of several strings is checked here in all three, so "1 sittings" — the reason
 * this test existed when the summary was built by hand — cannot come back in any of them.
 */
class StringsTest {

    private val original: Locale = Locale.getDefault()

    @AfterTest
    fun restore() = Locale.setDefault(original)

    private fun <T> speaking(tag: String, block: suspend () -> T): T {
        Locale.setDefault(Locale.forLanguageTag(tag))
        return runBlocking { block() }
    }

    @Test
    fun `a count never disagrees with the word after it`() {
        assertEquals("sitting" to "sittings", speaking("en") {
            getPluralString(Res.plurals.sittings, 1) to getPluralString(Res.plurals.sittings, 2)
        })
        assertEquals("sessie" to "sessies", speaking("nl") {
            getPluralString(Res.plurals.sittings, 1) to getPluralString(Res.plurals.sittings, 2)
        })
        // French counts 0 as singular, which is the platform's rule and not ours to encode
        assertEquals("séance" to "séances", speaking("fr") {
            getPluralString(Res.plurals.sittings, 1) to getPluralString(Res.plurals.sittings, 2)
        })
    }

    @Test
    fun `a translation may put the arguments in its own order`() {
        val headline: suspend () -> String = {
            getString(
                Res.string.goal_headline,
                getString(Res.string.goal_today),
                7,
                10,
                getPluralString(Res.plurals.minutes, 10),
            )
        }
        assertEquals("Today: 7 of 10 minutes", speaking("en", headline))
        assertEquals("Vandaag: 7 van 10 minuten", speaking("nl", headline))
        // the count comes before what is counted in French, and the format string says so
        assertEquals("Aujourd’hui : 7 minutes sur 10", speaking("fr", headline))
    }

    @Test
    fun `the backup summary reads as a sentence in every language`() {
        val one = Backup(
            config = Config(presets = listOf(Preset())),
            history = listOf(Entry(1L, 300_000L, "x")),
            goals = listOf(Goal(id = 1)),
            reminders = listOf(Reminder(id = 1)),
        )
        assertEquals("1 sitting, 1 preset, 1 goal and 1 reminder", speaking("en") { one.summary() })
        assertEquals("1 sessie, 1 voorinstelling, 1 doel en 1 herinnering", speaking("nl") { one.summary() })
        assertEquals("1 séance, 1 préréglage, 1 objectif et 1 rappel", speaking("fr") { one.summary() })
        // and nothing at all reads as nothing, not as an error
        val none = Backup(config = Config(presets = emptyList()))
        assertEquals("0 sittings, 0 presets, 0 goals and 0 reminders", speaking("en") { none.summary() })
        assertEquals("0 sessies, 0 voorinstellingen, 0 doelen en 0 herinneringen", speaking("nl") { none.summary() })
        // zero is singular in French, which is the platform's plural rule doing its job
        assertEquals("0 séance, 0 préréglage, 0 objectif et 0 rappel", speaking("fr") { none.summary() })
    }

    @Test
    fun `the goal a first run offers reads as one sitting a day`() {
        val goal = newGoal(emptyList())
        val said: suspend () -> String = {
            getString(
                Res.string.goal_description,
                goal.target,
                getPluralString(goal.metric.plural, goal.target),
                getString(goal.period.each),
            )
        }
        assertEquals("1 sitting a day", speaking("en", said))
        assertEquals("1 sessie per dag", speaking("nl", said))
        assertEquals("1 séance par jour", speaking("fr", said))
    }

    @Test
    fun `an apostrophe and a paragraph break survive the resource pipeline`() {
        // ’ rather than ' throughout values-fr, so nothing has to be escaped in the XML
        assertTrue(speaking("fr") { getString(Res.string.support_p1) }.contains("d’essai"))
        val body = speaking("en") { getString(Res.string.backup_import_body, "X") }
        assertTrue(body.contains("\n\n"), "the paragraph break was eaten: [$body]")
        assertTrue(body.endsWith("replaced by the ones in the file."), body)
    }
}
