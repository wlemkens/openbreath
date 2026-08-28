package io.github.wlemkens.openbreath

import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.ComposeUIViewController
import platform.Foundation.NSProcessInfo
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
    val store = store()
    LaunchedEffect(Unit) { importDemoLog(store) }
    CompositionLocalProvider(
        LocalStore provides store,
        LocalPlatform provides IosPlatform(),
    ) {
        AppTheme { Breath(Modifier.safeDrawingPadding()) }
    }
}

/**
 * The store screenshots, and the only reason this exists.
 *
 * The achievements and milestone screens are worth photographing only with a real streak behind
 * them, and a real one cannot be arranged on purpose — so the harness hands in the same generated
 * log Android's `screenshots.py` imports. Android drives the actual Import button through
 * uiautomator; iOS cannot, because the file would have to be somewhere the document picker can
 * browse to, so the backup arrives in the environment instead and goes through the same
 * [Store.importBackup] the Settings screen calls.
 *
 * `launchEnvironment` is set by an XCUITest and by nothing else: an App Store build has no way to
 * arrive here, since nobody can set a variable on a launch the phone performs. It is inert without
 * it, which is why it can sit in the shipping entry point.
 */
private suspend fun importDemoLog(store: Store) {
    val text = NSProcessInfo.processInfo.environment["OPENBREATH_IMPORT_JSON"] as? String ?: return
    decodeBackup(text)?.let { store.importBackup(it) }
}
