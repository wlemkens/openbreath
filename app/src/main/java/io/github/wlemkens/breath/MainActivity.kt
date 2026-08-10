package io.github.wlemkens.breath

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.launch

private val Ink = Color(0xFF07090F)

/** The far end of the cue's gradient. Fixed; only the bright end is the user's to pick. */
internal val Deep = Color(0xFF1E4B78)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(background = Ink, surface = Ink)) {
                Surface(Modifier.fillMaxSize()) {
                    Breath(Modifier.safeDrawingPadding())
                }
            }
        }
    }
}

@Composable
private fun Breath(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val config by remember { context.configFlow() }.collectAsState(initial = null)
    var showSettings by remember { mutableStateOf(false) }

    val current = config ?: return // first frame, before DataStore has read

    if (showSettings) {
        SettingsScreen(
            config = current,
            onChange = { scope.launch { context.saveConfig(it) } },
            onBack = { showSettings = false },
            modifier = modifier,
        )
    } else {
        SessionScreen(current, onOpenSettings = { showSettings = true }, modifier = modifier)
    }
}

@Composable
fun SessionScreen(config: Config, onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val view = LocalView.current
    val preset = config.active
    val timing = preset.timing
    val total = remember(config) { config.limit.totalMs(timing) }
    val glow = Color(config.cueColor)

    val synth = remember { WaveSynth() }
    val markers = remember { PhaseMarkers(context) }
    val dnd = remember { DndGuard(context) }

    var running by remember { mutableStateOf(false) }
    var elapsed by remember { mutableLongStateOf(0L) }

    // a session that outlives its screen would leave audio playing and the phone in DND
    DisposableEffect(Unit) {
        onDispose {
            synth.stop()
            markers.release()
            dnd.restore()
        }
    }

    // foreground only, by design: leaving the app pauses rather than playing on in the background
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { running = false }

    DisposableEffect(running) {
        view.keepScreenOn = running
        onDispose { view.keepScreenOn = false }
    }

    LaunchedEffect(running) {
        if (!running) {
            dnd.restore()
            return@LaunchedEffect
        }
        if (config.muteNotifications) dnd.silence()
        markers.prepare(preset)
        // starting again while the last session's bowl is still ringing would play the first
        // breath over its tail
        markers.stopSessionEnd()

        val base = elapsed
        var prev = phaseAt(base, timing)

        fun boundaryCrossed(ended: List<Phase>) {
            // one buzz per boundary, however many zero-length phases ended on it
            if (config.vibrate) context.buzz()
            ended.filter { preset.soundOf(it).mode == SoundMode.MARKER }
                // two phases ending on the same instant must not play the same sound twice
                .distinctBy { markers.soundIdOf(it, preset) }
                .forEach { markers.play(it, preset) }
        }

        val t0 = withFrameNanos { it }
        fun wavesLevel(state: PhaseState) =
            if (preset.soundOf(state.phase).mode == SoundMode.WAVES) state.edge else 0f

        prev.let {
            synth.openness = it.openness
            synth.phaseGain = wavesLevel(it)
        }
        synth.start()
        try {
            while (true) {
                val now = withFrameNanos { it }
                val e = base + (now - t0) / 1_000_000
                if (e >= total) {
                    if (config.endSound) {
                        // the session ending is the larger event; the final phase's own marker
                        // on top of it would only muddy the moment. The buzz still belongs to
                        // the phase boundary, so it stays.
                        if (config.vibrate) context.buzz()
                        markers.playSessionEnd()
                    } else {
                        // the final phase completed, plus any zero-length phase trailing it
                        val opens = phaseAt(total, timing).phase
                        boundaryCrossed(phasesEndingBetween(prev.phase, opens, timing))
                    }
                    elapsed = total
                    running = false
                    break
                }
                val state = phaseAt(e, timing)
                // a cycle whose only non-zero phase is the inhale never changes phase, so the
                // cycle index is part of what makes a boundary
                if (state.phase != prev.phase || state.cycle != prev.cycle) {
                    boundaryCrossed(phasesEndingBetween(prev.phase, state.phase, timing))
                    prev = state
                }
                elapsed = e
                synth.openness = state.openness
                synth.phaseGain = wavesLevel(state)
            }
        } finally {
            synth.stop()
            dnd.restore()
        }
    }

    val finished = elapsed >= total
    val state = phaseAt(if (finished) 0L else elapsed, timing)
    val cycles = (total / timing.cycleMs).toInt()

    Column(
        modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            if (!running) TextButton(onClick = onOpenSettings) { Text("Settings") }
        }

        Text(
            when {
                finished -> "Done"
                running -> state.phase.label
                else -> preset.name
            },
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )

        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            Cue(config.cue, if (running) state.openness else 0f, glow)
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (config.showTime) {
                Text(mmss(total - elapsed), style = MaterialTheme.typography.displaySmall)
            }
            if (config.showDots) Dots(cycles, elapsed.toFloat() / total, glow)
            if (config.showBreaths) {
                Text(
                    "breath ${(state.cycle + 1).coerceAtMost(cycles)} of $cycles",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { if (finished) elapsed = 0L else running = !running }) {
                    Text(
                        when {
                            finished -> "Again"
                            running -> "Pause"
                            elapsed > 0L -> "Resume"
                            else -> "Start"
                        }
                    )
                }
                if (elapsed > 0L && !finished) {
                    TextButton(onClick = { running = false; elapsed = 0L }) { Text("Reset") }
                }
            }
        }
    }
}

/**
 * One dot per breath, filling left to right. Drawn rather than laid out so that the spacing
 * divides the width whatever the breath count is — a 60-breath session gets 60 dots, not a
 * row that runs off the edge.
 */
@Composable
private fun Dots(count: Int, progress: Float, glow: Color) {
    Canvas(Modifier.fillMaxWidth().height(16.dp)) {
        val step = size.width / count
        val r = minOf(step * 0.3f, 3.dp.toPx())
        repeat(count) { i ->
            // the dot for the breath in progress fades in as that breath is taken
            val fill = (progress * count - i).coerceIn(0f, 1f)
            drawCircle(glow.copy(alpha = 0.15f + 0.85f * fill), r, Offset(step * (i + 0.5f), size.height / 2))
        }
    }
}

private fun mmss(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0L)
    return "%d:%02d".format(s / 60, s % 60)
}
