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

    LaunchedEffect(offlineModeEnabled) {
        org.maplibre.android.MapLibre.setConnected(!offlineModeEnabled)
    }

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

    // A group link shows that group, so it replaces the followed set rather than appending to it.
    LaunchedEffect(pendingFollowClientIds) {
        if (pendingFollowClientIds.isNotEmpty()) {
            val self = app.clientIdManager.getClientId()
            app.userPreferences.setFollowedClientIds(pendingFollowClientIds.filter { it != self })
            app.liveTrackingFollower.follow(app.userPreferences.followedClientIds.value)
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
            MapScreen(
                onSettingsClick = { navController.navigate(SettingsRoute()) },
                onOfflineSettingsClick = { navController.navigate(SettingsRoute(highlightOfflineMode = true)) },
                onOnlineTrackingSettingsClick = { navController.navigate(OnlineTrackingRoute) },
                viewingPoint = viewingPoint,
                onClearViewingPoint = { viewingPoint = null }
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
                onSponsorClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, "https://buymeacoffee.com/henrik242".toUri()))
                },
                onBackClick = { navController.popBackStack() },
                onDownloadClick = { navController.navigate(DownloadRoute) },
                onTracksClick = { navController.navigate(TracksRoute()) },
                onSavedPointsClick = { navController.navigate(SavedPointsRoute) },
                onOnlineTrackingClick = { navController.navigate(OnlineTrackingRoute) },
                onAttributionsClick = { navController.navigate(AttributionsRoute) },
                onReleaseNotesClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, "https://github.com/henrik242/where/blob/main/RELEASES.md".toUri()))
                },
                onGuideClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, "https://where.synth.no/guide".toUri()))
                },
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
            OnlineTrackingScreen(
                onBackClick = { navController.popBackStack() },
                onNavigateToMap = { navController.popBackStack<MapRoute>(false) }
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
                onShowTrackOnMap = { track ->
                    trackRepository.addViewingTrack(track)
                    navController.popBackStack<MapRoute>(false)
                },
                onShowTracksOnMap = { tracks ->
                    trackRepository.setViewingTracks(tracks)
                    navController.popBackStack<MapRoute>(false)
                },
                onNavigateTrack = { track ->
                    trackRepository.startNavigation(track, reversed = false)
                    // The foreground service owns the location stream and the persistent
                    // notification while navigating; it self-stops when navigation ends.
                    if (trackRepository.navigation.value != null) {
                        LocationTrackingService.start(context)
                    }
                    navController.popBackStack<MapRoute>(false)
                },
                onCropTrack = { track ->
                    trackRepository.addViewingTrack(track)
                    trackRepository.startCrop(track.id)
                    navController.popBackStack<MapRoute>(false)
                }
            )
        }
        composable<DownloadRoute> {
            DownloadScreen(
                onBackClick = { navController.popBackStack() },
                onLayerClick = { layerId ->
                    navController.navigate(LayerRegionsRoute(layerId))
                },
                onDownloadsClick = { navController.navigate(DownloadQueueRoute) }
            )
        }
        composable<DownloadQueueRoute> {
            DownloadQueueScreen(onBackClick = { navController.popBackStack() })
        }
        composable<LayerRegionsRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<LayerRegionsRoute>()
            LayerHexMapScreen(
                layerId = route.layerId,
                onBackClick = { navController.popBackStack() },
                onOfflineChipClick = { navController.navigate(SettingsRoute(highlightOfflineMode = true)) },
                onQueueChipClick = { navController.navigate(DownloadQueueRoute) },
                offlineModeEnabled = offlineModeEnabled
            )
        }
    }
}

