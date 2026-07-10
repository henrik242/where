package no.synth.where.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import no.synth.where.data.geo.CoordFormat
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class UserPreferencesTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var scope: CoroutineScope
    private lateinit var prefs: UserPreferences

    @Before
    fun setUp() = runBlocking {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val dataStore = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { tempFolder.newFile("user_prefs_test.preferences_pb") }
        )
        prefs = UserPreferences(dataStore)
        // Allow init collector to complete initial read
        delay(100)
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun defaults_crashReportingEnabledIsTrue() {
        assertEquals(true, prefs.crashReportingEnabled.value)
    }

    @Test
    fun defaults_onlineTrackingEnabledIsFalse() {
        assertEquals(false, prefs.onlineTrackingEnabled.value)
    }

    @Test
    fun updateCrashReportingEnabled_updatesStateFlow() {
        prefs.updateCrashReportingEnabled(false)
        assertEquals(false, prefs.crashReportingEnabled.value)
    }

    @Test
    fun updateOnlineTrackingEnabled_updatesStateFlow() {
        prefs.updateOnlineTrackingEnabled(true)
        assertEquals(true, prefs.onlineTrackingEnabled.value)
    }

    @Test
    fun defaults_hasSeenTrackingInfoIsFalse() {
        assertEquals(false, prefs.hasSeenTrackingInfo.value)
    }

    @Test
    fun confirmTrackingInfoAndEnable_marksSeenAndEnables() {
        prefs.confirmTrackingInfoAndEnable()
        assertEquals(true, prefs.hasSeenTrackingInfo.value)
        assertEquals(true, prefs.onlineTrackingEnabled.value)
    }

    @Test
    fun confirmTrackingInfoAndEnable_subsequentToggleSkipsDialog() {
        prefs.confirmTrackingInfoAndEnable()
        prefs.updateOnlineTrackingEnabled(false)
        assertEquals(false, prefs.onlineTrackingEnabled.value)
        assertEquals(true, prefs.hasSeenTrackingInfo.value)
    }

    @Test
    fun defaults_downloadMaxZoomIsStandard() {
        assertEquals(12, UserPreferences.DEFAULT_DOWNLOAD_MAX_ZOOM)
        assertEquals(UserPreferences.DEFAULT_DOWNLOAD_MAX_ZOOM, prefs.downloadMaxZoom.value)
    }

    @Test
    fun updateDownloadMaxZoom_updatesStateFlow() {
        prefs.updateDownloadMaxZoom(16)
        assertEquals(16, prefs.downloadMaxZoom.value)
    }

    @Test
    fun defaults_coordFormatIsLatLng() {
        assertEquals(CoordFormat.LATLNG, prefs.coordFormat.value)
    }

    @Test
    fun updateCoordFormat_updatesStateFlow() {
        prefs.updateCoordFormat(CoordFormat.UTM)
        assertEquals(CoordFormat.UTM, prefs.coordFormat.value)
        prefs.updateCoordFormat(CoordFormat.DMS)
        assertEquals(CoordFormat.DMS, prefs.coordFormat.value)
    }

    @Test
    fun updateCoordFormat_persistsAcrossReload() = runBlocking {
        val file = tempFolder.newFile("coord_format_persist.preferences_pb")
        val firstScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val firstStore = PreferenceDataStoreFactory.create(scope = firstScope, produceFile = { file })
        val first = UserPreferences(firstStore)
        delay(100)
        first.updateCoordFormat(CoordFormat.MGRS)
        delay(200)
        firstScope.cancel()

        val secondScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val secondStore = PreferenceDataStoreFactory.create(scope = secondScope, produceFile = { file })
        val second = UserPreferences(secondStore)
        delay(200)
        assertEquals(CoordFormat.MGRS, second.coordFormat.value)
        secondScope.cancel()
    }

}
