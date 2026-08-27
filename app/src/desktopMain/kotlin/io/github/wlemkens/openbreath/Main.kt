package io.github.wlemkens.openbreath

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

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

    Window(onCloseRequest = ::exitApplication, state = state, title = "OpenBreath") {
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
