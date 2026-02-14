package no.synth.where

import android.content.Context
import android.net.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import org.koin.androidx.compose.koinViewModel
import com.google.firebase.crashlytics.FirebaseCrashlytics
import no.synth.where.data.FylkeDownloader
import no.synth.where.data.PlatformFile
import no.synth.where.data.RegionsRepository
import no.synth.where.data.SavedPoint
import no.synth.where.navigation.*
import no.synth.where.service.LocationTrackingService
import no.synth.where.ui.*
import no.synth.where.util.Logger

@Composable
fun WhereApp(
    pendingGpxUri: Uri? = null,
    regionsLoadedTrigger: Int = 0,
    onGpxHandled: () -> Unit = {},
    onRegionsLoaded: () -> Unit = {}
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val viewModel: WhereAppViewModel = koinViewModel()
    val userPreferences = viewModel.userPreferences
    val trackRepository = viewModel.trackRepository
    val showCountyBorders by userPreferences.showCountyBorders.collectAsState()
    val crashReportingEnabled by userPreferences.crashReportingEnabled.collectAsState()
    var showSavedPoints by remember { mutableStateOf(true) }
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

    LaunchedEffect(isOnline, showCountyBorders) {
        if (isOnline && showCountyBorders && !hasDownloadedCounties) {
            val success = FylkeDownloader.downloadAndCacheFylker(PlatformFile(context.cacheDir))
            if (success) {
                hasDownloadedCounties = true
                RegionsRepository.reloadRegions(PlatformFile(context.cacheDir))
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
                Logger.e(e, "GPX import error")
            }
            onGpxHandled()
        }
    }

    NavHost(navController = navController, startDestination = MapRoute) {
        composable<MapRoute> {
            MapScreen(
                onSettingsClick = { navController.navigate(SettingsRoute) },
                showCountyBorders = showCountyBorders,
                onShowCountyBordersChange = { userPreferences.updateShowCountyBorders(it) },
                showSavedPoints = showSavedPoints,
                onShowSavedPointsChange = { showSavedPoints = it },
                viewingPoint = viewingPoint,
                onClearViewingPoint = { viewingPoint = null },
                regionsLoadedTrigger = regionsLoadedTrigger
            )
        }
        composable<SettingsRoute> {
            SettingsScreen(
                onBackClick = { navController.popBackStack() },
                onDownloadClick = { navController.navigate(DownloadRoute) },
                onTracksClick = { navController.navigate(TracksRoute) },
                onSavedPointsClick = { navController.navigate(SavedPointsRoute) },
                onOnlineTrackingClick = { navController.navigate(OnlineTrackingRoute) },
                crashReportingEnabled = crashReportingEnabled,
                onCrashReportingChange = { enabled ->
                    userPreferences.updateCrashReportingEnabled(enabled)
                    FirebaseCrashlytics.getInstance().isCrashlyticsCollectionEnabled = enabled
                }
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
        composable<TracksRoute> {
            TracksScreen(
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
            LayerRegionsScreen(
                layerId = route.layerId,
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}

