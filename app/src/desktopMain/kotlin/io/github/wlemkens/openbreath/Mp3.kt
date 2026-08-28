package io.github.wlemkens.openbreath

import javazoom.jl.decoder.Bitstream
import javazoom.jl.decoder.Decoder
import javazoom.jl.decoder.SampleBuffer
import java.io.ByteArrayInputStream
import java.io.File
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem

/**
 * Decoding an mp3, which is the one thing the JVM cannot do by itself.
 *
 * Android has SoundPool and iOS has AVAudioPlayer; `javax.sound.sampled` reads WAV, AU and AIFF
 * and nothing else, so without a decoder here the two bundled bowls and a reader's own file could
 * not sound on the desktop at all. JLayer is that decoder and the only non-Apache dependency in
 * the tree — see the note beside it in build.gradle.kts.
 *
 * Everything downstream of this is ordinary samples, which is why the desktop needs no equivalent
 * of iOS's split between a player for recordings and an engine for syntheses: once a bowl is a
 * FloatArray it is played exactly the way the bell is.
 */

/** Decoded samples and the rate they were recorded at, which is not necessarily the output's. */
internal class Pcm(val samples: FloatArray, val rate: Int)

/**
 * Mono float samples in [-1, 1], or null if the bytes are not an mp3 we can read.
 *
 * Null rather than an exception for the same reason [bowlBytes] returns null: the way this fails in
 * practice is a clone without git-lfs, where the "mp3" is a 130-byte pointer. A session must not
 * fail to start over a bowl it cannot decode, and `.github/check-media.sh` is what catches the
 * cause rather than the symptom.
 *
 * Downmixed to mono because that is what both the DSP and every marker line here are: a stereo
 * recording averaged is one line's worth of the same sound, and the app has no stereo image to
 * preserve.
 */
internal fun decodeMp3(bytes: ByteArray): Pcm? = runCatching {
    val stream = Bitstream(ByteArrayInputStream(bytes))
    val decoder = Decoder()
    var out = FloatArray(INITIAL)
    var length = 0
    var rate = 0

    while (true) {
        val header = stream.readFrame() ?: break
        rate = header.frequency()
        val buffer = decoder.decodeFrame(header, stream) as SampleBuffer
        val channels = buffer.channelCount.coerceAtLeast(1)
        val samples = buffer.buffer
        val count = buffer.bufferLength

        // interleaved, so a frame of n channels is n consecutive shorts
        val frames = count / channels
        if (length + frames > out.size) out = out.copyOf(maxOf(out.size * 2, length + frames))
        for (f in 0 until frames) {
            var sum = 0f
            for (c in 0 until channels) sum += samples[f * channels + c] / 32768f
            out[length++] = sum / channels
        }
        stream.closeFrame()
    }
    stream.close()

    if (length == 0 || rate <= 0) null else Pcm(out.copyOf(length), rate)
}.getOrNull()

/**
 * A file the reader picked, whatever kind it is: mp3 through JLayer, and WAV, AIFF or AU through
 * `javax.sound.sampled`, which reads those three and only those three.
 *
 * Both, rather than mp3 alone, because the other two platforms take all of these — SoundPool and
 * AVAudioPlayer are not fussy — and the desktop being the one that quietly falls back to a bell
 * for a wav is precisely the failure CLAUDE.md warns about: pick your own sound, hear a bell, and
 * there is no way to tell whether the app or the file is at fault. Reading WAV costs stdlib.
 */
internal fun decodeAudio(file: File): Pcm? {
    val bytes = runCatching { file.readBytes() }.getOrNull() ?: return null
    return decodeMp3(bytes) ?: decodeSampled(file)
}

/** WAV, AIFF and AU, downmixed to mono float exactly as [decodeMp3] leaves an mp3. */
private fun decodeSampled(file: File): Pcm? = runCatching {
    AudioSystem.getAudioInputStream(file).use { encoded ->
        // whatever it was — 8-bit, 24-bit, float, big-endian — asked for as the 16-bit signed
        // little-endian mono the rest of this file speaks. AudioSystem converts, or refuses and
        // this returns null
        val target = AudioFormat(encoded.format.sampleRate, 16, 1, true, false)
        AudioSystem.getAudioInputStream(target, encoded).use { pcm ->
            val bytes = pcm.readBytes()
            val samples = FloatArray(bytes.size / 2) {
                val low = bytes[it * 2].toInt() and 0xFF
                val high = bytes[it * 2 + 1].toInt() // signed: this is the top byte
                ((high shl 8) or low) / 32768f
            }
            if (samples.isEmpty()) null else Pcm(samples, target.sampleRate.toInt())
        }
    }
}.getOrNull()

/**
 * Resamples in one pass, doing the two jobs that would otherwise be two: bringing a recording made
 * at one rate onto a line running at another, and shifting its pitch.
 *
 * Both are the same arithmetic — read the source faster and it comes out higher and shorter — so
 * combining them is not a shortcut but the honest description. [pitch] is the same number
 * `gongRate` hands SoundPool on Android and `AVAudioPlayer.rate` on iOS, which is why the low bowl
 * sounds the same on all three: 0.6 there and 0.6 here mean one thing.
 *
 * Linear interpolation rather than nearest-sample. It is a few instructions more and it is the
 * difference between a bowl and a bowl with a hiss on it — nearest-sample at a non-integer step
 * is a stairstep, and a stairstep is broadband noise.
 */
internal fun resample(source: FloatArray, from: Int, to: Int, pitch: Float): FloatArray {
    val step = (from.toDouble() / to) * pitch
    if (source.isEmpty() || step <= 0.0) return FloatArray(0)
    // the last output sample must have a source sample after it to interpolate towards
    val length = ((source.size - 1) / step).toInt().coerceAtLeast(1)
    val out = FloatArray(length)
    for (i in 0 until length) {
        val at = i * step
        val index = at.toInt()
        val fraction = (at - index).toFloat()
        val next = if (index + 1 < source.size) source[index + 1] else source[index]
        out[i] = source[index] + (next - source[index]) * fraction
    }
    return out
}

/** ~1.5 s at 44.1k, so a six-second bowl doubles twice rather than a dozen times. */
private const val INITIAL = 65_536
