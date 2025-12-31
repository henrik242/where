package no.synth.where

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import no.synth.where.ui.DownloadScreen
import no.synth.where.ui.MapScreen

@Composable
fun WhereApp() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "map") {
        composable("map") {
            MapScreen(
                onDownloadClick = { navController.navigate("download") }
            )
        }
        composable("download") {
            DownloadScreen(
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}

