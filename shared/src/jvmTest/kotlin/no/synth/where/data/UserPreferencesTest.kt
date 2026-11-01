package no.synth.where.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import no.synth.where.ui.map.NveOverlay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import no.synth.where.data.geo.CoordFormat
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UserPreferencesTest {

    private companion object {
        // Only bounds a hang; the awaits return as soon as the work is done.
        const val TIMEOUT = 10_000L
    }

    private val dir = File(System.getProperty("java.io.tmpdir"), "user-prefs-${System.nanoTime()}").apply { mkdirs() }

    private fun newFile(name: String) = File(dir, name)

    private lateinit var scope: CoroutineScope
    private lateinit var prefs: UserPreferences

    @BeforeTest
    fun setUp() = runBlocking {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val dataStore = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { newFile("user_prefs_test.preferences_pb") }
        )
        prefs = UserPreferences(dataStore)
        withTimeout(TIMEOUT) { prefs.awaitHydrated() }
    }

    @AfterTest
    fun tearDown() = runBlocking {
        // Let pending writes land, then release the file before deleting it.
        withTimeout(TIMEOUT) { prefs.awaitPersisted() }
        scope.coroutineContext.job.cancelAndJoin()
        dir.deleteRecursively()
        Unit
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
                produceFile = { newFile("rapid_toggle_$i.preferences_pb") }
            )
            val rapid = UserPreferences(store)
            withTimeout(TIMEOUT) { rapid.awaitHydrated() }
            rapid.confirmTrackingInfoAndEnable()
            rapid.updateOnlineTrackingEnabled(false)
            withTimeout(TIMEOUT) { rapid.awaitPersisted() }
            assertEquals(false, store.data.first()[booleanPreferencesKey("online_tracking_enabled")])
            assertEquals(false, rapid.onlineTrackingEnabled.value)
            storeScope.coroutineContext.job.cancelAndJoin()
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
        val file = newFile("nve_overlay_persist.preferences_pb")
        assertEquals(NveOverlay.STEEPNESS, reload(file) { it.updateNveOverlay(NveOverlay.STEEPNESS) }.nveOverlay.value)
        // Turning it off must survive a reload, not fall back to the legacy boolean.
        assertNull(reload(file) { it.updateNveOverlay(null) }.nveOverlay.value)
    }

    @Test
    fun nveOverlay_migratesLegacyAvalancheZonesFlag() = runBlocking {
        val file = newFile("nve_overlay_legacy.preferences_pb")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val store = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
        store.edit { it[booleanPreferencesKey("show_avalanche_zones")] = true }
        val migrated = UserPreferences(store)
        withTimeout(TIMEOUT) { migrated.awaitHydrated() }
        assertEquals(NveOverlay.STEEPNESS_RUNOUT, migrated.nveOverlay.value)
        scope.coroutineContext.job.cancelAndJoin()
    }

    /** Applies [change], then reopens the same file in a fresh UserPreferences and returns it. */
    private suspend fun reload(file: File, change: (UserPreferences) -> Unit): UserPreferences {
        val writeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val writeStore = PreferenceDataStoreFactory.create(scope = writeScope, produceFile = { file })
        val writer = UserPreferences(writeStore)
        withTimeout(TIMEOUT) { writer.awaitHydrated() }
        change(writer)
        // Cancelling the store scope drops a write still in flight, so wait for it first.
        withTimeout(TIMEOUT) { writer.awaitPersisted() }
        // DataStore releases the file only once its scope completes, not on cancel().
        writeScope.coroutineContext.job.cancelAndJoin()

        val readScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val readStore = PreferenceDataStoreFactory.create(scope = readScope, produceFile = { file })
        val reader = UserPreferences(readStore)
        withTimeout(TIMEOUT) { reader.awaitHydrated() }
        readScope.coroutineContext.job.cancelAndJoin()
        return reader
    }

    @Test
    fun showOsmPaths_defaultsOffAndPersistsAcrossReload() = runBlocking {
        assertEquals(false, prefs.showOsmPaths.value)
        val file = newFile("osm_paths_persist.preferences_pb")
        assertEquals(true, reload(file) { it.updateShowOsmPaths(true) }.showOsmPaths.value)
    }

    @Test
    fun updateCoordFormat_persistsAcrossReload() = runBlocking {
        val file = newFile("coord_format_persist.preferences_pb")
        assertEquals(CoordFormat.MGRS, reload(file) { it.updateCoordFormat(CoordFormat.MGRS) }.coordFormat.value)
    }

    @Test
    fun showCoverage_defaultsOffAndPersistsAcrossReload() = runBlocking {
        assertEquals(false, prefs.showCoverage.value)
        val file = newFile("coverage_persist.preferences_pb")
        assertEquals(true, reload(file) { it.updateShowCoverage(true) }.showCoverage.value)
    }

    @Test
    fun coverageTilesVersion_defaultsToFallbackAndPersistsAcrossReload() = runBlocking {
        assertEquals(CoverageTilesClient.FALLBACK_VERSION, prefs.coverageTilesVersion.value)
        val file = newFile("coverage_version_persist.preferences_pb")
        assertEquals("2026-11-01", reload(file) { it.updateCoverageTilesVersion("2026-11-01") }.coverageTilesVersion.value)
    }
}
