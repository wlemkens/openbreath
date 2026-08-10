package io.github.wlemkens.breath

import org.junit.Test
import java.io.File
import java.util.Locale
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Draws the launcher foreground from the cue's own lattice, frozen at the top of the breath, so the
 * icon is the thing the app actually shows rather than a drawing of it.
 *
 * Not an assertion — it rewrites `ic_launcher_foreground.xml` on every test run. That is the point:
 * change the cloud, run the tests, and the icon follows. It is deterministic, so an unchanged cloud
 * leaves the file byte-identical and the working tree clean. Never hand-edit the generated file.
 *
 * ponytail: the constants below are copies of Cue.kt's privates, so a change there drifts silently
 * until someone looks at the icon. Promote them to internal if that ever actually bites.
 */
class IconGen {

    @Test
    fun generate() {
        val pts = cuePoints()
        val body = StringBuilder()

        for (o in pts.indices step STRIDE) {
            // openness 1: the radius factor the point takes at the top of the breath
            val f = pts[o + 5]
            val phase = pts[o + 4]
            val s1 = frac(phase * ROOT2)
            val s2 = frac(phase * ROOT3)
            var px = pts[o] * f
            var py = pts[o + 1] * f
            var pz = pts[o + 2] * f

            val u = frac(T / TRIP_PERIOD + s2)
            val strayed = if (u < TRIP_SHARE) sin(PI_F * u / TRIP_SHARE) else 0f
            if (strayed > 0f) {
                val w = (T - TRIP_SPAN * s1) * TRIP_RATE
                val q = s2 * TWO_PI
                px += strayed * (f * 0.85f * sin(w * 0.37f + q) - px)
                py += strayed * (f * 0.6f * sin(w * 0.53f + 1.1f + q) - py)
                pz += strayed * (f * 0.85f * cos(w * 0.31f + q) - pz)
            }

            px += WANDER * sin(T * (0.25f + 0.55f * s1) + phase)
            py += WANDER * sin(T * (0.22f + 0.52f * s2) + phase * 1.7f + 1.3f)
            pz += WANDER * sin(T * (0.28f + 0.5f * frac(s1 + s2)) + phase * 2.9f)

            val len = sqrt(px * px + py * py + pz * pz)
            if (len > 1f) {
                val k = 1f / len
                px *= k; py *= k; pz *= k
            }

            val yaw = T * 0.10f
            val pitch = TILT + T * 0.037f
            val cy0 = cos(yaw); val sy0 = sin(yaw)
            val cp = cos(pitch); val sp = sin(pitch)
            val x1 = px * cy0 + pz * sy0
            val z1 = pz * cy0 - px * sy0
            val y2 = py * cp - z1 * sp
            val z2 = z1 * cp + py * sp

            val d = (z2 + 1f) / 2f
            val twinkle = 0.55f + 0.45f * sin(T * 1.7f + phase)
            val alpha = (twinkle * (0.10f + 0.90f * d * d + 0.7f * strayed)).coerceIn(0f, 1f)
            val color = lerpRgb(DEEP, GLOW, d)
            val cx = CENTRE + x1 * R
            val cy = CENTRE + y2 * R
            val size = DOT * (0.9f + 1.3f * d) * (0.75f + 0.5f * s1)

            // the halo first, same as the draw loop; below 2% it is not worth the bytes
            if (alpha * 0.15f > 0.02f) body.append(dot(cx, cy, size * 2.6f, color, alpha * 0.15f))
            if (alpha > 0.02f) body.append(dot(cx, cy, size, color, alpha))
        }

        File("src/main/res/drawable/ic_launcher_foreground.xml").writeText(
            HEADER + body + FOOTER
        )
    }

    private fun dot(cx: Float, cy: Float, r: Float, rgb: Int, alpha: Float): String {
        val a = (alpha * 255f).toInt().coerceIn(0, 255)
        // two half-circle arcs: a vector drawable has no <circle>
        return "    <path android:fillColor=\"#%02X%06X\" android:pathData=\"%s\" />\n".format(
            Locale.ROOT, a, rgb,
            "M%.2f,%.2fa%.2f,%.2f 0 1,0 %.2f,0a%.2f,%.2f 0 1,0 -%.2f,0".format(
                Locale.ROOT, cx - r, cy, r, r, 2 * r, r, r, 2 * r,
            ),
        )
    }

    private fun lerpRgb(a: Int, b: Int, t: Float): Int {
        fun ch(shift: Int): Int {
            val from = (a shr shift) and 0xFF
            val to = (b shr shift) and 0xFF
            return (from + (to - from) * t).toInt().coerceIn(0, 255) shl shift
        }
        return ch(16) or ch(8) or ch(0)
    }

    private fun frac(f: Float) = f - floor(f)

    private companion object {
        /** Centre and radius of the cue inside the 108 viewport, kept clear of the launcher mask. */
        const val CENTRE = 54f
        const val R = 34f

        /**
         * A point's base radius. On a phone the cue draws 1dp dots into a ~195dp sphere; at icon
         * scale that is dust, so the dots are fattened until the cloud reads at launcher size.
         */
        const val DOT = 0.5f

        /**
         * Which instant of the cloud to freeze. At t = 0 every traveller is still bunched at the
         * start of its trip, which reads as a lopsided knot; a few seconds in, the cloud has spread
         * over the sphere the way it looks in use.
         */
        const val T = 5.5f

        const val DEEP = 0x1E4B78
        const val GLOW = 0x5FD6C8

        // copies of Cue.kt's privates
        const val TILT = 0.4f
        const val WANDER = 0.13f
        const val TRIP_PERIOD = 10f
        const val TRIP_SHARE = 0.55f
        const val TRIP_RATE = 0.6f
        const val TRIP_SPAN = 8f
        const val ROOT2 = 0.4142136f
        const val ROOT3 = 0.7320508f
        const val TWO_PI = 6.2831855f
        const val PI_F = 3.1415927f

        val HEADER = """
            <?xml version="1.0" encoding="utf-8"?>
            <!-- Generated by IconGen: the breath cue's own point lattice at the top of the breath,
                 in the default cue colour, since a launcher icon can't follow the one picked in
                 settings. Edit IconGen and re-run it rather than this file. -->
            <vector xmlns:android="http://schemas.android.com/apk/res/android"
                xmlns:aapt="http://schemas.android.com/aapt"
                android:width="108dp"
                android:height="108dp"
                android:viewportWidth="108"
                android:viewportHeight="108">

                <path
                    android:pathData="M20,54a34,34 0 1,0 68,0a34,34 0 1,0 -68,0"
                    android:strokeWidth="1.5"
                    android:strokeColor="#5FD6C8"
                    android:strokeAlpha="0.10" />

                <path android:pathData="M20,54a34,34 0 1,0 68,0a34,34 0 1,0 -68,0">
                    <aapt:attr name="android:fillColor">
                        <gradient
                            android:type="radial"
                            android:centerX="54"
                            android:centerY="54"
                            android:gradientRadius="34">
                            <item android:offset="0" android:color="#2E5FD6C8" />
                            <item android:offset="0.6" android:color="#241E4B78" />
                            <item android:offset="1" android:color="#001E4B78" />
                        </gradient>
                    </aapt:attr>
                </path>

        """.trimIndent().trimStart() + "\n"

        const val FOOTER = "</vector>\n"
    }
}
