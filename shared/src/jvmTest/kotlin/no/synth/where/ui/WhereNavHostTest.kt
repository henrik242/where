package no.synth.where.ui

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import no.synth.where.data.ClientIdManager
import no.synth.where.data.DownloadEngine
import no.synth.where.data.DownloadQueueManager
import no.synth.where.data.InMemorySavedPointDao
import no.synth.where.data.InMemoryTrackDao
import no.synth.where.data.LiveTrackingFollower
import no.synth.where.data.MapDownloadManager
import no.synth.where.data.PlatformFile
import no.synth.where.data.QueuedDownload
import no.synth.where.data.SavedPoint
import no.synth.where.data.SavedPointsRepository
import no.synth.where.data.StravaApiClient
import no.synth.where.data.StravaRouteImporter
import no.synth.where.data.StravaTokenManager
import no.synth.where.data.TrackRepository
import no.synth.where.data.UserPreferences
import no.synth.where.data.geo.LatLng
import no.synth.where.navigation.SavedPointsRoute
import no.synth.where.navigation.SettingsRoute
import no.synth.where.navigation.TracksRoute
import okio.Path.Companion.toOkioPath
import kotlinx.coroutines.cancel
import java.nio.file.Files
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@OptIn(ExperimentalTestApi::class)
class WhereNavHostTest {

    private val dir = Files.createTempDirectory("navhost").toFile()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val defaultLocale = Locale.getDefault()

    // The assertions match English strings; the app also ships Norwegian.
    @BeforeTest
    fun setUp() = Locale.setDefault(Locale.ENGLISH)

    @AfterTest
    fun tearDown() {
        Locale.setDefault(defaultLocale)
        scope.cancel()
        dir.deleteRecursively()
    }

    private fun deps(): ShellDependencies {
        val files = PlatformFile(dir)
        fun store(name: String) = PreferenceDataStoreFactory.createWithPath { dir.resolve("$name.preferences_pb").toOkioPath() }
        val http = HttpClient(MockEngine { respondError(HttpStatusCode.ServiceUnavailable) })
        val prefs = UserPreferences(store("user"), hydrateFromDisk = false)
        val tracks = TrackRepository(files, InMemoryTrackDao())
        val tokens = StravaTokenManager(prefs, http)
        val idle = object : DownloadEngine {
            override suspend fun download(item: QueuedDownload, onProgress: (Int, Int) -> Unit) = false
            override fun cancelActive() {}
        }
        return ShellDependencies(
            userPreferences = prefs,
            trackRepository = tracks,
            savedPointsRepository = SavedPointsRepository(files, InMemorySavedPointDao()),
            clientIdManager = ClientIdManager(store("client")),
            liveTrackingFollower = LiveTrackingFollower("http://localhost:1"),
            stravaTokenManager = tokens,
            stravaRouteImporter = StravaRouteImporter(StravaApiClient(http), tokens, tracks),
            downloadManager = MapDownloadManager(DownloadQueueManager(idle, scope)),
        )
    }

    private val platform = ShellPlatform(
        openUrl = {}, pickFiles = {}, exportTrack = {}, shareText = {}, freeStorageBytes = { -1L },
    )

    private val point = SavedPoint(id = "p1", name = "Cabin", latLng = LatLng(60.0, 10.0))

    @Test
    fun settingsOpensFromTheMapAndBackReturnsToIt() = runComposeUiTest {
        val deps = deps()
        setContent {
            WhereNavHost(
                deps = deps,
                platform = platform,
                screens = ShellScreens(mapScreen = { nav -> Button(onClick = nav.onSettingsClick) { Text("map") } }),
            )
        }
        onNodeWithText("map").performClick()
        onNodeWithText("Settings").assertExists()
        onNodeWithContentDescription("Back").performClick()
        onNodeWithText("map").assertExists()
    }

    @Test
    fun showOnMapHandsThePointToTheMapAndPopsBackToIt() = runComposeUiTest {
        val deps = deps()
        lateinit var navController: NavHostController
        var viewing: SavedPoint? = null
        setContent {
            navController = rememberNavController()
            WhereNavHost(
                deps = deps,
                platform = platform,
                navController = navController,
                screens = ShellScreens(
                    mapScreen = { nav ->
                        viewing = nav.viewingPoint
                        Text("map")
                    },
                    savedPointsScreen = { _, onShowOnMap -> Button(onClick = { onShowOnMap(point) }) { Text("points") } },
                ),
            )
        }
        runOnIdle { navController.navigate(SettingsRoute()) }
        runOnIdle { navController.navigate(SavedPointsRoute) }
        onNodeWithText("points").performClick()
        onNodeWithText("map").assertExists()
        assertEquals(point, viewing)
        // Popped all the way to the map, not just one step back to settings.
        runOnIdle { assertFalse(navController.popBackStack()) }
    }

    @Test
    fun tracksRouteArgumentsReachTheTracksSlot() = runComposeUiTest {
        val deps = deps()
        lateinit var navController: NavHostController
        var importUrl: String? = null
        setContent {
            navController = rememberNavController()
            WhereNavHost(
                deps = deps,
                platform = platform,
                navController = navController,
                screens = ShellScreens(
                    mapScreen = { Text("map") },
                    tracksScreen = { route, _, onShowMap ->
                        importUrl = route.importUrl
                        Button(onClick = onShowMap) { Text("tracks") }
                    },
                ),
            )
        }
        runOnIdle { navController.navigate(TracksRoute(importUrl = "https://www.komoot.com/tour/1")) }
        onNodeWithText("tracks").assertExists()
        assertEquals("https://www.komoot.com/tour/1", importUrl)
        onNodeWithText("tracks").performClick()
        onNodeWithText("map").assertExists()
    }

    @Test
    fun withoutPlatformSlotsTheSharedScreensAreShown() = runComposeUiTest {
        val deps = deps()
        lateinit var navController: NavHostController
        setContent {
            navController = rememberNavController()
            WhereNavHost(
                deps = deps,
                platform = platform,
                navController = navController,
                screens = ShellScreens(mapScreen = { Text("map") }),
            )
        }
        runOnIdle { navController.navigate(SavedPointsRoute) }
        onNodeWithText("Saved Points").assertExists()
        onNodeWithContentDescription("Back").performClick()
        onNodeWithText("map").assertExists()
    }
}
