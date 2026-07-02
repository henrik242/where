package no.synth.where

import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.core.os.LocaleListCompat
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
    pendingFollowClientId: String? = null,
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

    LaunchedEffect(pendingFollowClientId) {
        pendingFollowClientId?.let { clientId ->
            app.userPreferences.updateFollowedClientId(clientId)
            app.liveTrackingFollower.follow(clientId)
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
                onContinueTrack = { track ->
                    trackRepository.continueTrack(track)
                    LocationTrackingService.start(context)
                    navController.popBackStack<MapRoute>(false)
                },
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

