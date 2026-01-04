package no.synth.where

import android.net.Uri
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import no.synth.where.data.Track
import no.synth.where.data.TrackRepository
import no.synth.where.data.UserPreferences
import no.synth.where.service.LocationTrackingService
import no.synth.where.ui.DownloadScreen
import no.synth.where.ui.MapScreen
import no.synth.where.ui.SavedPointsScreen
import no.synth.where.ui.SettingsScreen
import no.synth.where.ui.TracksScreen

@Composable
fun WhereApp(
    pendingGpxUri: Uri? = null,
    onGpxHandled: () -> Unit = {}
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val userPreferences = remember { UserPreferences.getInstance(context) }
    val trackRepository = remember { TrackRepository.getInstance(context) }
    var showSavedPoints by remember { mutableStateOf(true) }
    var viewingPoint by remember { mutableStateOf<no.synth.where.data.SavedPoint?>(null) }

    LaunchedEffect(pendingGpxUri) {
        // ...existing code...
    }

    NavHost(navController = navController, startDestination = "map") {
        composable("map") {
            MapScreen(
                onSettingsClick = { navController.navigate("settings") },
                showCountyBorders = userPreferences.showCountyBorders,
                onShowCountyBordersChange = { userPreferences.updateShowCountyBorders(it) },
                showSavedPoints = showSavedPoints,
                onShowSavedPointsChange = { showSavedPoints = it },
                viewingPoint = viewingPoint,
                onClearViewingPoint = { viewingPoint = null }
            )
        }
        composable("settings") {
            SettingsScreen(
                onBackClick = { navController.popBackStack() },
                onDownloadClick = { navController.navigate("download") },
                onTracksClick = { navController.navigate("tracks") },
                onSavedPointsClick = { navController.navigate("savedpoints") },
                showCountyBorders = userPreferences.showCountyBorders,
                onShowCountyBordersChange = { userPreferences.updateShowCountyBorders(it) }
            )
        }
        composable("savedpoints") {
            SavedPointsScreen(
                onBackClick = { navController.popBackStack() },
                onShowOnMap = { point ->
                    viewingPoint = point
                    navController.popBackStack("map", false)
                }
            )
        }
        composable("tracks") {
            TracksScreen(
                onBackClick = { navController.popBackStack() },
                onContinueTrack = { track ->
                    trackRepository.continueTrack(track)
                    LocationTrackingService.start(context)
                    navController.popBackStack("map", false)
                },
                onShowTrackOnMap = { track ->
                    trackRepository.setViewingTrack(track)
                    navController.popBackStack("map", false)
                }
            )
        }
        composable("download") {
            DownloadScreen(
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}

