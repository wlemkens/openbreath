package io.github.wlemkens.openbreath

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The colour maths that used to be android.graphics.Color's.
 *
 * Worth testing rather than eyeballing: it is the one thing in the settings screen that can be
 * wrong by a little and still look plausible, and the stored value is a colour someone chose.
 */
class HsvTest {
    private fun assertHsv(expected: Triple<Float, Float, Float>, argb: Int) {
        val hsv = argbToHsv(argb)
        assertEquals(expected.first, hsv[0], 0.5f)
        assertEquals(expected.second, hsv[1], 0.01f)
        assertEquals(expected.third, hsv[2], 0.01f)
    }

    @Test
    fun `the primaries land where a colour wheel says they do`() {
        assertHsv(Triple(0f, 1f, 1f), 0xFFFF0000.toInt())
        assertHsv(Triple(120f, 1f, 1f), 0xFF00FF00.toInt())
        assertHsv(Triple(240f, 1f, 1f), 0xFF0000FF.toInt())
        assertHsv(Triple(60f, 1f, 1f), 0xFFFFFF00.toInt())
        assertHsv(Triple(180f, 1f, 1f), 0xFF00FFFF.toInt())
        assertHsv(Triple(300f, 1f, 1f), 0xFFFF00FF.toInt())
    }

    @Test
    fun `grey has no hue and black has no brightness`() {
        assertHsv(Triple(0f, 0f, 1f), 0xFFFFFFFF.toInt())
        assertHsv(Triple(0f, 0f, 0f), 0xFF000000.toInt())
        assertHsv(Triple(0f, 0f, 0.5f), 0xFF808080.toInt())
    }

    @Test
    fun `a colour survives the round trip, the shipped one included`() {
        // the teal the app shipped with is the one that actually matters
        for (argb in listOf(
            DEFAULT_CUE_COLOR,
            0xFFFF0000.toInt(),
            0xFF3C7A9E.toInt(),
            0xFF808080.toInt(),
            0xFF000000.toInt(),
            0xFFFFFFFF.toInt(),
        )) {
            val hsv = argbToHsv(argb)
            assertEquals(
                "round trip of ${argb.toUInt().toString(16)}",
                argb,
                hsvToArgb(hsv[0], hsv[1], hsv[2]),
            )
        }
    }

    @Test
    fun `hue wraps rather than clamping, so a slider at either end is the same red`() {
        assertEquals(hsvToArgb(0f, 1f, 1f), hsvToArgb(360f, 1f, 1f))
        // and everything written is opaque, whatever the sliders say
        assertEquals(0xFF, (hsvToArgb(200f, 0.3f, 0.7f) shr 24) and 0xFF)
    }
}
