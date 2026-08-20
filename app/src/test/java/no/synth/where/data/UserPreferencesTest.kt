package no.synth.where.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import no.synth.where.ui.map.NveOverlay
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

    /** Writes launched on a multi-threaded dispatcher used to land out of order, so the init
     * collector read the stale value back over the newer one. Repeated because it is a race. */
    @Test
    fun rapidToggle_keepsTheLastCall() = runBlocking {
        repeat(20) { i ->
            val storeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val store = PreferenceDataStoreFactory.create(
                scope = storeScope,
                produceFile = { tempFolder.newFile("rapid_toggle_$i.preferences_pb") }
            )
            val rapid = UserPreferences(store)
            delay(60)
            rapid.confirmTrackingInfoAndEnable()
            rapid.updateOnlineTrackingEnabled(false)
            delay(150)
            assertEquals(false, rapid.onlineTrackingEnabled.value)
            storeScope.cancel()
        }
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
    fun toggleNveOverlay_selectsThenClears() {
        prefs.toggleNveOverlay(NveOverlay.STEEPNESS)
        assertEquals(NveOverlay.STEEPNESS, prefs.nveOverlay.value)
        prefs.toggleNveOverlay(NveOverlay.STEEPNESS)
        assertNull(prefs.nveOverlay.value)
    }

    @Test
    fun toggleNveOverlay_replacesTheOtherVariant() {
        prefs.toggleNveOverlay(NveOverlay.STEEPNESS_RUNOUT)
        prefs.toggleNveOverlay(NveOverlay.STEEPNESS)
        assertEquals(NveOverlay.STEEPNESS, prefs.nveOverlay.value)
    }

    @Test
    fun nveOverlay_persistsBothChoiceAndOff() = runBlocking {
        val file = tempFolder.newFile("nve_overlay_persist.preferences_pb")
        assertEquals(NveOverlay.STEEPNESS, reload(file) { it.updateNveOverlay(NveOverlay.STEEPNESS) })
        // Turning it off must survive a reload, not fall back to the legacy boolean.
        assertNull(reload(file) { it.updateNveOverlay(null) })
    }

    @Test
    fun nveOverlay_migratesLegacyAvalancheZonesFlag() = runBlocking {
        val file = tempFolder.newFile("nve_overlay_legacy.preferences_pb")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val store = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
        store.edit { it[booleanPreferencesKey("show_avalanche_zones")] = true }
        val migrated = UserPreferences(store)
        delay(200)
        assertEquals(NveOverlay.STEEPNESS_RUNOUT, migrated.nveOverlay.value)
        scope.cancel()
    }

    /** Applies [change], then reopens the same file in a fresh UserPreferences and reads it back. */
    private suspend fun reload(file: java.io.File, change: (UserPreferences) -> Unit): NveOverlay? {
        val writeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val writeStore = PreferenceDataStoreFactory.create(scope = writeScope, produceFile = { file })
        val writer = UserPreferences(writeStore)
        delay(100)
        change(writer)
        delay(200)
        writeScope.cancel()

        val readScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val readStore = PreferenceDataStoreFactory.create(scope = readScope, produceFile = { file })
        val reader = UserPreferences(readStore)
        delay(200)
        return reader.nveOverlay.value.also { readScope.cancel() }
    }

    @Test
    fun showOsmPaths_defaultsOffAndPersistsAcrossReload() = runBlocking {
        assertEquals(false, prefs.showOsmPaths.value)

        val file = tempFolder.newFile("osm_paths_persist.preferences_pb")
        val firstScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val first = UserPreferences(PreferenceDataStoreFactory.create(scope = firstScope, produceFile = { file }))
        delay(100)
        first.updateShowOsmPaths(true)
        delay(200)
        firstScope.cancel()

        val secondScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val second = UserPreferences(PreferenceDataStoreFactory.create(scope = secondScope, produceFile = { file }))
        delay(200)
        assertEquals(true, second.showOsmPaths.value)
        secondScope.cancel()
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
