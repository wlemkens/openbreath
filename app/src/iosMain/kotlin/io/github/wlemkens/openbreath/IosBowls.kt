package io.github.wlemkens.openbreath

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.AVFAudio.AVAudioPlayer
import platform.Foundation.NSData
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfFile
import platform.darwin.dispatch_after
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_time

/**
 * Everything on iOS that is played rather than synthesised: the two bundled bowls, and a file of
 * the reader's own once they have picked one.
 *
 * `AVAudioPlayer` and not the engine the rest of the sound goes through, because these are
 * encoded mp3 rather than samples we generated — a player decodes them, where feeding
 * `AVAudioPlayerNode` would mean decoding to PCM first for no gain. The pitch comes from `rate`
 * with `enableRate`, which shifts speed and pitch together exactly as SoundPool's rate does on
 * Android, so the same recording marks a breath in and a breath out differently on both.
 *
 * **A player has to be kept referenced or it stops.** AVAudioPlayer does not retain itself while
 * playing, so one created in a function and dropped goes quiet the moment it is collected —
 * a bowl that sounds for a random fraction of itself. Hence the ring.
 */
@OptIn(ExperimentalForeignApi::class)
class IosBowls {

    private var phaseData: NSData? = null
    private var endData: NSData? = null

    /** Picked files, by the handle stored in the preset — see [Files.pickAudio]. */
    private val picked = mutableMapOf<String, NSData>()

    /** In rotation, so a strike landing inside the previous one's tail does not cut it off. */
    private val ringing = mutableListOf<AVAudioPlayer>()
    private var endPlayer: AVAudioPlayer? = null

    suspend fun prepare() {
        if (phaseData == null) phaseData = bowlBytes(Bowl.PHASE)?.toNSData()
        if (endData == null) endData = bowlBytes(Bowl.SESSION_END)?.toNSData()
    }

    /**
     * Reads in every sound the preset points at, before a boundary needs one.
     *
     * A handle that no longer resolves is left out rather than made an error, and the phase then
     * falls back to its tone — see [IosMarkers]. A preset can name a file this phone has never had:
     * a backup carried over from Android names `content://` URIs that mean nothing here.
     */
    fun preparePicked(handles: Collection<String>) {
        for (handle in handles) {
            if (handle in picked) continue
            val path = pickedMarkerPath(handle) ?: continue
            NSData.dataWithContentsOfFile(path)?.let { picked[handle] = it }
        }
    }

    /** Whether that file was actually found — see the fallback in IosMarkers. */
    fun hasPicked(handle: String): Boolean = handle in picked

    /** Played whole and unpitched: truncating or transposing someone's own choice is not ours. */
    fun playPicked(handle: String) {
        start(picked[handle] ?: return, rate = 1f)
    }

    /**
     * The bowl at a phase boundary, pitched by the turn it marks and faded rather than left to
     * ring into the next phase — see [GONG_FADE_START_MS], which is shared with Android so the
     * two cannot drift.
     */
    fun playPhase(phase: Phase) {
        val player = start(phaseData ?: return, gongRate(phase)) ?: return

        after(GONG_FADE_START_MS) {
            // fadeDuration is seconds, and the ramp is the player's own rather than a timer of
            // ours writing volume in steps
            player.setVolume(0f, fadeDuration = GONG_FADE_MS / 1000.0)
            after(GONG_FADE_MS) {
                player.stop()
                ringing.remove(player)
            }
        }
    }

    /** No fade: a session that is over may ring out, and the recording has one baked in. */
    fun playSessionEnd() {
        stopSessionEnd()
        endPlayer = start(endData ?: return, rate = 1f)
    }

    /** So that starting again does not play the first breath over the last session's tail. */
    fun stopSessionEnd() {
        endPlayer?.stop()
        endPlayer = null
    }

    fun release() {
        ringing.forEach { it.stop() }
        ringing.clear()
        picked.clear()
        stopSessionEnd()
    }

    private fun start(data: NSData, rate: Float): AVAudioPlayer? {
        configureAudioSession()
        val player = AVAudioPlayer(data = data, error = null) ?: return null
        player.enableRate = true
        player.rate = rate
        player.prepareToPlay()
        if (!player.play()) return null
        ringing += player
        // the ring is a leak guard, not a voice limit: a stopped player is dropped by the fade
        // above, and this only catches the ones that somehow were not
        if (ringing.size > VOICES) ringing.removeAt(0).stop()
        return player
    }

    private fun after(ms: Long, block: () -> Unit) {
        // 0 is DISPATCH_TIME_NOW, spelled out rather than imported: it is documented as zero and
        // the constant is not something the Kotlin/Native bindings reliably surface
        dispatch_after(dispatch_time(0uL, ms * 1_000_000L), dispatch_get_main_queue()) { block() }
    }

    private companion object {
        const val VOICES = 4
    }
}

/**
 * The bytes as Foundation sees them. Pinned for the copy, because `NSData.create` reads through
 * the pointer immediately and Kotlin is otherwise free to move the array while it does.
 */
@OptIn(ExperimentalForeignApi::class)
private fun ByteArray.toNSData(): NSData = usePinned { pinned ->
    NSData.create(bytes = pinned.addressOf(0), length = size.toULong())
}
