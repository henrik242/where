package no.synth.where

import android.content.Context
import android.net.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import no.synth.where.data.FylkeDownloader
import no.synth.where.data.RegionsRepository
import no.synth.where.data.TrackRepository
import no.synth.where.data.UserPreferences
import no.synth.where.service.LocationTrackingService
import no.synth.where.ui.*

@Composable
fun WhereApp(
    pendingGpxUri: Uri? = null,
    regionsLoadedTrigger: Int = 0,
    onGpxHandled: () -> Unit = {},
    onRegionsLoaded: () -> Unit = {}
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val userPreferences = remember { UserPreferences.getInstance(context) }
    val trackRepository = remember { TrackRepository.getInstance(context) }
    var showSavedPoints by remember { mutableStateOf(true) }
    var viewingPoint by remember { mutableStateOf<no.synth.where.data.SavedPoint?>(null) }
    var hasDownloadedCounties by remember { mutableStateOf(FylkeDownloader.hasCachedData(context)) }
    var isOnline by remember { mutableStateOf(false) }

    DisposableEffect(context) {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                isOnline = true
            }

            override fun onLost(network: Network) {
                isOnline = false
            }
        }

        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.registerNetworkCallback(networkRequest, networkCallback)

        val activeNetwork = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
        isOnline = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

        onDispose {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        }
    }

    LaunchedEffect(isOnline) {
        if (isOnline && !hasDownloadedCounties) {
            val success = FylkeDownloader.downloadAndCacheFylker(context)
            if (success) {
                hasDownloadedCounties = true
                RegionsRepository.reloadRegions(context)
                onRegionsLoaded()
            }
        }
    }

    LaunchedEffect(pendingGpxUri) {
        pendingGpxUri?.let { uri ->
            try {
                val gpxContent = context.contentResolver.openInputStream(uri)?.use {
                    it.bufferedReader().readText()
                }
                if (gpxContent != null) {
                    val importedTrack = trackRepository.importTrack(gpxContent)
                    if (importedTrack != null) {
                        trackRepository.setViewingTrack(importedTrack)
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
                showCountyBorders = userPreferences.showCountyBorders,
                onShowCountyBordersChange = { userPreferences.updateShowCountyBorders(it) },
                showSavedPoints = showSavedPoints,
                onShowSavedPointsChange = { showSavedPoints = it },
                viewingPoint = viewingPoint,
                onClearViewingPoint = { viewingPoint = null },
                regionsLoadedTrigger = regionsLoadedTrigger
            )
        }
        composable("settings") {
            SettingsScreen(
                onBackClick = { navController.popBackStack() },
                onDownloadClick = { navController.navigate("download") },
                onTracksClick = { navController.navigate("tracks") },
                onSavedPointsClick = { navController.navigate("savedpoints") },
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
                onBackClick = { navController.popBackStack() },
                onLayerClick = { layerId ->
                    navController.navigate("download/$layerId")
                }
            )
        }
        composable("download/{layerId}") { backStackEntry ->
            val layerId = backStackEntry.arguments?.getString("layerId") ?: "kartverket"
            LayerRegionsScreen(
                layerId = layerId,
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}

