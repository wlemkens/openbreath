package io.github.wlemkens.openbreath

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch

/**
 * Android's way in, and all that is left of Android in getting there.
 *
 * The one place a Context becomes a Store and an Activity becomes a Platform; nothing below this
 * asks for either. iosMain/MainViewController.kt does the same with its own two.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CompositionLocalProvider(
                LocalStore provides store(),
                LocalPlatform provides AndroidPlatform(this),
            ) {
                AppTheme { Breath(Modifier.safeDrawingPadding()) }
            }
        }
    }
}

/**
 * Which screen is showing.
 *
 * Still Android's, and only because four of its seven destinations are: Settings, Log, Reminders
 * and Support have file pickers, permission prompts and intents in them. When those port, this
 * and its iOS twin collapse into one navigator in commonMain and both of them go.
 */
@Composable
private fun Breath(modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val store = LocalStore.current
    val config by remember { store.configFlow() }.collectAsState(initial = null)
    val goals by remember { store.goalsFlow() }.collectAsState(initial = null)
    var showSettings by remember { mutableStateOf(false) }
    var showLog by remember { mutableStateOf(false) }
    var showReminders by remember { mutableStateOf(false) }
    var showGoals by remember { mutableStateOf(false) }
    var showAchievements by remember { mutableStateOf(false) }
    var showSupport by remember { mutableStateOf(false) }

    // first frames, before DataStore has read. An empty list here would be a lie the goal screen
    // could act on: saving a goal built on "no goals yet" writes over every goal there is
    val current = config ?: return
    val saved = goals ?: return

    // over whichever screen is showing: a milestone is worth interrupting a settings tweak for
    MilestoneWatch(current, saved)

    when {
        showSettings -> SettingsScreen(
            config = current,
            onChange = { scope.launch { store.saveConfig(it) } },
            onBack = { showSettings = false },
            modifier = modifier,
        )

        showLog -> LogScreen(onBack = { showLog = false }, modifier = modifier)

        showGoals -> GoalsScreen(
            goals = saved,
            onChange = { scope.launch { store.saveGoals(it) } },
            onBack = { showGoals = false },
            modifier = modifier,
        )

        showAchievements ->
            AchievementsScreen(saved, onBack = { showAchievements = false }, modifier = modifier)

        showReminders -> RemindersScreen(onBack = { showReminders = false }, modifier = modifier)

        showSupport -> SupportScreen(onBack = { showSupport = false }, modifier = modifier)

        else -> SessionScreen(
            current,
            onOpenGoals = { showGoals = true },
            onOpenAchievements = { showAchievements = true },
            onOpenSettings = { showSettings = true },
            onOpenLog = { showLog = true },
            onOpenReminders = { showReminders = true },
            onOpenSupport = { showSupport = true },
            modifier = modifier,
        )
    }
}
