package io.github.wlemkens.openbreath

import org.junit.Test
import java.awt.BasicStroke
import java.awt.Color
import java.awt.RadialGradientPaint
import java.awt.RenderingHints
import java.awt.geom.Point2D
import java.awt.image.BufferedImage
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
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
        val dots = mutableListOf<Dot>()

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
            if (alpha * 0.15f > 0.02f) dots += Dot(cx, cy, size * 2.6f, color, alpha * 0.15f)
            if (alpha > 0.02f) dots += Dot(cx, cy, size, color, alpha)
        }

        for (d in dots) body.append(dot(d.cx, d.cy, d.r, d.rgb, d.alpha))

        File("src/androidMain/res/drawable/ic_launcher_foreground.xml").writeText(
            HEADER + body + FOOTER
        )
        writeIosIcon(dots)
    }

    private class Dot(val cx: Float, val cy: Float, val r: Float, val rgb: Int, val alpha: Float)

    /**
     * The same cloud again, as the single 1024 px PNG an iOS app icon is.
     *
     * From the same geometry as the vector above rather than exported from it, which is the whole
     * point of the TODO this answers: two icons drawn from one lattice cannot drift apart.
     *
     * **No alpha channel.** An iOS app icon with one is rejected on upload, so this paints the
     * Ink background first and composites onto it — the same Ink the adaptive icon names as its
     * background colour and the session screen uses.
     *
     * The whole 108 viewport is drawn rather than the 72 the launcher mask keeps. Android crops a
     * circle out of the middle, where iOS only rounds the corners, so drawing the same fraction on
     * both would leave the sphere pressed against the edges here. Full viewport puts it at roughly
     * the size Android's mask leaves it looking.
     */
    private fun writeIosIcon(dots: List<Dot>) {
        val px = 1024
        val scale = px / 108f
        val image = BufferedImage(px, px, BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

        g.color = Color(INK)
        g.fillRect(0, 0, px, px)

        // the faint ring and the gradient wash the vector draws before its points, so the sphere
        // still reads as a sphere and not only as a scatter. The three stops are the vector's own
        val r = R * scale
        val c = CENTRE * scale
        g.paint = RadialGradientPaint(
            Point2D.Float(c, c),
            r,
            floatArrayOf(0f, 0.6f, 1f),
            arrayOf(Color(0x2E5FD6C8, true), Color(0x241E4B78, true), Color(0x001E4B78, true)),
        )
        g.fillOval((c - r).toInt(), (c - r).toInt(), (2 * r).toInt(), (2 * r).toInt())

        g.paint = Color(GLOW or (26 shl 24), true)
        g.stroke = BasicStroke(1.5f * scale)
        g.drawOval((c - r).toInt(), (c - r).toInt(), (2 * r).toInt(), (2 * r).toInt())

        for (d in dots) {
            val a = (d.alpha * 255f).toInt().coerceIn(0, 255)
            g.paint = Color(d.rgb or (a shl 24), true)
            val rr = d.r * scale
            g.fillOval((d.cx * scale - rr).toInt(), (d.cy * scale - rr).toInt(), (2 * rr).toInt(), (2 * rr).toInt())
        }
        g.dispose()

        val out = File("../iosApp/Sources/Assets.xcassets/AppIcon.appiconset/icon-1024.png")
        out.parentFile.mkdirs()
        ImageIO.write(image, "png", out)
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

        /** Ink, the same background colors.xml gives the adaptive icon. */
        const val INK = 0x07090F


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
