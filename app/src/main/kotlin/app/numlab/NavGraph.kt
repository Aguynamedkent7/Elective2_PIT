package app.numlab

import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.numlab.ui.history.HistoryNav
import app.numlab.ui.history.HistoryScreen
import app.numlab.ui.motion.LocalReduceMotion
import app.numlab.ui.motion.Motion
import app.numlab.ui.workbench.WorkbenchRoute
import app.numlab.ui.workbench.WorkbenchViewModel

/** `home` (graph canvas is an overlay there) and `history` as a slide-up sheet (PLAN §6.1, §7.3). */
@Composable
fun NumLabNavGraph(workbench: WorkbenchViewModel, decimals: Int, openSettings: () -> Unit) {
    val nav = rememberNavController()
    val reduce = LocalReduceMotion.current
    NavHost(
        nav, startDestination = "home",
        enterTransition = { if (reduce) fadeIn(tween(120)) else slideInVertically(tween(440, easing = Motion.Decelerate)) { it } },
        exitTransition = { if (reduce) fadeOut(tween(120)) else ExitTransition.KeepUntilTransitionsFinished },
        popEnterTransition = { fadeIn(tween(if (reduce) 120 else 0)) },
        popExitTransition = { if (reduce) fadeOut(tween(120)) else slideOutVertically(tween(380, easing = Motion.Accelerate)) { it } },
    ) {
        composable("home") { WorkbenchRoute(workbench, { nav.navigate("history") { launchSingleTop = true } }, openSettings) }
        composable("history") {
            HistoryScreen(hiltViewModel(), decimals, HistoryNav(
                back = { nav.popBackStack() },
                openInputs = { workbench.load(it); nav.popBackStack() },
                openGraph = { workbench.load(it); nav.popBackStack(); workbench.openGraphWhenReady() },
            ))
        }
    }
}
