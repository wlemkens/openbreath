package io.github.wlemkens.openbreath

import io.github.wlemkens.openbreath.media.Res

/**
 * The two recordings, and the shape of playing them.
 *
 * These are the only sounds in the app that are not arithmetic. The bell and the tick are
 * synthesised from numbers in MarkerSynth.kt and so cost nothing to share; a bowl has to be
 * bundled, and it used to be bundled where only Android could reach it — `res/raw` is Android
 * resource machinery and Kotlin/Native has no way into it. They live in
 * `commonMain/composeResources/files` now, which both platforms read through the generated [Res].
 *
 * The paths are named here rather than spelled at each call site because a mistyped resource path
 * is a runtime miss, not a compile error: the bowl would simply never sound, which is exactly the
 * failure mode the LFS check exists to catch and is no easier to notice for a different cause.
 */
internal object Bowl {
    /** Cut to 6 s from a 15 s recording, because the whole thing is decoded into memory. */
    const val PHASE = "files/singing_bowl.mp3"

    /** Cut to 12 s with its fade baked in, from a 50 s original. A session that is over may ring. */
    const val SESSION_END = "files/session_end.mp3"
}

/**
 * How long the phase bowl is allowed to sound before it is taken away.
 *
 * The recording rings for longer than a breath phase, so it is faded rather than left to overlap
 * the next one — about 4.2 s audible in total. Both numbers were picked by ear, which is exactly
 * why they are here and not one copy per platform: a constant chosen by ear that drifts between
 * two files is a difference nobody can see in a diff and everybody can hear.
 */
internal const val GONG_FADE_START_MS = 3_500L
internal const val GONG_FADE_MS = 700L

/**
 * The bytes of a bundled recording, or null if it could not be read.
 *
 * Null rather than an exception because a missing bowl is a quieter app and not a broken one — and
 * because the way it goes missing in practice is a clone without git-lfs, where the "mp3" is a
 * 130-byte pointer that no decoder will take. A session must not fail to start over that.
 */
internal suspend fun bowlBytes(path: String): ByteArray? =
    runCatching { Res.readBytes(path) }.getOrNull()?.takeIf { it.isNotEmpty() }
