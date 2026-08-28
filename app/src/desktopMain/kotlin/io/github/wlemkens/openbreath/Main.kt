package io.github.wlemkens.openbreath

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import org.jetbrains.compose.resources.decodeToImageBitmap

/**
 * The desktop's way in, and the twin of androidMain's MainActivity and iosMain's
 * MainViewController: provide where the log lives, provide what makes a sound, then hand over.
 *
 * It passes the navigator neither of its optional arguments, exactly as iOS does — there is no
 * reminders screen here and so nothing for the first-run question to arm, and both come out as a
 * menu item that is absent rather than one that opens nothing.
 *
 * `mainClass` in build.gradle.kts names this file's generated class, `…openbreath.MainKt`. Renaming
 * the file renames that class and the packaged app then fails to launch with a message about a
 * missing main class, which is worth knowing before spending an afternoon on it.
 */
fun main() = application {
    /**
     * Phone-shaped, because the layout is: one column of rows, sized for a thumb. Stretched across
     * a 27-inch monitor the cue would sit alone in a field of black with the buttons a hand-span
     * apart, so the window keeps the proportions the screens were drawn for and the reader resizes
     * it if they disagree.
     */
    val state = rememberWindowState(size = DpSize(430.dp, 860.dp))

    Window(
        onCloseRequest = ::exitApplication,
        state = state,
        title = "OpenBreath",
        icon = appIcon(),
    ) {
        // below this a hold of 380 dp starts wrapping labels onto two lines
        window.minimumSize = java.awt.Dimension(380, 560)

        CompositionLocalProvider(
            LocalStore provides store(),
            // the window is the owner every modal file dialog needs, and the reason DesktopPlatform
            // takes one at all: a FileDialog with no owner can open behind the app on Windows
            LocalPlatform provides DesktopPlatform(window),
        ) {
            AppTheme { Breath() }
        }
    }
}

/**
 * The icon on the window, in the taskbar and in the dock — the same cloud the launcher icon and the
 * iOS app icon are drawn from, by IconGen, at the same instant of the same lattice.
 *
 * Separate from the installer's icon and needed as well as it: `nativeDistributions` hands jpackage
 * an `.ico` or an `.icns` for the *packaged* app, and neither reaches `:app:run` or a jar launched
 * by hand. Without this AWT supplies its default cup, which is not cosmetic — it is the icon
 * somebody has to find the window by among a row of them.
 *
 * Null if it cannot be read, which leaves the cup rather than refusing to open a window over a
 * missing png. That is the honest trade for a decoration, and the sort of thing that goes missing
 * for a real reason: `.gitattributes` puts every png in LFS, so a clone without git-lfs has a
 * 130-byte pointer here and no decoder will take it.
 */
private fun appIcon(): Painter? = runCatching {
    // any class in the module will do to reach its classloader; DesktopPlatform is one that exists,
    // where a class declared for the purpose would collide with the Breath composable
    val stream = DesktopPlatform::class.java.getResourceAsStream("/icon.png")!!
    BitmapPainter(stream.use { it.readBytes() }.decodeToImageBitmap())
}.getOrNull()
