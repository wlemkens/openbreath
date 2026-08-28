package io.github.wlemkens.openbreath

import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * That the bundled bowls actually decode on this platform.
 *
 * Worth a test where the bell is not, and for three reasons at once. The bell is arithmetic that
 * runs or throws; a bowl goes through a dependency, and a bowl that fails to decode is **silent**,
 * which is the failure mode CLAUDE.md keeps warning about — the app looks fine, the boundary passes,
 * and nobody knows whether the file or the code is at fault.
 *
 * It also catches the LFS trap without meaning to: a clone made without git-lfs has a 130-byte
 * pointer here instead of an mp3, JLayer refuses it, and this goes red with the length assertion
 * rather than leaving it to `.github/check-media.sh` alone.
 *
 * Read off disk rather than through the generated `Res`, deliberately: the point is the decoder, and
 * the resource loader is already proven by the two platforms that ship.
 */
class Mp3Test {

    /**
     * The same paths [Bowl] names, resolved from the module directory a Gradle test runs in. Not
     * `Bowl.PHASE` prefixed by a guess — if these move, this test should fail to find them and say
     * so rather than quietly testing nothing.
     */
    private fun bowl(name: String) = File("src/commonMain/composeResources/$name")

    @Test
    fun `the phase bowl decodes to something a line could play`() {
        val file = bowl(Bowl.PHASE)
        assertTrue(file.exists(), "no bowl at ${file.absolutePath}")

        val pcm = assertNotNull(decodeMp3(file.readBytes()), "the phase bowl did not decode")
        assertTrue(pcm.rate in 8_000..192_000, "implausible sample rate ${pcm.rate}")

        // the comment on Bowl.PHASE says it is cut to 6 s, which is the claim being checked
        val seconds = pcm.samples.size.toDouble() / pcm.rate
        assertTrue(seconds > 3.0 && seconds < 10.0, "expected a few seconds of bowl, got $seconds")

        // and that it is a bowl rather than a run of zeroes, which is what a decoder that "works"
        // but reads the wrong field gives you
        assertTrue(pcm.samples.any { abs(it) > 0.01f }, "the bowl decoded to silence")
        assertTrue(pcm.samples.all { abs(it) <= 1.0f }, "samples outside [-1, 1] would clip")
    }

    @Test
    fun `the session end bowl decodes too`() {
        val pcm = assertNotNull(
            decodeMp3(bowl(Bowl.SESSION_END).readBytes()),
            "the session end bowl did not decode",
        )
        val seconds = pcm.samples.size.toDouble() / pcm.rate
        // cut to 12 s with its fade baked in, per Bowls.kt
        assertTrue(seconds > 6.0 && seconds < 20.0, "expected about twelve seconds, got $seconds")
    }

    @Test
    fun `a file that is not audio decodes to nothing rather than throwing`() {
        // exactly what a git-lfs pointer looks like, which is how this really goes wrong
        val pointer = """
            version https://git-lfs.github.com/spec/v1
            oid sha256:0000000000000000000000000000000000000000000000000000000000000000
            size 97000
        """.trimIndent().toByteArray()

        // null and not an exception: a session must still start, with the tone as its fallback
        assertTrue(decodeMp3(pointer) == null, "a pointer file should not decode as audio")
    }

    @Test
    fun `the low bowl is longer than the high one`() {
        // the whole of how a breath out is told from a breath in: the same recording, played slower.
        // gongRate is commonMain's, so this is checking that the desktop actually applies it
        val pcm = assertNotNull(decodeMp3(bowl(Bowl.PHASE).readBytes()))
        val high = resample(pcm.samples, pcm.rate, pcm.rate, gongRate(Phase.INHALE))
        val low = resample(pcm.samples, pcm.rate, pcm.rate, gongRate(Phase.EXHALE))
        assertTrue(low.size > high.size, "the exhale bowl should be the stretched one")
    }
}
