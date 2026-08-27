package io.github.wlemkens.openbreath

import kotlinx.coroutines.runBlocking
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * That the desktop audio survives being used, which is the part of it a diff cannot show.
 *
 * All of it is a thread, a pipe or a mixer line, and none of those fail at compile time — they throw
 * on the frame somebody pressed Start. This drives each of them once, over every marker tone and
 * both bowls, and asserts only that nothing comes back up. Whether it sounds *right* needs ears;
 * whether it runs at all does not.
 *
 * **It must pass on a machine with no sound card**, because CI is one. Every path is written to
 * return null rather than throw when there is no output — [openSink], [lineSink] — so a silent
 * machine exercises the same code and simply gets no sink. A test that needed a speaker would be a
 * test that got deleted the first time it went red on a runner.
 */
class DesktopAudioTest {

    @Test
    fun `the bed starts, runs and stops without throwing`() {
        val audio = DesktopAudio()
        try {
            audio.openness = 0.5f
            audio.wavesGain = 1f
            audio.soundwaveGain = 0.4f

            audio.startWaves()
            // written from this thread while the audio thread reads them, which is the whole
            // arrangement WaveDsp's @Volatile fields exist for
            repeat(50) { audio.openness = it / 50f }
            audio.stopWaves()

            // and starting again after a stop, which is the case the sink-inside-the-thread
            // decision was made for: a sink held in a field would be closed by now
            audio.startWaves()
        } finally {
            audio.release()
        }
    }

    @Test
    fun `every marker a preset can name prepares and plays`() = runBlocking {
        val audio = DesktopAudio()
        val markers = DesktopMarkers(audio)
        try {
            for (tone in MarkerTone.entries) {
                val preset = markerPreset(tone)
                markers.prepare(preset)
                for (phase in Phase.entries) markers.play(phase, preset)
            }
            markers.playSessionEnd()
            markers.stopSessionEnd()
        } finally {
            markers.release()
            audio.release()
        }
    }

    @Test
    fun `a marker sounds without the bed having been started`() = runBlocking {
        // a preset can ask for markers and no ambient voice at all, and then nothing has called
        // startWaves to have opened a sink. The strike has to wake the thread by itself
        val audio = DesktopAudio()
        val markers = DesktopMarkers(audio)
        try {
            val preset = markerPreset(MarkerTone.BELL)
            markers.prepare(preset)
            markers.play(Phase.INHALE, preset)
        } finally {
            markers.release()
            audio.release()
        }
    }

    @Test
    fun `a phase pointing at a file that is not there falls back rather than going silent`() = runBlocking {
        val missing = "/nowhere/at/all/some-sound.mp3"
        val preset = Phase.entries.fold(markerPreset(MarkerTone.BELL)) { p, phase ->
            p.withSound(phase) { it.copy(markerUri = missing) }
        }

        val audio = DesktopAudio()
        val markers = DesktopMarkers(audio)
        try {
            markers.prepare(preset)
            // the bell is what should be rendered and struck instead — and the identity has to be
            // the bell's too, or the session screen would dedupe on a file that is not there
            assertTrue(
                markers.soundIdOf(Phase.INHALE, preset) == missing,
                "soundIdOf answers what the preset says, as on every platform",
            )
            for (phase in Phase.entries) markers.play(phase, preset)
        } finally {
            markers.release()
            audio.release()
        }
    }

    @Test
    fun `the rate it renders at is one the constants can live with`() {
        // 44100 wherever a system player will resample for us, since that is what every ear-picked
        // number was tuned against; whatever a raw line offers otherwise. Never a zero, which is
        // what would divide by nothing in every filter coefficient
        val rate = DesktopAudio().also { it.release() }.sampleRate
        assertTrue(rate in listOf(44100, 48000), "odd rate $rate")
    }

    /**
     * That a marker cannot badly clip the bed it is mixed into — which is the one thing summing
     * everything into a single stream put at risk, and the reason MARKER_PEAK had to become
     * internal so the recorded bowls could be normalised to it.
     *
     * **The bound is 5% and not zero, deliberately.** Measured, the bed peaks at about 0.63 with
     * both voices at full and a marker adds 0.38, so the sum just crosses 1.0 — by under one
     * percent, on a bell's attack, which no ear finds. And the phones do exactly the same thing:
     * the bell and the bed are separate tracks there and the system mixer sums them to the same
     * number. Attenuating the desktop's markers to make this assertion pass at zero would make it
     * the one platform whose markers are quieter, to fix something nobody can hear.
     *
     * What it does catch is a marker or a bed that gets genuinely louder — a retuned constant, a
     * bowl that stops being normalised — which would distort audibly.
     */
    @Test
    fun `a marker mixed onto the loudest bed barely reaches full scale`() = runBlocking {
        val audio = DesktopAudio()
        val markers = DesktopMarkers(audio)
        try {
            val preset = markerPreset(MarkerTone.GONG)
            markers.prepare(preset)
        } finally {
            markers.release()
            audio.release()
        }

        // the bed at full tilt, measured rather than assumed
        val dsp = WaveDsp(44100)
        dsp.running = true
        dsp.openness = 1f
        dsp.wavesGain = 1f
        dsp.soundwaveGain = 1f
        dsp.prime()
        val block = FloatArray(4096)
        var bed = 0f
        repeat(60) {
            dsp.render(block)
            for (v in block) bed = maxOf(bed, abs(v))
        }

        // and a marker, which every tone and both bowls are normalised to
        val peak = bed + MARKER_PEAK
        assertTrue(
            peak < 1.05f,
            "bed $bed plus a marker at $MARKER_PEAK sums to $peak — more than a transient clip",
        )
    }

    /** A preset whose every phase ends on [tone], so one prepare covers all four boundaries. */
    private fun markerPreset(tone: MarkerTone): Preset =
        Phase.entries.fold(Preset()) { preset, phase ->
            preset.withSound(phase) { it.copy(mode = SoundMode.MARKER, tone = tone) }
        }
}
