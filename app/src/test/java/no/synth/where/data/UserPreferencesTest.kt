package no.synth.where.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
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
    fun defaults_showCountyBordersIsFalse() {
        assertEquals(false, prefs.showCountyBorders.value)
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
    fun updateShowCountyBorders_updatesStateFlow() {
        prefs.updateShowCountyBorders(true)
        assertEquals(true, prefs.showCountyBorders.value)
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
    fun updatePersistsAndEmits() = runBlocking {
        prefs.updateShowCountyBorders(true)
        assertEquals(true, prefs.showCountyBorders.value)

        prefs.updateShowCountyBorders(false)
        assertEquals(false, prefs.showCountyBorders.value)
    }
}
