package io.github.wlemkens.openbreath

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.Mixer
import javax.sound.sampled.SourceDataLine

/**
 * The desktop's one output stream: the ambient bed from [WaveDsp] with every marker mixed into it.
 *
 * **One stream, where Android and iOS each use two or three.** Not a simplification for its own
 * sake — it is what the platform forces. Android hands a bowl to SoundPool and a bell to an
 * AudioTrack and lets the system mix them; on Linux there may be exactly one route out of the
 * process (see [openSink]), so anything that wants to be heard has to be summed here first.
 *
 * It turns out to be the better design anyway. The bowl's fade is arithmetic over a sample index
 * instead of ten scheduled volume writes, two strikes inside one tail overlap properly instead of
 * cutting each other off, and there is one place where clipping can be reasoned about — which is
 * why [MARKER_PEAK] had to become `internal` and why the recorded bowls are normalised to it.
 *
 * Created once per session by [DesktopPlatform.session] and shared by the [Synth] and the
 * [Markers] it hands out, because they are now literally the same stream.
 */
class DesktopAudio {

    /**
     * The rate everything is rendered at, decided before anything is opened because [WaveDsp] and
     * every marker have to be built for it — a synth built for 44100 and played at 48000 is the
     * same sound roughly a semitone sharp.
     *
     * 44100 whenever a system player is available, since that is what every ear-picked constant was
     * tuned at and PipeWire or CoreAudio will resample as it pleases. Only the raw-device fallback
     * has to take what it is given.
     */
    val sampleRate: Int = if (playerCommand() != null) TUNED_RATE else supportedRate()

    private val dsp = WaveDsp(sampleRate)

    /**
     * Markers currently sounding. Copy-on-write because the audio thread walks it every block while
     * the composition thread adds to it at a phase boundary — a few strikes a minute against a
     * read every 23 ms, which is exactly the ratio this list is for.
     */
    private val ringing = CopyOnWriteArrayList<Voice>()

    private var thread: Thread? = null

    @Volatile
    private var closed = false

    var openness: Float
        get() = dsp.openness
        set(value) { dsp.openness = value }
    var wavesGain: Float
        get() = dsp.wavesGain
        set(value) { dsp.wavesGain = value }
    var soundwaveGain: Float
        get() = dsp.soundwaveGain
        set(value) { dsp.soundwaveGain = value }

    fun startWaves() {
        dsp.running = true
        // a thread still fading out will simply fade back in — never two streams at once
        if (thread?.isAlive != true) dsp.prime()
        wake()
    }

    /** Fire-and-forget: the thread plays out the fade and lets go of the sink itself. */
    fun stopWaves() {
        dsp.running = false
    }

    /**
     * Sound this, now, mixed over whatever else is playing.
     *
     * [fadeAfterMs] is the bowl's: its recording rings for longer than a breath phase, so it is
     * taken away rather than left to overlap the next one. [tag] is how [silence] finds it again,
     * which only the session-end bowl needs.
     */
    fun strike(samples: FloatArray, fadeAfterMs: Long? = null, tag: Any? = null) {
        if (samples.isEmpty() || closed) return
        val from = fadeAfterMs?.let { (it * sampleRate / 1000).toInt() } ?: -1
        ringing += Voice(samples, tag, from, (GONG_FADE_MS * sampleRate / 1000).toInt())
        wake()
    }

    /** Cuts short whatever was struck under [tag] — see [Markers.stopSessionEnd]. */
    fun silence(tag: Any) {
        ringing.removeAll(ringing.filter { it.tag == tag })
    }

    /**
     * Everything down. The thread notices and releases the sink; joined briefly so a session that
     * is being torn down for a new one does not leave two streams open for a moment.
     */
    fun release() {
        closed = true
        dsp.running = false
        ringing.clear()
        thread?.join(TimeUnit.MILLISECONDS.toMillis(250))
        thread = null
    }

    /**
     * Starts the thread if nothing is running, which is what lets a marker sound in a preset whose
     * phases ask for no ambient bed at all — there is then no `startWaves` to have opened anything.
     */
    private fun wake() {
        if (closed || thread?.isAlive == true) return
        thread = Thread(::run, "OpenBreath audio").apply {
            isDaemon = true // a closed window must not keep the process alive mid-fade
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    /**
     * The sink is opened here and not held in a field, which matters on both routes: it is closed
     * when the last sound has faded, and a field would then hold a closed line or a dead process
     * that the next Start could not reopen. Android's WaveSynth does the same.
     */
    private fun run() {
        val sink = openSink(sampleRate) ?: return
        val block = FloatArray(BLOCK)
        val bytes = ByteArray(BLOCK * 2)
        try {
            // past `running` until the fade has actually reached silence, and past that until
            // every marker has finished ringing
            while (!closed && (!dsp.finished || ringing.isNotEmpty())) {
                dsp.render(block)
                mix(block)
                block.toPcm(bytes)
                sink.write(bytes)
            }
        } catch (e: Exception) {
            // a sink that dies mid-session — the process killed, the device pulled — must cost the
            // sound and not the sitting. Said out loud, because silence explains nothing by itself
            say("the audio sink stopped: $e")
        } finally {
            runCatching { sink.close() }
        }
    }

    /** Sums every ringing marker onto the bed and drops the ones that have finished. */
    private fun mix(out: FloatArray) {
        if (ringing.isEmpty()) return
        val spent = mutableListOf<Voice>()
        for (voice in ringing) {
            voice.mixInto(out)
            if (voice.done) spent += voice
        }
        if (spent.isNotEmpty()) ringing.removeAll(spent)
    }

    /**
     * One marker part-way through sounding.
     *
     * The fade is a multiplier over the sample index rather than a timer writing a volume control,
     * which is the whole reason this class replaced a `Clip`: it cannot be late, it cannot be
     * cancelled halfway by a restart, and it is the same arithmetic on every platform.
     */
    private class Voice(
        private val samples: FloatArray,
        val tag: Any?,
        private val fadeFrom: Int,
        private val fadeLength: Int,
    ) {
        private var at = 0

        private val end =
            if (fadeFrom >= 0) minOf(samples.size, fadeFrom + fadeLength) else samples.size

        val done: Boolean get() = at >= end

        fun mixInto(out: FloatArray) {
            var i = 0
            while (i < out.size && at < end) {
                val gain =
                    if (fadeFrom < 0 || at < fadeFrom) 1f
                    else 1f - (at - fadeFrom).toFloat() / fadeLength
                out[i] += samples[at] * gain
                i++
                at++
            }
        }
    }

    private companion object {
        /** ~23 ms at 44.1k, the same block iOS schedules: a level written this frame is heard now. */
        const val BLOCK = 1024
    }
}

/** Where the samples can actually go. Two routes, and on Linux they are not equivalent. */
internal interface PcmSink {
    /** Blocks until the sink has room, which is the backpressure that paces the render loop. */
    fun write(bytes: ByteArray)
    fun close()
}

/**
 * The rate every ear-picked constant in WaveDsp and MarkerSynth was chosen against. Preferred
 * wherever the sink will resample for us rather than demanding the hardware's own rate.
 */
private const val TUNED_RATE = 44100

/** Said on stderr, in the shape Android's Log.w and iOS's NSLog lines already take. */
internal fun say(message: String) = System.err.println("OpenBreath: $message")

/**
 * **Why this is not simply `AudioSystem.getLine`, which is what it was.**
 *
 * `javax.sound.sampled` on Linux can see raw ALSA hardware devices and nothing else — never the
 * ALSA `default` PCM, and so never PipeWire or PulseAudio. On a normal desktop PipeWire holds the
 * analog output, which then reports its formats as unsupported, and `AudioSystem.getLine` hands
 * back the first device it *can* open. On a machine with a graphics card that is an **HDMI port**.
 * The app then plays perfectly into a monitor, and there is no error anywhere: the first version of
 * this file did exactly that, and it took `fuser` on the sound devices to find out. Which is also
 * why [say] exists at all: Android logs this failure and iOS logs it, and only here was it silent.
 *
 * (Do not write that device path here. Kotlin block comments nest, so the slash-star inside it
 * opens a comment that never closes — which cost one red build already.)
 *
 * So on Linux the samples go to a system player over a pipe instead, which is strictly better than
 * a raw line in every way that matters: it follows whichever output the reader has actually
 * selected, it appears in the volume mixer as an ordinary application, and it shares the device
 * rather than seizing it. It costs a subprocess.
 *
 * Windows and macOS need none of this — JavaSound goes to the system default output there, which is
 * the right answer — so [playerCommand] finds nothing and the line is used. **That makes this the
 * one place in desktopMain that behaves differently per operating system**, alongside where the log
 * is stored. Both are noted in CLAUDE.md, because the claim that the desktop needs no per-OS
 * branching was made rather confidently and is now one branch weaker.
 */
internal fun openSink(rate: Int): PcmSink? =
    playerCommand()?.let { command -> processSink(command, rate) } ?: lineSink(rate)

/**
 * A system player that reads raw PCM from its standard input, or null where there is none — which
 * is every Windows and every macOS, and a Linux without either package installed.
 *
 * Two candidates, because which one exists is a packaging question and not a technical one:
 * `pw-cat` comes with `pipewire-bin`, `aplay` with `alsa-utils`, and `-D pulse` routes ALSA through
 * PipeWire's PulseAudio interface. Both were verified to reach the speakers on a machine where a
 * direct line could not. The first that is present wins; a Linux with neither falls through to the
 * raw line and gets a loud line in the log about it.
 */
private fun playerCommand(): List<String>? {
    if (!System.getProperty("os.name").orEmpty().lowercase().contains("linux")) return null
    val rate = TUNED_RATE.toString()
    val candidates = listOf(
        listOf("pw-cat", "--playback", "-", "--format", "s16", "--rate", rate, "--channels", "1"),
        listOf("aplay", "-D", "pulse", "-q", "-f", "S16_LE", "-r", rate, "-c", "1", "-"),
    )
    return candidates.firstOrNull { onPath(it.first()) }
}

/** Whether an executable is on PATH, without running it. */
private fun onPath(command: String): Boolean =
    System.getenv("PATH").orEmpty().split(':').any { dir ->
        dir.isNotEmpty() && java.io.File(dir, command).canExecute()
    }

private fun processSink(command: List<String>, rate: Int): PcmSink? = runCatching {
    // stderr to our own, so a player that refuses the format says so where it can be read rather
    // than filling a pipe nobody drains and wedging
    val process = ProcessBuilder(command)
        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
        .redirectError(ProcessBuilder.Redirect.INHERIT)
        .start()
    say("audio at $rate Hz through ${command.first()}")

    object : PcmSink {
        private val out = process.outputStream

        override fun write(bytes: ByteArray) {
            out.write(bytes)
            // unbuffered would hand the player 1024 frames at a time through a pipe, which is what
            // it wants; flushing keeps the latency at the block rather than at the stream buffer
            out.flush()
        }

        override fun close() {
            runCatching { out.close() }
            // it drains what it has and exits; killed only if it will not
            if (!process.waitFor(2, TimeUnit.SECONDS)) process.destroyForcibly()
        }
    }
}.getOrElse {
    say("could not start ${command.first()}: $it")
    null
}

/**
 * A raw device line. Correct on Windows and macOS, and the fallback of last resort on Linux.
 *
 * Non-HDMI first, because an HDMI port is almost never where someone wants a meditation played and
 * it is exactly what the default pick got wrong. If only HDMI can be opened, it is used and said
 * out loud — a sentence in the log is the difference between a five-minute diagnosis and an
 * afternoon of it.
 */
private fun lineSink(rate: Int): PcmSink? {
    val format = pcmFormat(rate)
    val info = DataLine.Info(SourceDataLine::class.java, format)

    val usable = AudioSystem.getMixerInfo().filter { mixer ->
        runCatching { AudioSystem.getMixer(mixer).isLineSupported(info) }.getOrDefault(false)
    }
    val (plain, hdmi) = usable.partition { !it.looksLikeHdmi() }
    if (plain.isEmpty() && hdmi.isNotEmpty()) {
        say("only HDMI outputs are available to Java — sound will go to a monitor, not to speakers")
    }

    for (mixer in plain + hdmi) {
        val line = runCatching {
            (AudioSystem.getMixer(mixer).getLine(info) as SourceDataLine).also {
                it.open(format, LINE_BUFFER_BYTES)
                it.start()
            }
        }.getOrNull() ?: continue

        say("audio at $rate Hz on ${mixer.name}")
        return object : PcmSink {
            override fun write(bytes: ByteArray) {
                line.write(bytes, 0, bytes.size)
            }

            override fun close() {
                runCatching { line.drain(); line.stop(); line.close() }
            }
        }
    }

    // null rather than an exception: a breathing app on a machine with no reachable output is still
    // a breathing app, and the cue is the part that matters most. But it does not do it quietly
    say("no audio output could be opened — the session will be silent")
    return null
}

/** HDMI and DisplayPort name themselves in the mixer description on every platform. */
private fun Mixer.Info.looksLikeHdmi(): Boolean =
    "$name $description".lowercase().let { "hdmi" in it || "displayport" in it }

/** ~0.19 s at 44.1k: long enough that a GC pause is not a dropout, short enough to stop promptly. */
private const val LINE_BUFFER_BYTES = 16_384

/**
 * Mono, signed 16-bit, little-endian — what every desktop mixer supports, what both players are
 * told to expect, and what [toPcm] writes.
 *
 * Mono because that is what [WaveDsp] and every marker produce. Asking for stereo would mean
 * writing the same samples twice for the mixer to sum back together.
 */
internal fun pcmFormat(rate: Int) = AudioFormat(rate.toFloat(), 16, 1, true, false)

/**
 * The best rate a raw line will take, preferring the one the constants were tuned at. Only reached
 * where there is no system player to resample for us.
 */
internal fun supportedRate(): Int = listOf(TUNED_RATE, 48000).firstOrNull { rate ->
    runCatching {
        AudioSystem.isLineSupported(DataLine.Info(SourceDataLine::class.java, pcmFormat(rate)))
    }.getOrDefault(false)
} ?: TUNED_RATE

/**
 * The exact inverse of the scaling [WaveDsp] and [MarkerSynth] apply, so these are the same shorts
 * Android writes — little-endian, matching [pcmFormat].
 *
 * The coercion is a hard clip, and it is meant to be unreachable: the bed peaks near 0.43 and every
 * marker is normalised to [MARKER_PEAK], so a strike landing on the loudest part of a wave sums to
 * about 0.8. It is here because "unreachable" and "unchecked" should not be the same thing.
 */
internal fun FloatArray.toPcm(into: ByteArray) {
    var j = 0
    for (v in this) {
        val s = (v * 32768f).toInt().coerceIn(-32768, 32767)
        into[j++] = (s and 0xFF).toByte()
        into[j++] = ((s shr 8) and 0xFF).toByte()
    }
}
