package io.github.wlemkens.openbreath

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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Clock

/**
 * Near-black rather than black: an OLED-black background makes the cue's glow edge visible as a
 * ring where the gradient stops.
 */
private val Ink = Color(0xFF07090F)

/**
 * The look, shared by both platforms so neither drifts.
 *
 * The navigator that sits inside this is still per-platform: it reaches Settings, Log, Reminders
 * and Support, which are Android's until they are ported, and offering an iPhone a menu item that
 * opens nothing would be worse than offering it fewer.
 */
@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(background = Ink, surface = Ink)) {
        Surface(Modifier.fillMaxSize()) { content() }
    }
}

@Composable
fun SessionScreen(
    config: Config,
    onOpenGoals: () -> Unit,
    onOpenAchievements: () -> Unit,
    // null where the platform has no such screen yet: the menu leaves the item out rather than
    // offering one that does nothing, which is the same rule the DND setting follows
    onOpenSettings: (() -> Unit)? = null,
    onOpenLog: (() -> Unit)? = null,
    onOpenReminders: (() -> Unit)? = null,
    onOpenSupport: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val store = LocalStore.current
    val platform = LocalPlatform.current
    val preset = config.active
    val timing = preset.timing
    val total = remember(config) { config.limit.totalMs(timing) }
    val glow = Color(config.cueColor).let { if (config.vividCue) it.vivid() else it }

    // one lifetime, one teardown: see SessionServices for why these are grouped
    val session = remember(platform) { platform.session() }
    val synth = session.synth
    val markers = session.markers
    val dnd = session.focus
    val torch = session.torch

    var running by remember { mutableStateOf(false) }
    var elapsed by remember { mutableLongStateOf(0L) }

    // when this sitting began, which is what the log entry is keyed on: pausing and carrying on
    // rewrites the same entry rather than adding a second one
    var startedAt by remember { mutableLongStateOf(0L) }

    // a session that outlives its screen would leave audio playing, the torch burning and the
    // phone in DND
    DisposableEffect(session) {
        onDispose { session.release() }
    }

    // foreground only, by design: leaving the app pauses rather than playing on in the background
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { running = false }

    DisposableEffect(running) {
        platform.screen.keepAwake(running)
        onDispose { platform.screen.keepAwake(false) }
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
        if (base == 0L) startedAt = Clock.System.now().toEpochMilliseconds()
        var prev = phaseAt(base, timing)
        // read back in the finally instead of `elapsed`, which Reset clears in the same event
        // that stops the session — the breaths taken still happened
        var breathed = base

        fun boundaryCrossed(ended: List<Phase>) {
            // one buzz per boundary, however many zero-length phases ended on it
            if (config.vibrate) platform.haptics.buzz()
            ended.filter { preset.soundOf(it).mode == SoundMode.MARKER }
                // two phases ending on the same instant must not play the same sound twice
                .distinctBy { markers.soundIdOf(it, preset) }
                .forEach { markers.play(it, preset) }
        }

        val t0 = withFrameNanos { it }
        // a phase asks for one continuous voice or none; the other stays at zero rather than
        // being switched off, so the boundary between two different voices is a crossfade
        fun levelOf(voice: AmbientVoice, state: PhaseState) =
            preset.soundOf(state.phase).let {
                if (it.mode == SoundMode.AMBIENT && it.voice == voice) state.edge else 0f
            }

        fun follow(state: PhaseState) {
            synth.openness = state.openness
            synth.wavesGain = levelOf(AmbientVoice.WAVES, state)
            synth.soundwaveGain = levelOf(AmbientVoice.SOUNDWAVE, state)
        }

        follow(prev)
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
                        if (config.vibrate) platform.haptics.buzz()
                        markers.playSessionEnd()
                    } else {
                        // the final phase completed, plus any zero-length phase trailing it
                        val opens = phaseAt(total, timing).phase
                        boundaryCrossed(phasesEndingBetween(prev.phase, opens, timing))
                    }
                    elapsed = total
                    breathed = total
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
                breathed = e
                follow(state)
                if (config.flashlight) torch.follow(state)
            }
        } finally {
            synth.stop()
            dnd.restore()
            torch.off()
            // pausing and leaving the screen both cancel this coroutine, and a cancelled one
            // cannot suspend — without NonCancellable every sitting but a completed one is lost
            withContext(NonCancellable) { store.logSession(startedAt, breathed, preset) }
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
            if (!running) {
                var menuOpen by remember { mutableStateOf(false) }
                // Box, so the menu hangs off the button rather than the top of the screen
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        // the glyph rather than material-icons: a whole dependency for three dots
                        Text("⋮", style = MaterialTheme.typography.titleLarge)
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        if (onOpenSettings != null) {
                            DropdownMenuItem(
                                text = { Text("Settings") },
                                onClick = { menuOpen = false; onOpenSettings() },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Goals") },
                            onClick = { menuOpen = false; onOpenGoals() },
                        )
                        if (onOpenReminders != null) {
                            DropdownMenuItem(
                                text = { Text("Reminders") },
                                onClick = { menuOpen = false; onOpenReminders() },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Achievements") },
                            onClick = { menuOpen = false; onOpenAchievements() },
                        )
                        if (onOpenLog != null) {
                            DropdownMenuItem(
                                text = { Text("Log") },
                                onClick = { menuOpen = false; onOpenLog() },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Feedback") },
                            onClick = { menuOpen = false; platform.links.openFeedback() },
                        )
                        if (onOpenSupport != null) {
                            DropdownMenuItem(
                                text = { Text("Support") },
                                onClick = { menuOpen = false; onOpenSupport() },
                            )
                        }
                        if (platform.links.canRate) {
                            DropdownMenuItem(
                                text = { Text("Rate") },
                                onClick = { menuOpen = false; platform.links.openRating() },
                            )
                        }
                    }
                }
            }
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

internal fun mmss(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0L)
    // digits and a colon, so unlike formatOneDecimal there is nothing here a locale has an
    // opinion about — String.format was only ever the JVM's way of padding to two
    return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
}
