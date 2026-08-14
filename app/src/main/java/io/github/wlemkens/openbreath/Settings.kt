package io.github.wlemkens.openbreath

import android.content.Intent
import android.graphics.Color as AndroidColor
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    config: Config,
    onChange: (Config) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val preset = config.active
    val dnd = remember { DndGuard(context) }
    val torch = remember { Torch(context) }

    fun editPreset(f: (Preset) -> Preset) {
        onChange(config.copy(presets = config.presets.toMutableList().also { it[config.activeIndex] = f(preset) }))
    }

    var renaming by remember { mutableStateOf(false) }
    var advanced by remember { mutableStateOf(false) }
    var pendingPhase by remember { mutableStateOf<Phase?>(null) }
    val pickMp3 = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        val phase = pendingPhase
        pendingPhase = null
        if (uri == null || phase == null) return@rememberLauncherForActivityResult
        // without a persisted grant the URI dies when the process does
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        editPreset { it.withSound(phase) { s -> s.copy(markerUri = uri.toString(), mode = SoundMode.MARKER) } }
    }

    LazyColumn(
        modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Settings", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                TextButton(onClick = onBack) { Text("Done") }
            }
        }
        item {
            // not persisted: which half you were last looking at is not a preference, and
            // opening on Standard is the right answer for anyone who did not come to tinker
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !advanced,
                    onClick = { advanced = false },
                    label = { Text("Standard") },
                )
                FilterChip(
                    selected = advanced,
                    onClick = { advanced = true },
                    label = { Text("Advanced") },
                )
            }
        }

        item { SectionLabel("Preset") }
        item {
            // FlowRow, not Row: the preset count and their names are both user data, so a
            // single line runs off the screen edge as soon as there are a few
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                config.presets.forEachIndexed { i, p ->
                    FilterChip(
                        selected = i == config.activeIndex,
                        onClick = { onChange(config.copy(activeIndex = i)) },
                        label = { Text(p.name) },
                    )
                }
            }
        }
        if (advanced) item {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AssistChip(onClick = { renaming = true }, label = { Text("Rename") })
                AssistChip(
                    onClick = {
                        val copy = preset.copy(name = "${preset.name} copy")
                        onChange(config.copy(presets = config.presets + copy, activeIndex = config.presets.size))
                    },
                    label = { Text("Duplicate") },
                )
                if (config.presets.size > 1) {
                    AssistChip(
                        onClick = {
                            onChange(
                                config.copy(
                                    presets = config.presets.filterIndexed { i, _ -> i != config.activeIndex },
                                    activeIndex = 0,
                                )
                            )
                        },
                        label = { Text("Delete") },
                    )
                }
            }
        }

        item { SectionLabel("Timing") }
        for (phase in Phase.entries) {
            item(key = "time-${phase.name}") {
                val isHold = phase == Phase.HOLD_IN || phase == Phase.HOLD_OUT
                SecondsSlider(
                    // both holds are labelled "Hold"; say which one
                    label = when (phase) {
                        Phase.HOLD_IN -> "Hold in"
                        Phase.HOLD_OUT -> "Hold out"
                        else -> phase.label
                    },
                    ms = preset.timing.durationOf(phase),
                    // a breath needs an actual inhale and exhale; holds may be skipped
                    minMs = if (isHold) 0 else 1000,
                    maxMs = 20_000,
                    onChange = { editPreset { p -> p.withDuration(phase, it) } },
                )
            }
        }
        item {
            Text(
                "One breath: %.1f s  •  %.1f breaths per minute".format(
                    preset.timing.cycleMs / 1000f,
                    60_000f / preset.timing.cycleMs,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (advanced) item { SectionLabel("Sound per phase") }
        if (advanced) for (phase in Phase.entries) {
            item(key = "sound-${phase.name}") {
                val sound = preset.soundOf(phase)
                Column(Modifier.padding(vertical = 4.dp)) {
                    Text(
                        when (phase) {
                            Phase.HOLD_IN -> "Hold in"
                            Phase.HOLD_OUT -> "Hold out"
                            else -> phase.label
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    // four modes no longer fit a line on a narrow phone
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SoundMode.entries.forEach { mode ->
                            FilterChip(
                                selected = sound.mode == mode,
                                onClick = { editPreset { it.withSound(phase) { s -> s.copy(mode = mode) } } },
                                label = { Text(mode.label) },
                            )
                        }
                    }
                    if (sound.mode == SoundMode.AMBIENT) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AmbientVoice.entries.forEach { voice ->
                                FilterChip(
                                    selected = sound.voice == voice,
                                    onClick = {
                                        editPreset { it.withSound(phase) { s -> s.copy(voice = voice) } }
                                    },
                                    label = { Text(voice.label) },
                                )
                            }
                        }
                    }
                    if (sound.mode == SoundMode.MARKER) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = {
                                pendingPhase = phase
                                pickMp3.launch(arrayOf("audio/*"))
                            }) { Text(if (sound.markerUri == null) "Choose mp3" else "Change") }
                            Text(
                                sound.markerUri?.let { context.displayName(Uri.parse(it)) }
                                    ?: "built-in ${sound.tone.label.lowercase()}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            if (sound.markerUri != null) {
                                TextButton(onClick = {
                                    editPreset { it.withSound(phase) { s -> s.copy(markerUri = null) } }
                                }) { Text("Clear") }
                            }
                        }
                        // only meaningful while no mp3 of their own is standing in for it
                        if (sound.markerUri == null) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                MarkerTone.entries.forEach { tone ->
                                    FilterChip(
                                        selected = sound.tone == tone,
                                        onClick = {
                                            editPreset { it.withSound(phase) { s -> s.copy(tone = tone) } }
                                        },
                                        label = { Text(tone.label) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item { SectionLabel("Session length") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !config.limitIsCycles,
                    onClick = { onChange(config.copy(limitIsCycles = false)) },
                    label = { Text("By time") },
                )
                FilterChip(
                    selected = config.limitIsCycles,
                    onClick = { onChange(config.copy(limitIsCycles = true)) },
                    label = { Text("By breaths") },
                )
            }
        }
        item {
            if (config.limitIsCycles) {
                LabelledSlider(
                    label = "Breaths",
                    value = config.cycles.toFloat(),
                    range = 1f..100f,
                    steps = 98,
                    readout = "${config.cycles}",
                    onChange = { onChange(config.copy(cycles = it.roundToInt())) },
                )
            } else {
                LabelledSlider(
                    label = "Minutes",
                    value = config.durationMs / 60_000f,
                    range = 1f..60f,
                    steps = 58,
                    readout = "${(config.durationMs / 60_000L)} min",
                    onChange = { onChange(config.copy(durationMs = (it.roundToInt() * 60_000L))) },
                )
            }
            val total = config.limit.totalMs(preset.timing)
            Text(
                "Rounds up to whole breaths: %d:%02d, %d breaths".format(
                    total / 60_000, total / 1000 % 60, total / preset.timing.cycleMs,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        item { SectionLabel("During a session") }
        item {
            ToggleRow("Vibrate at each phase change", config.vibrate) {
                onChange(config.copy(vibrate = it))
            }
        }
        // a switch for hardware the phone hasn't got is just a puzzle; the setting stays stored
        // either way, so a phone that does have one still finds it turned on
        if (torch.available) {
            item {
                ToggleRow("Flashlight follows the breath", config.flashlight) {
                    onChange(config.copy(flashlight = it))
                }
            }
        }
        item {
            ToggleRow("Bowl at the end of the session", config.endSound) {
                onChange(config.copy(endSound = it))
            }
        }
        item {
            ToggleRow("Silence notifications", config.muteNotifications) {
                onChange(config.copy(muteNotifications = it))
            }
            if (config.muteNotifications && !dnd.granted) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Needs Do Not Disturb access",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    TextButton(onClick = { context.startActivity(notificationPolicyIntent()) }) {
                        Text("Grant")
                    }
                }
            }
        }

        item { SectionLabel("Breath cue") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CueStyle.entries.forEach { style ->
                    FilterChip(
                        selected = config.cue == style,
                        onClick = { onChange(config.copy(cue = style)) },
                        label = { Text(style.label) },
                    )
                }
            }
        }
        if (advanced) item { CueColour(config.cueColor) { onChange(config.copy(cueColor = it)) } }

        item { SectionLabel("Progress on screen") }
        item {
            ToggleRow("Dots", config.showDots) { onChange(config.copy(showDots = it)) }
        }
        // a clock and a tally are the two things a meditation is better off not counting for
        // you, so they are here for whoever wants them rather than in front of everyone
        if (advanced) item {
            ToggleRow("Time remaining", config.showTime) { onChange(config.copy(showTime = it)) }
        }
        if (advanced) item {
            ToggleRow("Breath count", config.showBreaths) { onChange(config.copy(showBreaths = it)) }
        }
        item { HorizontalDivider(Modifier.padding(vertical = 24.dp)) }
    }

    if (renaming) {
        var draft by remember { mutableStateOf(preset.name) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("Preset name") },
            text = {
                OutlinedTextField(value = draft, onValueChange = { draft = it }, singleLine = true)
            },
            confirmButton = {
                Button(onClick = {
                    val name = draft.trim().ifEmpty { preset.name }
                    editPreset { it.copy(name = name) }
                    renaming = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("Cancel") } },
        )
    }
}

@Composable
internal fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 20.dp, bottom = 4.dp),
    )
}

/** Half-second granularity: finer than that is not something a breath can follow. */
@Composable
private fun SecondsSlider(label: String, ms: Int, minMs: Int, maxMs: Int, onChange: (Int) -> Unit) {
    LabelledSlider(
        label = label,
        value = ms / 500f,
        range = (minMs / 500f)..(maxMs / 500f),
        steps = (maxMs - minMs) / 500 - 1,
        readout = "%.1f s".format(ms / 1000f),
        onChange = { onChange(it.roundToInt() * 500) },
    )
}

@Composable
internal fun LabelledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    readout: String,
    onChangeFinished: (() -> Unit)? = null,
    onChange: (Float) -> Unit,
) {
    Column {
        Row {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(readout, style = MaterialTheme.typography.bodyMedium)
        }
        Slider(
            value = value,
            onValueChange = onChange,
            onValueChangeFinished = onChangeFinished,
            valueRange = range,
            steps = steps.coerceAtLeast(0),
        )
    }
}

/**
 * The bright end of the breath cue, and the colour of the progress dots. Hue/saturation/brightness
 * rather than three RGB sliders: picking a colour by eye means moving one thing at a time, and on
 * RGB every axis drags the other two along with it.
 */
@Composable
private fun CueColour(argb: Int, onChange: (Int) -> Unit) {
    // Seeded once. Nothing outside this row edits the stored colour while it is on screen, and
    // reading it back each time would snap a fully desaturated colour's hue to red, since grey
    // has no hue to recover.
    val seed = remember { FloatArray(3).also { AndroidColor.colorToHSV(argb, it) } }
    var h by remember { mutableFloatStateOf(seed[0]) }
    var s by remember { mutableFloatStateOf(seed[1]) }
    var v by remember { mutableFloatStateOf(seed[2]) }
    val picked = AndroidColor.HSVToColor(floatArrayOf(h, s, v))
    // on release, not on every frame of the drag: the cue is not on this screen to follow along,
    // and a slider at 60fps would be 60 DataStore writes a second
    val commit = { onChange(picked) }

    Column {
        Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(28.dp).clip(CircleShape).background(Color(picked)))
            Text(
                "Cue and dots",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f).padding(start = 12.dp),
            )
            TextButton(onClick = {
                AndroidColor.colorToHSV(DEFAULT_CUE_COLOR, seed)
                h = seed[0]; s = seed[1]; v = seed[2]
                onChange(DEFAULT_CUE_COLOR)
            }) { Text("Reset") }
        }
        LabelledSlider("Hue", h, 0f..360f, 0, "%.0f°".format(h), commit) { h = it }
        LabelledSlider("Saturation", s, 0f..1f, 0, "%.0f%%".format(s * 100), commit) { s = it }
        LabelledSlider("Brightness", v, 0f..1f, 0, "%.0f%%".format(v * 100), commit) { v = it }
    }
}

@Composable
internal fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
