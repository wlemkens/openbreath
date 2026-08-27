package io.github.wlemkens.openbreath

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * That the window icon is actually reachable at runtime.
 *
 * `Window(icon = …)` in Main.kt reads `/icon.png` off the classpath, which only works because
 * build.gradle.kts adds `desktopIcons` as a resources directory — a one-line arrangement that
 * nothing else depends on and that no compiler checks. Get it wrong and the window silently wears
 * AWT's default Java cup, which is exactly the symptom that started this.
 *
 * It also catches the LFS pointer, as [Mp3Test] does for the bowls: every png is in LFS, so a clone
 * without git-lfs has 130 bytes here and no decoder will take them.
 */
class AppIconTest {

    @Test
    fun `the window icon is on the classpath and is a real png`() {
        val stream = assertNotNull(
            DesktopPlatform::class.java.getResourceAsStream("/icon.png"),
            "no /icon.png on the classpath — is desktopIcons still a resources srcDir?",
        )
        val bytes = stream.use { it.readBytes() }

        assertTrue(bytes.size > 10_000, "${bytes.size} bytes — an LFS pointer rather than a png?")
        // the eight-byte png signature, so a renamed jpeg or a text file fails here and not in AWT
        val signature = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte())
        assertTrue(
            bytes.take(4).toByteArray().contentEquals(signature),
            "not a png: starts with ${bytes.take(4)}",
        )
    }

    @Test
    fun `the installer icons are there too`() {
        // jpackage reads these from the directory rather than the classpath, but they travel
        // together and a missing one fails the packaging job rather than a test
        for (name in listOf("icon.ico", "icon.icns")) {
            val bytes = assertNotNull(
                DesktopPlatform::class.java.getResourceAsStream("/$name"),
                "no /$name — IconGen writes it under -Picons=true",
            ).use { it.readBytes() }
            assertTrue(bytes.size > 10_000, "$name is ${bytes.size} bytes — an LFS pointer?")
        }
    }
}
