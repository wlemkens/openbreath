package io.github.wlemkens.openbreath

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier

/**
 * Android's way in, and all that is left of Android in getting there.
 *
 * The one place a Context becomes a Store and an Activity becomes a Platform; nothing below this
 * asks for either. iosMain/MainViewController.kt and desktopMain/Main.kt do the same with theirs.
 *
 * The navigator this hands to is commonMain's now. Android is the only platform that passes it
 * anything: the reminders screen and the scheduler the first-run question arms, both of which are
 * AlarmManager's and so have no counterpart elsewhere yet.
 */
class MainActivity : ComponentActivity() {

    // a field and not a local: the launchers inside register with the Activity, and registering
    // has to happen before it is started — see AndroidFiles
    private val files = AndroidFiles(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CompositionLocalProvider(
                LocalStore provides store(),
                LocalPlatform provides AndroidPlatform(this, files),
            ) {
                AppTheme {
                    Breath(
                        modifier = Modifier.safeDrawingPadding(),
                        reminders = { padding, onBack -> RemindersScreen(onBack, padding) },
                        onReminder = rememberReminderScheduler(),
                    )
                }
            }
        }
    }
}
