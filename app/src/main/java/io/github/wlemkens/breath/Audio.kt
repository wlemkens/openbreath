package io.github.wlemkens.breath

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.SoundPool
import android.net.Uri
import java.util.Random
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

private const val SR = 44100

/**
 * Waves coming ashore: white noise squeezed into a band by two one-pole filters, the upper
 * edge riding the breath.
 *
 * Noise has no pitch to shift, so "the pitch goes up during the breath in" is a filter
 * cutoff sweep — that is what actually reads as a wave rising and falling. Every constant
 * below is a tuning knob; they were picked by ear and are meant to be adjusted.
 */
class WaveSynth {
    /** 0 = fully exhaled, 1 = fully inhaled. Written from the UI thread, read by the audio thread. */
    @Volatile
    var openness: Float = 0f

    /**
     * Per-phase level: the sound mode's on/off multiplied by [PhaseState.edge], so it dips
     * to silence at every phase boundary. Written every frame; smoothed only enough to take
     * the stair-steps off a 60 Hz update, since the ramp it follows is already gradual.
     */
    @Volatile
    var phaseGain: Float = 0f

    @Volatile
    private var alive = false
    private var thread: Thread? = null

    fun start() {
        alive = true
        // a thread still fading out will simply fade back in — never two tracks at once
        if (thread?.isAlive == true) return
        thread = Thread(::render, "WaveSynth").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    /**
     * Fire-and-forget: the render thread fades out and releases itself. Deliberately does
     * not join — this is called from the main thread and a ~1 s fade must not block a frame.
     */
    fun stop() {
        alive = false
    }

    private fun render() {
        val minBytes =
            AudioTrack.getMinBufferSize(SR, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SR)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(maxOf(minBytes, 8192))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        val buf = ShortArray(1024)
        val rnd = Random()
        var lp = 0f // upper band edge, swept by the breath
        var hp = 0f // lower band edge, fixed — kills the rumble
        var cutoff = LO_HZ
        var master = 0f
        var gate = phaseGain
        var lfo = 0.0

        track.play()
        // keep rendering past `alive` until the fade-out has actually reached silence
        while (alive || master > 1e-3f) {
            val target = if (alive) 1f else 0f
            val fade = if (alive) FADE_IN else FADE_OUT
            for (i in buf.indices) {
                // per-sample glides: openness only updates at frame rate, and stepping the
                // cutoff once per buffer zippers audibly
                master += (target - master) * fade
                gate += (phaseGain - gate) * GATE_GLIDE
                cutoff += (LO_HZ + (HI_HZ - LO_HZ) * openness - cutoff) * GLIDE

                val a = 1f - exp(-2.0 * PI * cutoff / SR).toFloat()
                lp += a * ((rnd.nextFloat() * 2f - 1f) - lp)
                hp += HP_A * (lp - hp)
                // a one-pole loses amplitude as it closes; undo that so loudness is set
                // only by `gain` below and not smuggled in by the sweep
                val band = (lp - hp) / sqrt(a / (2f - a))

                lfo += TEXTURE_HZ / SR
                val texture = 1f + 0.22f * sin(2.0 * PI * lfo).toFloat()
                val gain = (0.22f + 0.78f * openness) * texture * master * gate

                buf[i] = (band * gain * 9000f).toInt().coerceIn(-32768, 32767).toShort()
            }
            track.write(buf, 0, buf.size)
        }
        track.stop()
        track.release()
    }

    private companion object {
        const val LO_HZ = 260f // fully exhaled
        const val HI_HZ = 2100f // fully inhaled
        const val TEXTURE_HZ = 0.13 // slow undulation, so holds don't sound frozen
        const val GLIDE = 0.0004f // cutoff settles in ~60 ms
        // fast (~4 ms): the phase envelope must actually reach silence at a boundary, and a
        // slow crossfade here would smear the 0.2 s fade into inaudibility. Safe to be this
        // fast because mode changes now land while the envelope is already at zero.
        const val GATE_GLIDE = 0.006f
        const val FADE_IN = 0.00002f // ~1 s in
        const val FADE_OUT = 0.00015f // ~0.15 s out
        val HP_A = 1f - exp(-2.0 * PI * 90.0 / SR).toFloat()
    }
}

/**
 * The sound at the *end* of a phase: the user's own mp3 if they picked one, otherwise a
 * synthesized bell — which keeps the app asset-free.
 */
class PhaseMarkers(private val context: Context) {
    private val pool = SoundPool.Builder()
        .setMaxStreams(2)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val loaded = mutableMapOf<String, Int>()
    private var bell: AudioTrack? = null

    /** Decode every mp3 the preset points at up front — a phase change must not wait on IO. */
    fun prepare(preset: Preset) {
        for (phase in Phase.entries) {
            val uri = preset.soundOf(phase).markerUri ?: continue
            if (uri in loaded) continue
            // a file the user has since deleted or revoked must not take the session down
            runCatching {
                context.contentResolver.openAssetFileDescriptor(Uri.parse(uri), "r")!!.use {
                    loaded[uri] = pool.load(it, 1)
                }
            }
        }
    }

    fun play(phase: Phase, preset: Preset) {
        val id = preset.soundOf(phase).markerUri?.let { loaded[it] }
        if (id != null) {
            pool.play(id, 1f, 1f, 1, 0, 1f)
        } else {
            playBell()
        }
    }

    /**
     * ponytail: one static AudioTrack reused for every bell, so two phase ends closer than
     * the 1.2 s tail cut each other off. Phases are seconds apart; give the bell its own
     * SoundPool entry if that ever stops being true.
     */
    private fun playBell() {
        val track = bell ?: buildBell().also { bell = it }
        runCatching {
            track.stop()
            track.reloadStaticData()
            track.play()
        }
    }

    fun release() {
        pool.release()
        bell?.release()
        bell = null
        loaded.clear()
    }

    private fun buildBell(): AudioTrack {
        val n = SR * 12 / 10
        val pcm = ShortArray(n)
        for (i in 0 until n) {
            val t = i.toDouble() / SR
            val env = exp(-3.2 * t)
            val s = sin(2 * PI * 528 * t) * 0.7 + sin(2 * PI * 1056 * t) * 0.3
            pcm[i] = (s * env * 9000).toInt().toShort()
        }
        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SR)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(n * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
            .also { it.write(pcm, 0, n) }
    }
}
