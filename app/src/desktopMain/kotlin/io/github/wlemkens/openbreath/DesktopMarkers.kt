package io.github.wlemkens.openbreath

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The sound struck at the end of a phase, on the desktop.
 *
 * **This class holds no audio machinery at all any more.** It decodes, it resamples, it decides
 * which samples a phase wants — and hands them to [DesktopAudio], which is the session's one output
 * stream. It used to own a `Clip` per sound, and that was wrong twice over: a Clip picks its own
 * output device, which on Linux meant every marker went to an HDMI port, and ten scheduled volume
 * writes for the bowl's fade is a worse fade than a multiply.
 *
 * The bell and the tick come from commonMain/MarkerSynth.kt — the same numbers Android strikes and
 * iOS strikes. The pitching of the bowl comes from `gongRate`, the same 0.6 that reaches SoundPool
 * on Android and `AVAudioPlayer.rate` on iOS; here it reaches [resample], which is what those two
 * do internally.
 *
 * One class where iOS has two, because [decodeAudio] turns a recording into a FloatArray and after
 * that a bowl is played by exactly the code the bell is.
 */
class DesktopMarkers(private val audio: DesktopAudio) {

    /**
     * Every sound this preset can make, keyed by what identifies it: a pitch for the syntheses, a
     * rate for the bowl, a handle for a picked file. Rendered up front — synthesising or decoding on
     * the boundary itself would be the audible thing about it.
     */
    private val rendered = mutableMapOf<Any, FloatArray>()

    /** Recordings, decoded once and kept at their own rate until a pitch is asked for. */
    private var phaseBowl: Pcm? = null
    private var endBowl: Pcm? = null
    private val picked = mutableMapOf<String, Pcm>()

    /**
     * Decode and render everything the preset points at, before a boundary needs any of it.
     *
     * Off the composition thread, unlike the other two platforms: this is called from the session's
     * coroutine, which on the desktop runs on the UI dispatcher, and decoding two mp3s there is a
     * visible stall on the frame Start was pressed. iOS gets away with it because AVFoundation does
     * its decoding on threads of its own.
     */
    suspend fun prepare(preset: Preset): Unit = withContext(Dispatchers.Default) {
        if (phaseBowl == null) phaseBowl = bowlBytes(Bowl.PHASE)?.let { decodeMp3(it) }
        if (endBowl == null) endBowl = bowlBytes(Bowl.SESSION_END)?.let { decodeMp3(it) }
        if (phaseBowl == null) say("the phase bowl could not be decoded — check git-lfs")

        // before the pitches, because whether a picked file is really there decides whether the
        // phase needs a tone rendered as its fallback
        for (phase in Phase.entries) {
            val handle = preset.soundOf(phase).markerUri ?: continue
            if (handle in picked) continue
            val decoded = decodeAudio(File(handle))
            if (decoded == null) say("could not read the chosen sound $handle — using the tone")
            else picked[handle] = decoded
        }

        for (phase in Phase.entries) {
            val sound = preset.soundOf(phase)
            if (sound.mode != SoundMode.MARKER) continue
            val key = keyFor(phase, preset)
            if (key in rendered) continue
            rendered[key] = samplesFor(phase, preset)
        }

        // the end bowl is the session's rather than a phase's, so it is ready whatever the preset
        // says — the setting that turns it on is not this class's to read
        if (END !in rendered) endBowl?.let { rendered[END] = pitched(it, 1f) }
    }

    /**
     * Identity of the sound a phase would play, so that two phases ending on the same instant —
     * which is what a zero-length hold means — sound once rather than twice. The same identity
     * Android and iOS compute, minus the playing.
     */
    fun soundIdOf(phase: Phase, preset: Preset): Any =
        preset.soundOf(phase).let { it.markerUri ?: (it.tone to markerRate(it.tone, phase)) }

    fun play(phase: Phase, preset: Preset) {
        val samples = rendered[keyFor(phase, preset)] ?: return
        // only the bowl is faded: it rings for longer than a breath phase. A file of the reader's
        // own plays whole, because truncating their choice is not ours to do
        val fade = if (isBowl(phase, preset)) GONG_FADE_START_MS else null
        audio.strike(samples, fadeAfterMs = fade)
    }

    /** No fade: a session that is over may ring out, and the recording has one baked in. */
    fun playSessionEnd() {
        rendered[END]?.let { audio.strike(it, tag = END) }
    }

    /** So that starting again does not play the first breath over the last session's tail. */
    fun stopSessionEnd() = audio.silence(END)

    fun release() {
        rendered.clear()
        picked.clear()
    }

    /**
     * What a phase's sound is, as a cache key — and the one place the fallback rule lives.
     *
     * A picked file counts only when it is really there. A preset can name one this machine has
     * never had, since a backup carried from Android names `content://` URIs that mean nothing here
     * and one from an iPhone names a bare file name; and it can name one since deleted. When it is
     * gone the phase falls back to its tone, which is what all three platforms do — a boundary that
     * makes no sound at all reads as a broken app rather than as a missing file.
     */
    private fun keyFor(phase: Phase, preset: Preset): Any {
        val sound = preset.soundOf(phase)
        sound.markerUri?.takeIf { it in picked }?.let { return it }
        return when (sound.tone) {
            MarkerTone.GONG -> "bowl" to gongRate(phase)
            MarkerTone.BELL -> BELL_HZ
            MarkerTone.TICK -> TICK_HZ * tickRate(phase)
        }
    }

    private fun isBowl(phase: Phase, preset: Preset): Boolean {
        val sound = preset.soundOf(phase)
        return sound.markerUri?.takeIf { it in picked } == null && sound.tone == MarkerTone.GONG
    }

    private fun samplesFor(phase: Phase, preset: Preset): FloatArray {
        val sound = preset.soundOf(phase)
        picked[sound.markerUri]?.let { return pitched(it, 1f) }
        return when (sound.tone) {
            MarkerTone.GONG ->
                phaseBowl?.let { pitched(it, gongRate(phase)) } ?: FloatArray(0)
            MarkerTone.BELL -> struckMarker(BELL_HZ, audio.sampleRate)
            MarkerTone.TICK -> tickMarker(TICK_HZ * tickRate(phase), audio.sampleRate)
        }
    }

    /**
     * A recording at the stream's rate and the asked-for pitch, and **normalised to the same peak
     * the synthesised markers use**.
     *
     * The normalisation is new to the desktop and necessary here: everything is summed into one
     * stream now, and a bowl mastered near full scale landing on the loudest part of a wave would
     * clip. It also means every marker is the same loudness as every other, which the two phones
     * get for free from `MarkerSynth` and could not get for a recording.
     *
     * ponytail: that makes the bowl exactly as loud as the bell, where on Android SoundPool plays
     * the mp3 at its own level and the bowl is the louder of the two. If it wants to be louder,
     * that is one multiplier here — but it cannot exceed about 0.55 without clipping the bed.
     */
    private fun pitched(pcm: Pcm, pitch: Float): FloatArray =
        resample(pcm.samples, pcm.rate, audio.sampleRate, pitch).normalisedTo(MARKER_PEAK)

    private companion object {
        /** The session-end bowl's key, and the tag [stopSessionEnd] finds it by. */
        const val END = "session-end"
    }
}
