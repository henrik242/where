package no.synth.where

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import no.synth.where.data.UserPreferences
import no.synth.where.ui.DownloadScreen
import no.synth.where.ui.MapScreen
import no.synth.where.ui.SettingsScreen

@Composable
fun WhereApp() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val userPreferences = remember { UserPreferences.getInstance(context) }

    NavHost(navController = navController, startDestination = "map") {
        composable("map") {
            MapScreen(
                onSettingsClick = { navController.navigate("settings") },
                showCountyBorders = userPreferences.showCountyBorders
            )
        }
        composable("settings") {
            SettingsScreen(
                onBackClick = { navController.popBackStack() },
                onDownloadClick = { navController.navigate("download") },
                showCountyBorders = userPreferences.showCountyBorders,
                onShowCountyBordersChange = { userPreferences.updateShowCountyBorders(it) }
            )
        }
        composable("download") {
            DownloadScreen(
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}

