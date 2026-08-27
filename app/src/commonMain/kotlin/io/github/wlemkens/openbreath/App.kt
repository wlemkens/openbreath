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

/** The look, shared by every platform so none of them drifts. */
@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(background = Ink, surface = Ink)) {
        Surface(Modifier.fillMaxSize()) { content() }
    }
}

/**
 * Which screen is showing — for every platform, now.
 *
 * This was the same fifty-line `when` written out in androidMain/MainActivity.kt and again in
 * iosMain/MainViewController.kt, and both of them said in a comment that they would collapse into
 * one when the screens ported. Six of the seven destinations are in commonMain, so they have; a
 * desktop would otherwise have been a third copy of it.
 *
 * Reminders is the exception and the only one, because AlarmManager and a BroadcastReceiver have
 * no counterpart anywhere else yet. Both of its parameters are null where the platform has none —
 * the same rule [SessionScreen]'s nullable `onOpen…` parameters already follow, and the rule
 * [FirstRunSetup] already followed for its scheduler: the menu leaves the item out rather than
 * offering one that opens nothing.
 */
@Composable
fun Breath(
    modifier: Modifier = Modifier,
    reminders: (@Composable (modifier: Modifier, onBack: () -> Unit) -> Unit)? = null,
    onReminder: ((Reminder) -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    val store = LocalStore.current
    val config by remember { store.configFlow() }.collectAsState(initial = null)
    val goals by remember { store.goalsFlow() }.collectAsState(initial = null)
    var showSettings by remember { mutableStateOf(false) }
    var showLog by remember { mutableStateOf(false) }
    var showReminders by remember { mutableStateOf(false) }
    var showGoals by remember { mutableStateOf(false) }
    var showAchievements by remember { mutableStateOf(false) }
    var showSupport by remember { mutableStateOf(false) }

    // first frames, before DataStore has read. An empty list here would be a lie the goal screen
    // could act on: saving a goal built on "no goals yet" writes over every goal there is
    val current = config ?: return
    val saved = goals ?: return

    // over whichever screen is showing: a milestone is worth interrupting a settings tweak for
    MilestoneWatch(current, saved)
    FirstRunSetup(onReminder = onReminder)

    when {
        showSettings -> SettingsScreen(
            config = current,
            onChange = { scope.launch { store.saveConfig(it) } },
            onBack = { showSettings = false },
            modifier = modifier,
        )

        showLog -> LogScreen(onBack = { showLog = false }, modifier = modifier)

        showGoals -> GoalsScreen(
            goals = saved,
            onChange = { scope.launch { store.saveGoals(it) } },
            onBack = { showGoals = false },
            modifier = modifier,
        )

        showAchievements ->
            AchievementsScreen(saved, onBack = { showAchievements = false }, modifier = modifier)

        showReminders && reminders != null -> reminders(modifier) { showReminders = false }

        showSupport -> SupportScreen(onBack = { showSupport = false }, modifier = modifier)

        else -> SessionScreen(
            current,
            onOpenGoals = { showGoals = true },
            onOpenAchievements = { showAchievements = true },
            onOpenSettings = { showSettings = true },
            onOpenLog = { showLog = true },
            onOpenReminders = reminders?.let { { showReminders = true } },
            onOpenSupport = { showSupport = true },
            modifier = modifier,
        )
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

    // ms left of the lead-in, 0 once it is over: the only thing that distinguishes "counting
    // down" from "breathing", since both run with `running` true
    var leadInLeft by remember { mutableLongStateOf(0L) }

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
            // pausing mid-count abandons the count: whatever is left of it is not a session
            leadInLeft = 0L
            dnd.restore()
            return@LaunchedEffect
        }
        if (config.muteNotifications) dnd.silence()
        markers.prepare(preset)
        // starting again while the last session's bowl is still ringing would play the first
        // breath over its tail
        markers.stopSessionEnd()

        val base = elapsed
        // only on a fresh start: resuming means the phone is already down and the eyes already
        // shut, which is the whole thing the count is for
        if (base == 0L && config.leadInMs > 0) {
            val c0 = withFrameNanos { it }
            do {
                leadInLeft = config.leadInMs - (withFrameNanos { it } - c0) / 1_000_000
            } while (leadInLeft > 0L)
            leadInLeft = 0L
        }
        // after the count, not before it: the sitting began when the breathing did
        if (base == 0L) startedAt = Clock.System.now().toEpochMilliseconds()
        var prev = phaseAt(base, timing)
        // read back in the finally instead of `elapsed`, which Reset clears in the same event
        // that stops the session — the breaths taken still happened
        var breathed = base

        /**
         * A marker belongs to the phase it opens, not the one it closes: it is there to tell you
         * what to breathe next, which is no use arriving after you were meant to start.
         */
        fun boundaryCrossed(starting: List<Phase>) {
            // one buzz per boundary, however many zero-length phases began on it
            if (config.vibrate) platform.haptics.buzz()
            starting.filter { preset.soundOf(it).mode == SoundMode.MARKER }
                // two phases beginning on the same instant must not play the same sound twice
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
        // the first phase is beginning, and beginnings are what markers are for. Only on a fresh
        // start: resuming from a pause lands mid-phase, where nothing is opening
        if (base == 0L) boundaryCrossed(listOf(prev.phase))
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
                        // no closing bowl, so the session still gets a sound of its own: the
                        // boundary it stopped on, treated like any other — the phase that would
                        // have opened next, plus any zero-length phase arriving with it
                        val opens = phaseAt(total, timing).phase
                        boundaryCrossed(phasesStartingBetween(prev.phase, opens, timing))
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
                    boundaryCrossed(phasesStartingBetween(prev.phase, state.phase, timing))
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
                                // "the app", because a menu item reading only "Support" is
                                // where someone goes to ask for help, not to offer any
                                text = { Text("Support the app") },
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

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                when {
                    finished -> "Done"
                    leadInLeft > 0L -> "Get ready"
                    running -> state.phase.label
                    else -> preset.name
                },
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
            // under the name it belongs to, and only while nothing is running: what a preset is
            // called says nothing about what it will do, and the readouts below are hideable —
            // so with them off there would otherwise be no way to see the sitting you set up
            if (leadInLeft > 0L) {
                // rounded up, so a 4 s count reads 4, 3, 2, 1 rather than starting at 3
                Text(
                    "${(leadInLeft + 999) / 1000}",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            } else if (!running && !finished) {
                Text(
                    "${preset.pattern} · ${mmss(total)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }

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
