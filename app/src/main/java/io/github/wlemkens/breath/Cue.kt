package io.github.wlemkens.breath

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** The breath cue: something that grows on the inhale and shrinks on the exhale. */
@Composable
fun Cue(style: CueStyle, openness: Float) {
    if (style == CueStyle.GLOW) GlowSphere(openness) else PointCloud(openness)
}

/** The original: a soft sphere, teal core fading through deep blue. */
@Composable
private fun GlowSphere(openness: Float) {
    Canvas(Modifier.fillMaxSize()) {
        val max = maxRadius()
        val r = max * (0.24f + 0.76f * openness)
        rimRing(max)
        drawCircle(
            brush = Brush.radialGradient(
                0f to Glow.copy(alpha = 0.95f),
                0.6f to Deep.copy(alpha = 0.75f),
                1f to Deep.copy(alpha = 0.05f),
                center = center,
                radius = r,
            ),
            radius = r,
            center = center,
        )
    }
}

/**
 * A cloud of light points turning slowly in place, breathing with [openness]. The points are a
 * fixed lattice; only the rotation, the twinkle and the radius change per frame, so what you
 * see is one object turning rather than a swarm reshuffling.
 */
@Composable
private fun PointCloud(openness: Float) {
    val pts = remember { cuePoints() }
    // the session clock only ticks while running; the cloud drifts on the idle screen too
    val seconds = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val t0 = withFrameNanos { it }
        while (true) seconds.floatValue = (withFrameNanos { it } - t0) / 1e9f
    }

    Canvas(Modifier.fillMaxSize()) {
        // read inside the draw lambda, not the composable body: a frame costs a redraw and no
        // recomposition
        val t = seconds.floatValue
        val max = maxRadius()
        val r = max * (0.24f + 0.76f * openness)
        rimRing(max)
        // the old gradient, dimmed, still under the points — it is what gives the cloud volume
        drawCircle(
            brush = Brush.radialGradient(
                0f to Glow.copy(alpha = 0.18f),
                0.6f to Deep.copy(alpha = 0.14f),
                1f to Deep.copy(alpha = 0f),
                center = center,
                radius = r,
            ),
            radius = r,
            center = center,
        )

        // yaw and a much slower pitch: two incommensurate rates, so the turn never visibly repeats
        val yaw = t * 0.10f
        val pitch = TILT + t * 0.037f
        val cy = cos(yaw); val sy = sin(yaw)
        val cp = cos(pitch); val sp = sin(pitch)
        val dot = 1.dp.toPx()

        var i = 0
        while (i < pts.size) {
            // where the point rests, and where the full breath puts it; the cloud repacks between
            // the two rather than simply scaling
            val f = pts[i + 3] + (pts[i + 5] - pts[i + 3]) * openness
            val phase = pts[i + 4]
            val s1 = frac(phase * ROOT2)
            val s2 = frac(phase * ROOT3)
            var px = pts[i] * f
            var py = pts[i + 1] * f
            var pz = pts[i + 2] * f
            i += STRIDE

            // Every point takes trips. The window it leaves in is hashed off its own phase, so at
            // any moment a slice of the cloud is out on the path and the rest is holding the shape
            // — and those out together are strung along that one path at their own lags, which is
            // the follow-the-leader part.
            val u = frac(t / TRIP_PERIOD + s2)
            val strayed = if (u < TRIP_SHARE) sin(PI_F * u / TRIP_SHARE) else 0f
            if (strayed > 0f) {
                val w = (t - TRIP_SPAN * s1) * TRIP_RATE
                // The path is turned by the same seed that sets the departure window, so points
                // leaving together share one and trail each other along it, while the cloud as a
                // whole spreads over a family of them. On a single shared path this many
                // travellers just pile into a knot.
                val q = s2 * TWO_PI
                // Scaled to the point's own radius: on a fixed one every traveller is dragged out
                // to the same shell, and with half the cloud travelling that flattens the packing
                // into a plain ball. Short of the rim either way — the drift below still has to
                // fit inside the ring.
                px += strayed * (f * 0.85f * sin(w * 0.37f + q) - px)
                py += strayed * (f * 0.6f * sin(w * 0.53f + 1.1f + q) - py)
                pz += strayed * (f * 0.85f * cos(w * 0.31f + q) - pz)
            }

            // Drift applied last, so a point out on the path rides it loosely instead of threading
            // a wire. The rates are per point, not just the phases — on shared rates the whole
            // field swings in step and the cloud reads as one rigid object however far each is off.
            px += WANDER * sin(t * (0.25f + 0.55f * s1) + phase)
            py += WANDER * sin(t * (0.22f + 0.52f * s2) + phase * 1.7f + 1.3f)
            pz += WANDER * sin(t * (0.28f + 0.5f * frac(s1 + s2)) + phase * 2.9f)

            // The ring is the edge of the cue, so nothing crosses it. One guard here rather than a
            // budget spread over the trip path and the drift — each pushes outward on its own and
            // only their sum has to fit.
            val len = sqrt(px * px + py * py + pz * pz)
            if (len > 1f) {
                val k = 1f / len
                px *= k; py *= k; pz *= k
            }

            // rotate about y, then about x
            val x1 = px * cy + pz * sy
            val z1 = pz * cy - px * sy
            val y2 = py * cp - z1 * sp
            val z2 = z1 * cp + py * sp

            val d = (z2 + 1f) / 2f // 0 at the back, 1 at the front
            // ponytail: index-phased sine instead of value noise — at this density it reads the same
            val twinkle = 0.55f + 0.45f * sin(t * 1.7f + phase)
            // a point off on its own lifts out of the depth fade, so the breakaway is legible even
            // when it is round the back
            val alpha = twinkle * (0.10f + 0.90f * d * d + 0.7f * strayed)
            val color = lerp(Deep, Glow, d)
            val at = Offset(center.x + x1 * r, center.y + y2 * r)
            val size = dot * (0.9f + 1.3f * d) * (0.75f + 0.5f * s1)

            // ponytail: two drawCircle per point, ~840 a frame on Cloud and Motes. If that ever
            // drops frames, bucket into a few depth bands and use drawPoints instead.
            drawCircle(color.copy(alpha = alpha * 0.15f), size * 2.6f, at)
            drawCircle(color.copy(alpha = alpha), size, at)
        }
    }
}

private fun DrawScope.maxRadius() = size.minDimension / 2f * 0.95f

/** A faint ring at full size, so the growth has something to grow towards. */
private fun DrawScope.rimRing(max: Float) =
    drawCircle(Glow.copy(alpha = 0.10f), max, center, style = Stroke(1.dp.toPx()))

/** Off-axis, so the lattice poles never sit in the middle of the face. */
private const val TILT = 0.4f

/**
 * How far a point strays from home on its own drift, as a fraction of the sphere. Roughly the
 * spacing between neighbours, so points visibly swim past each other without the shell dissolving.
 */
private const val WANDER = 0.13f

/** Seconds between one point's trip and its next. */
private const val TRIP_PERIOD = 10f

/**
 * The share of that period a point spends away — so also the share of the cloud travelling at any
 * moment, the rest of it sitting home holding the shape. Raise for fewer points standing still.
 */
private const val TRIP_SHARE = 0.55f

/** How fast the path itself is walked. Lower is a lazier swoop over the same ground. */
private const val TRIP_RATE = 0.6f

/**
 * Seconds between the front of the travelling line and its tail. Wants raising when [TRIP_RATE]
 * drops, or the lagged points bunch up: the same lag covers less path at a slower walk.
 */
private const val TRIP_SPAN = 8f

/** x, y, z, resting radius factor, twinkle phase, radius factor at the top of the breath. */
internal const val STRIDE = 6

/**
 * A Fibonacci lattice — directions spread over the sphere by stepping the golden angle round the
 * axis while walking down it — then scattered off it. The lattice alone covers evenly but reads as
 * rows of a spiral; the scatter breaks the rows while keeping the coverage, and being hashed off
 * the index rather than drawn at random the cloud is the same every launch.
 */
internal fun cuePoints(): FloatArray {
    val n = 420
    val out = FloatArray(n * STRIDE)
    for (i in 0 until n) {
        // scatter by about one lattice step, so a point lands anywhere in its own neighbourhood
        val y = (1f - 2f * (i + 0.5f) / n + (hash(i, 0f) - 0.5f) * 2.4f / n).coerceIn(-1f, 1f)
        val ring = sqrt((1f - y * y).coerceAtLeast(0f))
        val a = i * GOLDEN_ANGLE + (hash(i, 7.3f) - 0.5f) * 2.2f
        val o = i * STRIDE
        out[o] = cos(a) * ring
        out[o + 1] = y
        out[o + 2] = sin(a) * ring
        // cubed: at the bottom of the breath they are packed into a core, with a few loose ones
        // left out near the rim
        out[o + 3] = 0.25f + 0.75f * frac(i * ROOT2).let { it * it * it }
        out[o + 4] = frac(i * ROOT3) * TWO_PI
        // At the top of the breath the packing inverts — the core opens out and the loose ones are
        // drawn in. Its own irrational, so where a point ends up is independent of where it rests
        // and the two genuinely trade places rather than the whole cloud scaling. Square-rooted, an
        // evenly spread 0..1, which fills the volume without hollowing the middle out.
        out[o + 5] = sqrt(frac(i * ROOT5))
    }
    return out
}

private fun frac(f: Float) = f - kotlin.math.floor(f)

/** The usual shader hash: deterministic per index, and random enough for scatter. */
private fun hash(i: Int, salt: Float) = frac(sin(i * 12.9898f + salt) * 43758.547f)

private const val GOLDEN_ANGLE = 2.399963f

/**
 * Radius and twinkle step by their own irrationals rather than reusing the golden ratio: that one
 * is what the azimuth is already built from, so sharing it locks radius to angle and the cloud
 * comes out as a lopsided spiral instead of a sphere.
 */
private const val ROOT2 = 0.4142136f
private const val ROOT3 = 0.7320508f
private const val ROOT5 = 0.2360680f
private const val TWO_PI = 6.2831855f
private const val PI_F = 3.1415927f
