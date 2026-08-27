package io.github.wlemkens.openbreath

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * That `Res.readBytes` actually reaches the bowls on this platform, which is a different question
 * from whether they decode.
 *
 * It is the question that made the gong silent on iOS for months: the files were Android resources,
 * and `res/raw` is Android machinery. They live in `commonMain/composeResources` now and are read
 * through the generated `Res` on every platform — but "the resource loader finds them" is a runtime
 * fact on each one separately, and its failure is silence rather than an error.
 */
class BowlResourceTest {

    @Test
    fun `both bowls are reachable through the resource loader`(): Unit = runBlocking {
        val phase = assertNotNull(bowlBytes(Bowl.PHASE), "Res.readBytes could not find ${Bowl.PHASE}")
        val end = assertNotNull(bowlBytes(Bowl.SESSION_END), "Res.readBytes could not find ${Bowl.SESSION_END}")

        // a git-lfs pointer is about 130 bytes, and bowlBytes cannot tell one from an mp3
        assertTrue(phase.size > 10_000, "the phase bowl is ${phase.size} bytes — an LFS pointer?")
        assertTrue(end.size > 10_000, "the end bowl is ${end.size} bytes — an LFS pointer?")

        // and that what the loader hands back is what the decoder takes, which is the whole chain
        assertNotNull(decodeMp3(phase), "the bowl read through Res did not decode")
    }
}
