package no.synth.where

import android.Manifest
import android.content.Context
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
import androidx.navigation.compose.rememberNavController
import no.synth.where.data.followGroupLink
import no.synth.where.data.geo.LatLng
import no.synth.where.location.fusedLocationFlow
import no.synth.where.navigation.TracksRoute
import no.synth.where.resources.Res
import no.synth.where.resources.live_location_tracking
import no.synth.where.resources.share_tracking_chooser
import no.synth.where.service.LocationTrackingService
import no.synth.where.ui.*
import no.synth.where.util.Logger
import org.jetbrains.compose.resources.stringResource

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
    val deps = remember(app) {
        ShellDependencies(
            userPreferences = app.userPreferences,
            trackRepository = app.trackRepository,
            savedPointsRepository = app.savedPointsRepository,
            clientIdManager = app.clientIdManager,
            liveTrackingFollower = app.liveTrackingFollower,
            stravaTokenManager = app.stravaTokenManager,
            stravaRouteImporter = app.stravaRouteImporter,
            downloadManager = app.mapDownloadManager,
        )
    }
    val shareChooserTitle = stringResource(Res.string.share_tracking_chooser)
    val shareSubject = stringResource(Res.string.live_location_tracking)
    val platform = remember(context, shareChooserTitle, shareSubject) {
        androidPlatform(context, shareChooserTitle, shareSubject)
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

    WhereNavHost(
        deps = deps,
        platform = platform,
        navController = navController,
        userLocation = { rememberFusedLocation() },
        screens = ShellScreens(
            mapScreen = { nav -> AndroidMapScreen(nav) },
            tracksScreen = { route, onBack, onShowMap ->
                AndroidTracksScreen(
                    pendingImportUrl = route.importUrl,
                    pendingImportFileUri = route.importFileUri,
                    onBackClick = onBack,
                    onShowMap = onShowMap,
                    onNavigationStarted = {
                        // The foreground service owns the location stream and the persistent
                        // notification while navigating; it self-stops when navigation ends.
                        if (app.trackRepository.navigation.value != null) {
                            LocationTrackingService.start(context)
                        }
                    },
                )
            },
            savedPointsScreen = { onBack, onShowOnMap -> AndroidSavedPointsScreen(onBack, onShowOnMap) },
        ),
    )
}

// File picking and track export live in the Android screen slots above, which need activity
// result launchers; the shared fallbacks that would use them are never shown here.
private fun androidPlatform(context: Context, shareChooserTitle: String, shareSubject: String) = ShellPlatform(
    openUrl = { url ->
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
            .onFailure { Logger.e(it, "No app to open %s", url) }
    },
    pickFiles = {},
    exportTrack = {},
    shareText = { text ->
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            putExtra(Intent.EXTRA_SUBJECT, shareSubject)
        }
        context.startActivity(Intent.createChooser(intent, shareChooserTitle))
    },
    onLiveShareStarted = { LocationTrackingService.start(context) },
    freeStorageBytes = {
        runCatching {
            val dir = context.getExternalFilesDir(null) ?: context.filesDir
            android.os.StatFs(dir.absolutePath).availableBytes
        }.getOrDefault(-1L)
    },
    currentLanguageTag = {
        val locales = AppCompatDelegate.getApplicationLocales()
        if (locales.isEmpty) null else locales.toLanguageTags()
    },
    onLanguageSelected = { tag ->
        val locales = if (tag == null) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag)
        AppCompatDelegate.setApplicationLocales(locales)
    },
)

/** Live location so the region picker dot tracks movement; works without location permission. */
@Composable
private fun rememberFusedLocation(): LatLng? {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var location by remember { mutableStateOf<LatLng?>(null) }
    LaunchedEffect(Unit) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            fusedLocationFlow(context).collect { location = LatLng(it.latitude, it.longitude) }
        }
    }
    return location
}
