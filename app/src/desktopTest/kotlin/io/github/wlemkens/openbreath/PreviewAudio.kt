package io.github.wlemkens.openbreath

import java.io.ByteArrayInputStream
import java.io.File
import javax.sound.sampled.AudioFileFormat
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertTrue
import org.junit.Assume.assumeTrue

/**
 * The soundtrack for the App Store preview, rendered rather than recorded.
 *
 * `./gradlew :app:desktopTest --tests '*PreviewAudio*' -Ppreview=true` writes
 * `app/build/preview/waves.wav`; `.github/ios-previews.sh` muxes it onto the simulator capture.
 * The IconGen pattern, and for the same reason: a generated asset that only regenerates when asked.
 *
 * **Why a render and not a recording.** Apple *requires* an audio track on an app preview — stereo
 * AAC, 256 kbps — and `simctl io recordVideo` captures the display and nothing else, so there is no
 * device audio to be had. The alternative to this file is a silent track, which would satisfy the
 * requirement and misrepresent the app.
 *
 * **Why it is honest anyway.** Every constant of the sound is in `WaveDsp` in commonMain and every
 * constant of the breath is in `Session.kt`, both shared with the phone, so what this writes is what
 * an iPhone plays — same filters, same easing, same edge dip at each boundary. It is not a
 * re-creation of the app's sound; it is the app's sound, asked for a buffer instead of a speaker.
 * If the two ever diverge it is because someone moved a constant out of commonMain, which is the
 * thing CLAUDE.md says not to do.
 *
 * The one thing it cannot know is where the video will start, so the render begins at elapsed zero —
 * the top of an inhale — and the script trims the capture to the same instant.
 */
class PreviewAudio {

    @Test
    fun render() {
        // skipped rather than silently doing nothing, so a run that meant to regenerate and forgot
        // the flag says so in the report instead of looking like it worked
        assumeTrue(
            "the preview soundtrack is rendered only with -Ppreview=true",
            System.getProperty("openbreath.preview").toBoolean(),
        )

        val out = File("build/preview/waves.wav")
        out.parentFile.mkdirs()
        writeWaves(out, seconds = SECONDS, rate = RATE)

        // a wav of the wrong length silently desynchronises the preview, and 44 bytes of header
        // with nothing after it is what a broken render looks like
        val expected = 44 + SECONDS * RATE * 2 * 2
        assertTrue(
            out.length() in (expected - 4096)..(expected + 4096),
            "${out.length()} bytes, expected about $expected",
        )
        println("wrote ${out.absolutePath}, ${out.length()} bytes")
    }

    private companion object {
        /** Two whole breaths of the default preset. 15 to 30 seconds is Apple's window. */
        const val SECONDS = 22
        const val RATE = 44100
    }
}

/**
 * The wave bed for [seconds] of the default preset, from elapsed zero, as a 16-bit stereo wav.
 *
 * Mono duplicated to both channels: the synth is one voice and Apple wants two channels. Panning it
 * would be an invention — the app does not.
 */
internal fun writeWaves(out: File, seconds: Int, rate: Int) {
    // Preset() is the default preset, Coherence 5.5, which is also what a fresh install starts on —
    // so this is the breath the video shows, taken through the app's own Preset-to-Timing path
    // rather than by repeating 5500 here
    val timing = Preset().timing
    val dsp = WaveDsp(rate)
    dsp.running = true
    dsp.wavesGain = 1f
    dsp.prime()

    val block = FloatArray(1024)
    val total = seconds * rate
    val pcm = ByteArray(total * 4) // 2 channels x 2 bytes
    var written = 0

    while (written < total) {
        // the same two values the app writes every frame, from the same shared arithmetic
        val state = phaseAt((written * 1000L) / rate, timing)
        dsp.openness = state.openness
        dsp.wavesGain = state.edge
        dsp.render(block)

        for (i in block.indices) {
            if (written + i >= total) break
            val s = (block[i].coerceIn(-1f, 1f) * Short.MAX_VALUE).roundToInt().toShort()
            val at = (written + i) * 4
            pcm[at] = (s.toInt() and 0xFF).toByte()
            pcm[at + 1] = ((s.toInt() shr 8) and 0xFF).toByte()
            pcm[at + 2] = pcm[at]
            pcm[at + 3] = pcm[at + 1]
        }
        written += block.size
    }

    val format = AudioFormat(rate.toFloat(), 16, 2, true, false)
    AudioInputStream(ByteArrayInputStream(pcm), format, (total).toLong()).use {
        AudioSystem.write(it, AudioFileFormat.Type.WAVE, out)
    }
}
