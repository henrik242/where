package no.synth.where.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

class UserPreferences(context: Context) {
    private val dataStore = context.dataStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _showCountyBorders = MutableStateFlow(true)
    val showCountyBorders: StateFlow<Boolean> = _showCountyBorders.asStateFlow()

    private val _crashReportingEnabled = MutableStateFlow(true)
    val crashReportingEnabled: StateFlow<Boolean> = _crashReportingEnabled.asStateFlow()

    private val _onlineTrackingEnabled = MutableStateFlow(false)
    val onlineTrackingEnabled: StateFlow<Boolean> = _onlineTrackingEnabled.asStateFlow()

    private val _trackingServerUrl = MutableStateFlow("https://where.synth.no")
    val trackingServerUrl: StateFlow<String> = _trackingServerUrl.asStateFlow()

    init {
        scope.launch {
            dataStore.data.collect { prefs ->
                _showCountyBorders.value = prefs[SHOW_COUNTY_BORDERS] ?: true
                _crashReportingEnabled.value = prefs[CRASH_REPORTING_ENABLED] ?: true
                _onlineTrackingEnabled.value = prefs[ONLINE_TRACKING_ENABLED] ?: false
                _trackingServerUrl.value = prefs[TRACKING_SERVER_URL] ?: "https://where.synth.no"
            }
        }
    }

    fun updateShowCountyBorders(value: Boolean) {
        _showCountyBorders.value = value
        scope.launch {
            dataStore.edit { it[SHOW_COUNTY_BORDERS] = value }
        }
    }

    fun updateCrashReportingEnabled(value: Boolean) {
        _crashReportingEnabled.value = value
        scope.launch {
            dataStore.edit { it[CRASH_REPORTING_ENABLED] = value }
        }
    }

    fun updateOnlineTrackingEnabled(value: Boolean) {
        _onlineTrackingEnabled.value = value
        scope.launch {
            dataStore.edit { it[ONLINE_TRACKING_ENABLED] = value }
        }
    }

    companion object {
        private val CRASH_REPORTING_ENABLED = booleanPreferencesKey("crash_reporting_enabled")
        private val SHOW_COUNTY_BORDERS = booleanPreferencesKey("show_county_borders")
        private val ONLINE_TRACKING_ENABLED = booleanPreferencesKey("online_tracking_enabled")
        private val TRACKING_SERVER_URL = stringPreferencesKey("tracking_server_url")
    }
}
