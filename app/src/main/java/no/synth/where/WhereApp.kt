package no.synth.where

import android.content.Context
import android.net.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import androidx.lifecycle.viewmodel.compose.viewModel
import no.synth.where.util.CrashReporter
import no.synth.where.data.FylkeDownloader
import no.synth.where.data.PlatformFile
import no.synth.where.data.RegionsRepository
import no.synth.where.data.SavedPoint
import no.synth.where.navigation.*
import no.synth.where.service.LocationTrackingService
import no.synth.where.ui.*


@Composable
fun WhereApp(
    pendingGpxUri: Uri? = null,
    pendingImportUrl: String? = null,
    regionsLoadedTrigger: Int = 0,
    onGpxHandled: () -> Unit = {},
    onImportUrlHandled: () -> Unit = {},
    onRegionsLoaded: () -> Unit = {}
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val app = context.applicationContext as WhereApplication
    val viewModel: WhereAppViewModel = viewModel { WhereAppViewModel(app.trackRepository, app.userPreferences) }
    val userPreferences = viewModel.userPreferences
    val trackRepository = viewModel.trackRepository
    val showCountyBorders by userPreferences.showCountyBorders.collectAsState()
    val offlineModeEnabled by userPreferences.offlineModeEnabled.collectAsState()
    var viewingPoint by remember { mutableStateOf<SavedPoint?>(null) }
    var hasDownloadedCounties by remember { mutableStateOf(FylkeDownloader.hasCachedData(PlatformFile(context.cacheDir))) }
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

    LaunchedEffect(offlineModeEnabled) {
        org.maplibre.android.MapLibre.setConnected(!offlineModeEnabled)
    }

    LaunchedEffect(isOnline, showCountyBorders, offlineModeEnabled) {
        if (isOnline && !offlineModeEnabled && showCountyBorders && !hasDownloadedCounties) {
            val success = FylkeDownloader.downloadAndCacheFylker(PlatformFile(context.cacheDir))
            if (success) {
                hasDownloadedCounties = true
                RegionsRepository.reloadRegions(PlatformFile(context.cacheDir))
                onRegionsLoaded()
            }
        }
    }

    LaunchedEffect(pendingImportUrl) {
        pendingImportUrl?.let { url ->
            navController.navigate(TracksRoute(importUrl = url))
            onImportUrlHandled()
        }
    }

    LaunchedEffect(pendingGpxUri) {
        pendingGpxUri?.let { uri ->
            navController.navigate(TracksRoute(importFileUri = uri.toString()))
            onGpxHandled()
        }
    }

    NavHost(navController = navController, startDestination = MapRoute) {
        composable<MapRoute> {
            MapScreen(
                onSettingsClick = { navController.navigate(SettingsRoute()) },
                onOfflineSettingsClick = { navController.navigate(SettingsRoute(highlightOfflineMode = true)) },
                onOnlineTrackingSettingsClick = { navController.navigate(OnlineTrackingRoute) },
                viewingPoint = viewingPoint,
                onClearViewingPoint = { viewingPoint = null },
                regionsLoadedTrigger = regionsLoadedTrigger
            )
        }
        composable<SettingsRoute> { backStackEntry ->
            val settingsRoute = backStackEntry.toRoute<SettingsRoute>()
            SettingsScreen(
                highlightOfflineMode = settingsRoute.highlightOfflineMode,
                onBackClick = { navController.popBackStack() },
                onDownloadClick = { navController.navigate(DownloadRoute) },
                onTracksClick = { navController.navigate(TracksRoute()) },
                onSavedPointsClick = { navController.navigate(SavedPointsRoute) },
                onOnlineTrackingClick = { navController.navigate(OnlineTrackingRoute) },
                onAttributionsClick = { navController.navigate(AttributionsRoute) },
                userPreferences = userPreferences,
                onCrashReportingChange = { enabled ->
                    userPreferences.updateCrashReportingEnabled(enabled)
                    CrashReporter.setEnabled(enabled)
                }
            )
        }
        composable<AttributionsRoute> {
            AttributionsScreenContent(
                onBackClick = { navController.popBackStack() }
            )
        }
        composable<OnlineTrackingRoute> {
            OnlineTrackingScreen(
                onBackClick = { navController.popBackStack() }
            )
        }
        composable<SavedPointsRoute> {
            SavedPointsScreen(
                onBackClick = { navController.popBackStack() },
                onShowOnMap = { point ->
                    viewingPoint = point
                    navController.popBackStack<MapRoute>(false)
                }
            )
        }
        composable<TracksRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<TracksRoute>()
            TracksScreen(
                pendingImportUrl = route.importUrl,
                pendingImportFileUri = route.importFileUri,
                onBackClick = { navController.popBackStack() },
                onContinueTrack = { track ->
                    trackRepository.continueTrack(track)
                    LocationTrackingService.start(context)
                    navController.popBackStack<MapRoute>(false)
                },
                onShowTrackOnMap = { track ->
                    trackRepository.setViewingTrack(track)
                    navController.popBackStack<MapRoute>(false)
                }
            )
        }
        composable<DownloadRoute> {
            DownloadScreen(
                onBackClick = { navController.popBackStack() },
                onLayerClick = { layerId ->
                    navController.navigate(LayerRegionsRoute(layerId))
                }
            )
        }
        composable<LayerRegionsRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<LayerRegionsRoute>()
            LayerHexMapScreen(
                layerId = route.layerId,
                onBackClick = { navController.popBackStack() },
                onOfflineChipClick = { navController.navigate(SettingsRoute(highlightOfflineMode = true)) },
                offlineModeEnabled = offlineModeEnabled
            )
        }
    }
}

