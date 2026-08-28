package io.github.wlemkens.openbreath

import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CueTest {
    private val pts = cuePoints()

    /** Slot 3 is where a point rests, slot 5 where the top of the breath puts it. */
    private fun factors(slot: Int) = (pts.indices step STRIDE).map { pts[it + slot] }

    @Test
    fun `yields whole points with finite coordinates`() {
        assertEquals(0, pts.size % STRIDE)
        assertTrue(pts.isNotEmpty() && pts.all { it.isFinite() })
    }

    @Test
    fun `draws the count asked for and clamps a nonsense one`() {
        assertEquals(60 * STRIDE, cuePoints(60).size)
        assertEquals(MIN_CUE_POINTS * STRIDE, cuePoints(-6).size)
        assertEquals(MAX_CUE_POINTS * STRIDE, cuePoints(100_000).size)
    }

    @Test
    fun `directions are unit vectors`() {
        val lengths = (pts.indices step STRIDE).map {
            sqrt(pts[it] * pts[it] + pts[it + 1] * pts[it + 1] + pts[it + 2] * pts[it + 2])
        }
        assertTrue(lengths.all { abs(it - 1f) < 1e-3f })
    }

    @Test
    fun `nothing is placed outside the ring`() {
        assertTrue((factors(3) + factors(5)).all { it in 0f..1f })
    }

    @Test
    fun `repacks from a core to a full sphere as the breath fills`() {
        val rest = factors(3)
        val full = factors(5)
        // kotlin.test takes the message last, where JUnit took it first
        assertTrue(rest.average() < full.average() - 0.15, "rest should be core-heavy")
        // the two radii are drawn independently, so a fair share of points move inward while the
        // cloud as a whole opens out — that swap is what separates this from a plain scale-up
        assertTrue(rest.zip(full).count { (a, b) -> a > b } > rest.size / 10)
    }
}
