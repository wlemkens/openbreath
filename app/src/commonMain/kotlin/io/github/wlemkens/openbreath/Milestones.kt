package io.github.wlemkens.openbreath

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Watches the run of days on which everything was done, and marks the lengths worth marking.
 *
 * On screen only: a milestone is earned by finishing a sitting, and a sitting only runs with
 * the app in front of you, so there is never a moment when it would have to be sent for.
 *
 * Nothing is stored but the last length celebrated: the run itself is counted back out of the
 * log, so a milestone cannot be lost, double-counted, or awarded for a day that was later
 * deleted.
 */
@Composable
fun MilestoneWatch(config: Config, goals: List<Goal>) {
    val store = LocalStore.current
    val scope = rememberCoroutineScope()
    val history by remember { store.historyFlow() }.collectAsState(initial = null)
    val celebrated by remember { store.celebratedFlow() }.collectAsState(initial = null)

    val log = history ?: return
    val last = celebrated ?: return
    val days = remember(log, goals) { log.allReachedStreak(goals, Clock.System.now()) }
    val due = dueMilestone(days, last) ?: return

    MilestoneDialog(due, Color(config.cueColor).let { if (config.vividCue) it.vivid() else it }) {
        // marked before the dialog closes, so a recomposition cannot show it a second time
        scope.launch { store.saveCelebrated(due) }
    }
}

@Composable
private fun MilestoneDialog(days: Int, glow: Color, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(milestoneLabel(days), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Fireworks(glow, Modifier.fillMaxWidth().height(180.dp))
                Text(
                    milestoneMessage(days),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            }
        },
        // not "Resume": this can land over a paused session, and a button that reads like a
        // session control on a screen that has one is a button pressed by mistake
        confirmButton = { TextButton(onClick = onDismiss) { Text("Onwards") } },
    )
}

/** One spark's whole life, decided once so the burst is the same shape every time it runs. */
private class Spark(
    val angle: Float,
    val reach: Float,
    val offset: Float,
    val size: Float,
    val pale: Float,
)

private const val SPARKS = 300

/** How far a spark falls by the end of its life, as a fraction of its reach. */
private const val GRAVITY = 0.55f

/**
 * A burst of sparks, thrown out and falling back. Drawn from the same glowing dots as the
 * breath cue, so it stays the app's own celebration rather than a stock confetti shower —
 * but thrown rather than breathed, which is the difference between calm and festive.
 *
 * Each spark runs on its own offset into the same clock, so bursts overlap into something
 * continuous instead of the whole sky flashing at once.
 */
@Composable
private fun Fireworks(glow: Color, modifier: Modifier = Modifier) {
    val sparks = remember {
        // seeded: a fixed spread looks composed, where a fresh random one clumps and gaps
        val rnd = Random(7)
        List(SPARKS) {
            Spark(
                angle = rnd.nextFloat() * 2f * PI.toFloat(),
                reach = 0.45f + rnd.nextFloat() * 0.55f,
                offset = rnd.nextFloat(),
                size = 1.5f + rnd.nextFloat() * 3f,
                pale = rnd.nextFloat(),
            )
        }
    }
    val clock = rememberInfiniteTransition(label = "fireworks")
    val t by clock.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing)),
        label = "burst",
    )

    Canvas(modifier) {
        val max = minOf(size.width, size.height) / 2f
        sparks.forEach { spark ->
            val life = (t + spark.offset) % 1f
            // fast out of the gate and slowing, the way anything thrown does
            val flight = sqrt(life) * spark.reach * max
            val fall = GRAVITY * life * life * spark.reach * max
            // a spark is brightest just after it leaves, then goes out over the rest of its life
            val alpha = (1f - life) * minOf(1f, life * 12f)
            drawCircle(
                color = lerp(glow, Color.White, spark.pale * 0.6f).copy(alpha = alpha),
                radius = spark.size * (1f - life * 0.4f),
                center = center + Offset(
                    cos(spark.angle) * flight,
                    sin(spark.angle) * flight + fall,
                ),
            )
        }
    }
}

