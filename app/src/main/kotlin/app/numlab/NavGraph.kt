package app.numlab

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.numlab.ui.canvas.CanvasScreen
import app.numlab.ui.history.HistoryScreen
import app.numlab.ui.home.HomeScreen

/** `home` → `canvas/{module}` and `home` → `history` (PLAN §6.1). Custom transitions: Phase 6. */
@Composable
fun NumLabNavGraph() {
    val nav = rememberNavController()
    NavHost(nav, startDestination = "home") {
        composable("home") { HomeScreen(onOpenHistory = { nav.navigate("history") }) }
        composable("canvas/{module}") { entry ->
            CanvasScreen(entry.arguments?.getString("module").orEmpty(), onBack = nav::popBackStack)
        }
        composable("history") { HistoryScreen(onBack = nav::popBackStack) }
    }
}
