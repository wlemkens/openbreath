package io.github.wlemkens.openbreath

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
import androidx.compose.ui.window.ComposeUIViewController
import kotlinx.coroutines.launch
import platform.UIKit.UIViewController

/**
 * iOS's way in, and the mirror of androidMain's MainActivity.
 *
 * Exported from the OpenBreath framework and wrapped by the Swift app in a
 * UIViewControllerRepresentable. It does what the Activity does — provide where the log lives,
 * provide what makes a sound, then hand over — because after the port there is nothing else for
 * an entry point to do.
 */
fun MainViewController(): UIViewController = ComposeUIViewController {
    CompositionLocalProvider(
        LocalStore provides store(),
        LocalPlatform provides IosPlatform(),
    ) {
        AppTheme { IosBreath(Modifier.safeDrawingPadding()) }
    }
}

/**
 * Which screen is showing, over the ones that exist here.
 *
 * The twin of androidMain's Breath, and temporary in the same way. Settings, Log, Reminders and
 * Support are not reachable because they have not been ported, and passing null for them leaves
 * them out of the menu rather than offering an item that does nothing. When those four move to
 * commonMain, this file and its Android twin become one navigator and both are deleted.
 *
 * Everything else is already shared: the session, the cue, the goals, the achievements and the
 * milestone that interrupts them are all commonMain and are reached from here unchanged.
 */
@Composable
private fun IosBreath(modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val store = LocalStore.current
    val config by remember { store.configFlow() }.collectAsState(initial = null)
    val goals by remember { store.goalsFlow() }.collectAsState(initial = null)
    var showGoals by remember { mutableStateOf(false) }
    var showAchievements by remember { mutableStateOf(false) }

    // the same null gate the Android side has, and for the same reason: a list built on a value
    // that has not loaded yet is a lie the goal screen would act on
    val current = config ?: return
    val saved = goals ?: return

    MilestoneWatch(current, saved)

    when {
        showGoals -> GoalsScreen(
            goals = saved,
            onChange = { scope.launch { store.saveGoals(it) } },
            onBack = { showGoals = false },
            modifier = modifier,
        )

        showAchievements ->
            AchievementsScreen(saved, onBack = { showAchievements = false }, modifier = modifier)

        else -> SessionScreen(
            current,
            onOpenGoals = { showGoals = true },
            onOpenAchievements = { showAchievements = true },
            modifier = modifier,
        )
    }
}
