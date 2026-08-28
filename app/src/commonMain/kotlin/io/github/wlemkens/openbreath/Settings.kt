package io.github.wlemkens.openbreath

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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    config: Config,
    onChange: (Config) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val platform = LocalPlatform.current
    val files = platform.files
    val store = LocalStore.current
    val scope = rememberCoroutineScope()
    val preset = config.active
    val dnd = platform.focus
    val torch = platform.torch

    fun editPreset(f: (Preset) -> Preset) {
        onChange(config.copy(presets = config.presets.toMutableList().also { it[config.activeIndex] = f(preset) }))
    }

    val advanced = config.advancedSettings
    var renaming by remember { mutableStateOf(false) }
    var pendingPhase by remember { mutableStateOf<Phase?>(null) }
    fun pickMp3(phase: Phase) = files.pickAudio { handle ->
        if (handle != null) {
            editPreset { it.withSound(phase) { s -> s.copy(markerUri = handle, mode = SoundMode.MARKER) } }
        }
    }

    // what the import found, held until it has been agreed to: an import overwrites settings
    // and can never be undone from inside the app, so nothing is applied on the file picker's
    // say-so alone
    var pendingImport by remember { mutableStateOf<Backup?>(null) }
    var backupMessage by remember { mutableStateOf<String?>(null) }

    // the file is written before it is offered anywhere to put it, because reading the store is
    // the part that suspends and a picker callback is not a place to start a coroutine
    fun exportBackup() = scope.launch {
        val backup = store.exportBackup(Clock.System.now().toEpochMilliseconds())
        files.exportText(backupFileName(), encodeBackup(backup)) { ok ->
            backupMessage =
                if (ok) "Saved ${backup.summary}." else "Could not write that file. Nothing has changed."
        }
    }

    fun importBackup() = files.importText { text ->
        // a cancelled picker says nothing at all; a file that opens but is not a backup says so.
        // Telling those apart matters — one is a decision, the other is a mistake to correct
        if (text != null) {
            pendingImport = decodeBackup(text)
            if (pendingImport == null) backupMessage = "That is not an OpenBreath backup."
        }
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
            // remembered, so the screen opens where it was left: someone who works in Advanced
            // should not have to find it again every time
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !advanced,
                    onClick = { onChange(config.copy(advancedSettings = false)) },
                    label = { Text("Standard") },
                )
                FilterChip(
                    selected = advanced,
                    onClick = { onChange(config.copy(advancedSettings = true)) },
                    label = { Text("Advanced") },
                )
            }
        }

        // Advanced: Standard shows the Timing sliders alone, which edit whichever preset is
        // active — "Custom" until someone picks another here
        if (advanced) item { SectionLabel("Preset") }
        if (advanced) item {
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
        item {
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
                "One breath: ${formatOneDecimal(preset.timing.cycleMs / 1000f)} s" +
                    "  •  ${formatOneDecimal(60_000f / preset.timing.cycleMs)} breaths per minute",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (advanced) item {
            // in Timing rather than a section of its own: it is the one length here that is not
            // breathed, and it is still a length of the thing you just pressed Start on
            SecondsSlider(
                label = "Get ready",
                ms = config.leadInMs,
                minMs = 0,
                maxMs = 20_000,
                onChange = { onChange(config.copy(leadInMs = it)) },
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
                            if (files.canPickAudio) {
                                TextButton(onClick = {
                                    pickMp3(phase)
                                }) { Text(if (sound.markerUri == null) "Choose mp3" else "Change") }
                            }
                            Text(
                                sound.markerUri?.let { files.audioName(it) }
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
                    onStep = { dir -> onChange(config.copy(cycles = (config.cycles + dir).coerceIn(1, 100))) },
                    onChange = { onChange(config.copy(cycles = it.roundToInt())) },
                )
            } else {
                LabelledSlider(
                    label = "Minutes",
                    value = config.durationMs / 60_000f,
                    range = 1f..60f,
                    steps = 58,
                    readout = "${(config.durationMs / 60_000L)} min",
                    onStep = { dir ->
                        val min = (config.durationMs / 60_000L + dir).coerceIn(1L, 60L)
                        onChange(config.copy(durationMs = min * 60_000L))
                    },
                    onChange = { onChange(config.copy(durationMs = (it.roundToInt() * 60_000L))) },
                )
            }
            val total = config.limit.totalMs(preset.timing)
            Text(
                "Rounds up to whole breaths: ${total / 60_000}:" +
                    "${(total / 1000 % 60).toString().padStart(2, '0')}, " +
                    "${total / preset.timing.cycleMs} breaths",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        item { SectionLabel("During a session") }
        // a switch for hardware the machine hasn't got is just a puzzle; the setting stays stored
        // either way, so a phone that does have one still finds it turned on
        if (platform.haptics.supported) {
            item {
                ToggleRow("Vibrate at each phase change", config.vibrate) {
                    onChange(config.copy(vibrate = it))
                }
            }
        }
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
            if (dnd.supported) {
                ToggleRow("Silence notifications", config.muteNotifications) {
                    onChange(config.copy(muteNotifications = it))
                }
            }
            if (dnd.supported && config.muteNotifications && !dnd.granted) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Needs Do Not Disturb access",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    TextButton(onClick = { dnd.requestAccess() }) { Text("Grant") }
                }
            }
        }

        if (advanced) item { SectionLabel("Breath cue") }
        if (advanced) item {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
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
        if (advanced) item {
            ToggleRow("Vivid", config.vividCue) { onChange(config.copy(vividCue = it)) }
            Text(
                "The same hue with the grey taken out, past what the sliders can reach.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

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
        // Advanced: moving a log between phones is a thing you go looking for, and Import is
        // the one control here that can overwrite what you already have. Neither belongs in
        // front of someone who opened Settings to lengthen their exhale.
        if (advanced) item { SectionLabel("Backup") }
        if (advanced) item {
            Text(
                "Your practice log, presets, goals and reminders in one file. The only way to " +
                    "carry them to another phone, and yours to keep.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (advanced) item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { exportBackup() }) { Text("Export") }
                // application/json alone hides backups on the phones whose file picker types
                // them octet-stream, which is most of them once a file has been off the device
                TextButton(onClick = { importBackup() }) { Text("Import") }
            }
        }
        // last, and only under Advanced: it is here to be quoted in a bug report, not read
        if (advanced) item { SectionLabel("This build") }
        if (advanced) item {
            Text(
                // the commit is the part that actually answers "which build is this": a version
                // says what was intended, a sha says what the binary was made of
                "OpenBreath ${platform.version} · $BUILD_SHA",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item { HorizontalDivider(Modifier.padding(vertical = 24.dp)) }
    }

    pendingImport?.let { backup ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text("Import this backup?") },
            text = {
                Text(
                    "It holds ${backup.summary}.\n\nSittings are added to the ones already " +
                        "here — none are lost, whichever phone they were breathed on. Presets, " +
                        "goals and reminders are replaced by the ones in the file."
                )
            },
            confirmButton = {
                Button(onClick = {
                    pendingImport = null
                    scope.launch {
                        backupMessage = runCatching {
                            store.importBackup(backup)
                            "Imported."
                        }.getOrElse { "Could not read that backup. Nothing has changed." }
                    }
                }) { Text("Import") }
            },
            dismissButton = { TextButton(onClick = { pendingImport = null }) { Text("Cancel") } },
        )
    }

    backupMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { backupMessage = null },
            title = { Text("Backup") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { backupMessage = null }) { Text("OK") } },
        )
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

/** Half-second granularity: finer than that is not something a breath can follow. */
@Composable
private fun SecondsSlider(label: String, ms: Int, minMs: Int, maxMs: Int, onChange: (Int) -> Unit) {
    LabelledSlider(
        label = label,
        value = ms / 500f,
        range = (minMs / 500f)..(maxMs / 500f),
        steps = (maxMs - minMs) / 500 - 1,
        readout = "${formatOneDecimal(ms / 1000f)} s",
        onStep = { dir -> onChange((ms + dir * 500).coerceIn(minMs, maxMs)) },
        onChange = { onChange(it.roundToInt() * 500) },
    )
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
    val seed = remember { argbToHsv(argb) }
    var h by remember { mutableFloatStateOf(seed[0]) }
    var s by remember { mutableFloatStateOf(seed[1]) }
    var v by remember { mutableFloatStateOf(seed[2]) }
    val picked = hsvToArgb(h, s, v)
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
                val reset = argbToHsv(DEFAULT_CUE_COLOR)
                h = reset[0]; s = reset[1]; v = reset[2]
                onChange(DEFAULT_CUE_COLOR)
            }) { Text("Reset") }
        }
        LabelledSlider("Hue", h, 0f..360f, 0, "${h.roundToInt()}°", commit) { h = it }
        LabelledSlider("Saturation", s, 0f..1f, 0, "${(s * 100).roundToInt()}%", commit) { s = it }
        LabelledSlider("Brightness", v, 0f..1f, 0, "${(v * 100).roundToInt()}%", commit) { v = it }
    }
}

@Composable
internal fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
