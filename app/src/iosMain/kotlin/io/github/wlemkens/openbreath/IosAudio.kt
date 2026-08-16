package io.github.wlemkens.openbreath

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.get
import kotlinx.cinterop.set
import platform.AVFAudio.AVAudioEngine
import platform.AVFAudio.AVAudioFormat
import platform.AVFAudio.AVAudioPCMBuffer
import platform.AVFAudio.AVAudioPCMFormatFloat32
import platform.AVFAudio.AVAudioPlayerNode
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.setActive

/**
 * The sink for [WaveDsp] on iOS: an engine, a player node, and buffers kept fed.
 *
 * The sound itself is commonMain's and is shared with Android to the constant. What is here is
 * only where the samples go, which is the same division androidMain/Audio.kt keeps.
 *
 * **Scheduled buffers rather than an AVAudioSourceNode.** A source node's render block is called
 * on the realtime audio thread, and Kotlin/Native's runtime is not something to invite there: a
 * collection landing inside a render callback is a dropout you can hear. Scheduling ahead moves
 * the Kotlin onto an ordinary queue and leaves the realtime thread reading memory that is already
 * filled.
 *
 * **The sample rate comes from the engine, not from a constant.** iOS gives back whatever the
 * output node is running at, which is commonly 48000 where Android asked for 44100, and every
 * filter corner in the DSP is derived from it.
 */
/**
 * .playback, so a meditation still sounds with the ringer switch silenced. A breathing app that
 * goes mute because the phone is on silent has failed at the one moment it was most wanted.
 *
 * Shared by both engines and safe to call twice: the markers may sound in a preset whose phases
 * ask for no ambient bed at all, so neither can rely on the other having gone first.
 */
@OptIn(ExperimentalForeignApi::class)
internal fun configureAudioSession() {
    val session = AVAudioSession.sharedInstance()
    session.setCategory(AVAudioSessionCategoryPlayback, null)
    session.setActive(true, null)
}

@OptIn(ExperimentalForeignApi::class)
class IosWaveSynth {
    private val engine = AVAudioEngine()
    private val player = AVAudioPlayerNode()

    private val sampleRate: Int =
        engine.outputNode.outputFormatForBus(0u).sampleRate.toInt().coerceAtLeast(8000)

    private val dsp = WaveDsp(sampleRate)

    /** Mono float, non-interleaved: what [WaveDsp] produces, with no conversion in between. */
    private val format = AVAudioFormat(
        commonFormat = AVAudioPCMFormatFloat32,
        sampleRate = sampleRate.toDouble(),
        channels = 1u,
        interleaved = false,
    )

    private val scratch = FloatArray(FRAMES)
    private var started = false

    var openness: Float
        get() = dsp.openness
        set(value) { dsp.openness = value }
    var wavesGain: Float
        get() = dsp.wavesGain
        set(value) { dsp.wavesGain = value }
    var soundwaveGain: Float
        get() = dsp.soundwaveGain
        set(value) { dsp.soundwaveGain = value }

    fun start() {
        dsp.running = true
        if (started) return // already fading back in rather than starting a second engine
        dsp.prime()

        configureAudioSession()

        engine.attachNode(player)
        engine.connect(player, engine.mainMixerNode, format)
        engine.startAndReturnError(null)
        player.play()
        started = true

        // two in flight: one playing while the next is being filled, which is the smallest number
        // that never leaves the node with nothing to read
        scheduleNext()
        scheduleNext()
    }

    /**
     * Fire-and-forget, as on Android: the fade-out is played out by the buffers already being
     * scheduled, and the engine stops itself once [WaveDsp.finished] goes true. Blocking a frame
     * on a one-second fade is not worth the tidiness.
     */
    fun stop() {
        dsp.running = false
    }

    /** Everything down, whether or not it was ever started. */
    fun release() {
        dsp.running = false
        if (!started) return
        player.stop()
        engine.stop()
        started = false
    }

    private fun scheduleNext() {
        if (!started) return
        if (dsp.finished) {
            // the fade has reached silence; let go of the hardware rather than idle on it
            release()
            return
        }
        val buffer = AVAudioPCMBuffer(pCMFormat = format, frameCapacity = FRAMES.toUInt())
        buffer.frameLength = FRAMES.toUInt()
        val channel = buffer.floatChannelData?.get(0) ?: return

        dsp.render(scratch)
        for (i in 0 until FRAMES) channel[i] = scratch[i]

        player.scheduleBuffer(buffer) { scheduleNext() }
    }

    private companion object {
        /**
         * ~23 ms at 44.1k. Small enough that a level written this frame is heard this breath, and
         * large enough that the callback churn stays negligible.
         */
        const val FRAMES = 1024
    }
}
