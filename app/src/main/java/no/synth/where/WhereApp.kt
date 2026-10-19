package no.synth.where

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import androidx.lifecycle.viewmodel.compose.viewModel
import no.synth.where.util.CrashReporter
import no.synth.where.data.SavedPoint
import no.synth.where.data.followGroupLink
import no.synth.where.data.geo.LatLng
import no.synth.where.location.fusedLocationFlow
import no.synth.where.navigation.*
import no.synth.where.service.LocationTrackingService
import no.synth.where.ui.*


@Composable
fun WhereApp(
    pendingGpxUri: Uri? = null,
    pendingImportUrl: String? = null,
    pendingFollowClientIds: List<String> = emptyList(),
    onGpxHandled: () -> Unit = {},
    onImportUrlHandled: () -> Unit = {},
    onFollowHandled: () -> Unit = {}
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val app = context.applicationContext as WhereApplication
    val viewModel: WhereAppViewModel = viewModel { WhereAppViewModel(app.trackRepository, app.userPreferences) }
    val userPreferences = viewModel.userPreferences
    val trackRepository = viewModel.trackRepository
    val offlineModeEnabled by userPreferences.offlineModeEnabled.collectAsState()
    var viewingPoint by remember { mutableStateOf<SavedPoint?>(null) }

    // A live share outlives the process — its deadline is in prefs — but the foreground service
    // that feeds the coordinator does not, so after a force-stop, a reboot or an OEM battery kill
    // the countdown keeps ticking while nothing is sent. Restarting it is gated on RESUMED because
    // a location-typed service may only be started while the app really is in the foreground, and
    // re-running per resume also picks up a location permission granted after launch — without it
    // the service can never start, and there would be nothing to send anyway.
    val shouldTrackLocation by app.onlineTrackingCoordinator.shouldTrackLocation.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(shouldTrackLocation) {
        if (!shouldTrackLocation) return@LaunchedEffect
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            if (granted) LocationTrackingService.start(context)
        }
    }

    LaunchedEffect(pendingFollowClientIds) {
        if (pendingFollowClientIds.isNotEmpty()) {
            followGroupLink(
                app.userPreferences, app.liveTrackingFollower, pendingFollowClientIds,
                app.clientIdManager.getClientId()
            )
            onFollowHandled()
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
            AndroidMapScreen(
                MapScreenNavigation(
                    viewingPoint = viewingPoint,
                    onClearViewingPoint = { viewingPoint = null },
                    onSettingsClick = { navController.navigate(SettingsRoute()) },
                    onOfflineIndicatorClick = { navController.navigate(SettingsRoute(highlightOfflineMode = true)) },
                    onOnlineTrackingClick = { navController.navigate(OnlineTrackingRoute) },
                )
            )
        }
        composable<SettingsRoute> { backStackEntry ->
            val settingsRoute = backStackEntry.toRoute<SettingsRoute>()
            val currentLocale = AppCompatDelegate.getApplicationLocales()
            val currentLanguageTag = if (currentLocale.isEmpty) null else currentLocale.toLanguageTags()
            SettingsScreen(
                userPreferences = userPreferences,
                currentLanguageTag = currentLanguageTag,
                onLanguageSelected = { tag ->
                    val locales = if (tag == null) LocaleListCompat.getEmptyLocaleList()
                    else LocaleListCompat.forLanguageTags(tag)
                    AppCompatDelegate.setApplicationLocales(locales)
                },
                onSponsorClick = { context.startActivity(Intent(Intent.ACTION_VIEW, AppLinks.SPONSOR.toUri())) },
                onBackClick = { navController.popBackStack() },
                onDownloadClick = { navController.navigate(DownloadRoute) },
                onTracksClick = { navController.navigate(TracksRoute()) },
                onSavedPointsClick = { navController.navigate(SavedPointsRoute) },
                onOnlineTrackingClick = { navController.navigate(OnlineTrackingRoute) },
                onAttributionsClick = { navController.navigate(AttributionsRoute) },
                onReleaseNotesClick = { context.startActivity(Intent(Intent.ACTION_VIEW, AppLinks.RELEASE_NOTES.toUri())) },
                onGuideClick = { context.startActivity(Intent(Intent.ACTION_VIEW, AppLinks.GUIDE.toUri())) },
                onCrashReportingChange = { enabled ->
                    userPreferences.updateCrashReportingEnabled(enabled)
                    CrashReporter.setEnabled(enabled)
                },
                highlightOfflineMode = settingsRoute.highlightOfflineMode
            )
        }
        composable<AttributionsRoute> {
            AttributionsScreenContent(
                onBackClick = { navController.popBackStack() }
            )
        }
        composable<OnlineTrackingRoute> {
            AndroidOnlineTrackingScreen(
                onBackClick = { navController.popBackStack() },
                onNavigateToMap = { navController.popBackStack<MapRoute>(false) }
            )
        }
        composable<SavedPointsRoute> {
            AndroidSavedPointsScreen(
                onBackClick = { navController.popBackStack() },
                onShowOnMap = { point ->
                    viewingPoint = point
                    navController.popBackStack<MapRoute>(false)
                }
            )
        }
        composable<TracksRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<TracksRoute>()
            AndroidTracksScreen(
                pendingImportUrl = route.importUrl,
                pendingImportFileUri = route.importFileUri,
                onBackClick = { navController.popBackStack() },
                onShowMap = { navController.popBackStack<MapRoute>(false) },
                onNavigationStarted = {
                    // The foreground service owns the location stream and the persistent
                    // notification while navigating; it self-stops when navigation ends.
                    if (trackRepository.navigation.value != null) {
                        LocationTrackingService.start(context)
                    }
                },
            )
        }
        composable<DownloadRoute> {
            DownloadScreen(
                downloadManager = app.mapDownloadManager,
                userPreferences = userPreferences,
                freeStorageBytes = {
                    runCatching {
                        val dir = context.getExternalFilesDir(null) ?: context.filesDir
                        android.os.StatFs(dir.absolutePath).availableBytes
                    }.getOrDefault(-1L)
                },
                onBackClick = { navController.popBackStack() },
                onLayerClick = { layerId ->
                    navController.navigate(LayerRegionsRoute(layerId))
                },
                onDownloadsClick = { navController.navigate(DownloadQueueRoute) }
            )
        }
        composable<DownloadQueueRoute> {
            DownloadQueueScreen(app.mapDownloadManager, onBackClick = { navController.popBackStack() })
        }
        composable<LayerRegionsRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<LayerRegionsRoute>()
            val lifecycleOwner = LocalLifecycleOwner.current
            val downloadElevationData by userPreferences.downloadElevationData.collectAsState()
            val downloadMaxZoom by userPreferences.downloadMaxZoom.collectAsState()
            var userLocation by remember { mutableStateOf<LatLng?>(null) }
            // Live location so the picker dot tracks movement; works without location permission.
            LaunchedEffect(Unit) {
                lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    fusedLocationFlow(context).collect { userLocation = LatLng(it.latitude, it.longitude) }
                }
            }
            LayerHexMapScreen(
                layerId = route.layerId,
                onBackClick = { navController.popBackStack() },
                downloadManager = app.mapDownloadManager,
                userLocation = userLocation,
                downloadElevationData = downloadElevationData,
                downloadMaxZoom = downloadMaxZoom,
                onOfflineChipClick = { navController.navigate(SettingsRoute(highlightOfflineMode = true)) },
                onQueueChipClick = { navController.navigate(DownloadQueueRoute) },
                offlineModeEnabled = offlineModeEnabled
            )
        }
    }

    OfflineMigrationNotice(userPreferences)
}
