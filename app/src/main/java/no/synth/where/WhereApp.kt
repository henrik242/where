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

    LaunchedEffect(pendingGpxUri) {
        pendingGpxUri?.let { uri ->
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val gpxContent = inputStream?.bufferedReader()?.use { it.readText() }
                inputStream?.close()

                if (gpxContent != null) {
                    val track = Track.fromGPX(gpxContent)
                    if (track != null) {
                        trackRepository.importTrack(gpxContent)
                        trackRepository.setViewingTrack(track)
                        navController.navigate("map") {
                            popUpTo("map") { inclusive = false }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            onGpxHandled()
        }
    }

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
                onTracksClick = { navController.navigate("tracks") },
                showCountyBorders = userPreferences.showCountyBorders,
                onShowCountyBordersChange = { userPreferences.updateShowCountyBorders(it) }
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

