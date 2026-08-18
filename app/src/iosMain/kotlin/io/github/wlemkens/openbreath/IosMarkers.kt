package io.github.wlemkens.openbreath

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.get
import kotlinx.cinterop.set
import platform.AVFAudio.AVAudioEngine
import platform.AVFAudio.AVAudioFormat
import platform.AVFAudio.AVAudioPCMBuffer
import platform.AVFAudio.AVAudioPCMFormatFloat32
import platform.AVFAudio.AVAudioPlayerNode

/**
 * The sound struck at the end of a phase, on iOS.
 *
 * The bell and the tick are synthesised, and by commonMain/MarkerSynth.kt — the same buffer
 * Android strikes, from the same numbers. Only the playing of it is here.
 *
 * The two recorded bowls are [IosBowls]', not this class's: they are encoded mp3 and want a
 * player, where everything here is samples we generated and wants the engine. This class routes
 * to them and otherwise ignores them.
 *
 * A file of the reader's own is still owed, and is still silent rather than approximated with the
 * bell — someone would pick their own sound, hear a bell, and have no way to know which of the two
 * was the bug. It waits on the picker, which waits on decoding an arbitrary file.
 */
@OptIn(ExperimentalForeignApi::class)
class IosMarkers {
    private val engine = AVAudioEngine()

    // configures the session before asking, or the node answers 0 — see outputSampleRate
    private val sampleRate: Int = outputSampleRate(engine)

    private val format = AVAudioFormat(
        commonFormat = AVAudioPCMFormatFloat32,
        sampleRate = sampleRate.toDouble(),
        channels = 1u,
        interleaved = false,
    )

    /**
     * A few nodes in rotation, so a strike landing inside the previous one's tail does not cut it
     * off. A bell rings for 1.2 s and phases are seconds apart, but a zero-length hold puts two
     * boundaries close enough together to matter.
     */
    private val players = List(VOICES) { AVAudioPlayerNode() }
    private var next = 0

    /** Rendered up front and kept: synthesising on the boundary is the audible thing about it. */
    private val rendered = mutableMapOf<Float, AVAudioPCMBuffer>()
    private var started = false

    private val bowls = IosBowls()

    suspend fun prepare(preset: Preset) {
        start()
        bowls.prepare()
        for (phase in Phase.entries) {
            val hz = pitchOf(phase, preset) ?: continue
            rendered.getOrPut(hz) { buffer(samplesFor(hz, preset, phase)) }
        }
    }

    fun play(phase: Phase, preset: Preset) {
        val sound = preset.soundOf(phase)
        // a recording rather than a synthesis, so it takes the other road out of here entirely
        if (sound.markerUri == null && sound.tone == MarkerTone.GONG) {
            bowls.playPhase(phase)
            return
        }
        val hz = pitchOf(phase, preset) ?: return
        val buffer = rendered[hz] ?: return
        start()

        val player = players[next]
        next = (next + 1) % players.size
        player.stop()
        player.scheduleBuffer(buffer, null)
        player.play()
    }

    fun playSessionEnd() = bowls.playSessionEnd()

    fun stopSessionEnd() = bowls.stopSessionEnd()

    fun release() {
        bowls.release()
        if (!started) return
        players.forEach { it.stop() }
        engine.stop()
        rendered.clear()
        started = false
    }

    /**
     * Which pitch a phase would strike, or null for anything this class does not synthesise: the
     * recorded bowl, which [IosBowls] plays instead, and a file of the reader's own, which is
     * still owed.
     */
    private fun pitchOf(phase: Phase, preset: Preset): Float? {
        val sound = preset.soundOf(phase)
        if (sound.markerUri != null) return null // a picked file: owed with the file picker
        return when (sound.tone) {
            MarkerTone.BELL -> BELL_HZ
            MarkerTone.TICK -> TICK_HZ * tickRate(phase)
            MarkerTone.GONG -> null // a recording — handled by IosBowls, never reaches here
        }
    }

    private fun samplesFor(hz: Float, preset: Preset, phase: Phase): FloatArray =
        if (preset.soundOf(phase).tone == MarkerTone.TICK) {
            tickMarker(hz, sampleRate)
        } else {
            struckMarker(hz, sampleRate)
        }

    private fun start() {
        if (started) return
        configureAudioSession()
        players.forEach {
            engine.attachNode(it)
            engine.connect(it, engine.mainMixerNode, format)
        }
        engine.startAndReturnError(null)
        started = true
    }

    private fun buffer(samples: FloatArray): AVAudioPCMBuffer {
        val buffer = AVAudioPCMBuffer(pCMFormat = format, frameCapacity = samples.size.toUInt())
        buffer.frameLength = samples.size.toUInt()
        val channel = buffer.floatChannelData?.get(0) ?: return buffer
        for (i in samples.indices) channel[i] = samples[i]
        return buffer
    }

    private companion object {
        const val VOICES = 4
    }
}
