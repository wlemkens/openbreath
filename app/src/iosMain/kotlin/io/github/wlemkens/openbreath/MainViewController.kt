package io.github.wlemkens.openbreath

import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

/**
 * iOS's way in, and the mirror of androidMain's MainActivity.
 *
 * Exported from the OpenBreath framework and wrapped by the Swift app in a
 * UIViewControllerRepresentable. It does what the Activity does — provide where the log lives,
 * provide what makes a sound, then hand over — because after the port there is nothing else for
 * an entry point to do.
 *
 * It passes the navigator neither of its two optional arguments, which is the whole of what iOS
 * lacks: no reminders screen and so nothing for the first-run question to arm. Both come out as a
 * menu item that is simply absent rather than one that opens nothing.
 */
fun MainViewController(): UIViewController = ComposeUIViewController {
    CompositionLocalProvider(
        LocalStore provides store(),
        LocalPlatform provides IosPlatform(),
    ) {
        AppTheme { Breath(Modifier.safeDrawingPadding()) }
    }
}
