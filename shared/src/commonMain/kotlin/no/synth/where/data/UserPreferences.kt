package no.synth.where.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import no.synth.where.data.geo.CoordFormat
import no.synth.where.data.geo.LatLng
import no.synth.where.ui.map.MapLayer

class UserPreferences(private val dataStore: DataStore<Preferences>) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _showCountyBorders = MutableStateFlow(false)
    val showCountyBorders: StateFlow<Boolean> = _showCountyBorders.asStateFlow()

    private val _crashReportingEnabled = MutableStateFlow(true)
    val crashReportingEnabled: StateFlow<Boolean> = _crashReportingEnabled.asStateFlow()

    private val _hasSeenTrackingInfo = MutableStateFlow(false)
    val hasSeenTrackingInfo: StateFlow<Boolean> = _hasSeenTrackingInfo.asStateFlow()

    private val _onlineTrackingEnabled = MutableStateFlow(false)
    val onlineTrackingEnabled: StateFlow<Boolean> = _onlineTrackingEnabled.asStateFlow()

    private val _trackingServerUrl = MutableStateFlow("https://where.synth.no")
    val trackingServerUrl: StateFlow<String> = _trackingServerUrl.asStateFlow()

    private val _offlineModeEnabled = MutableStateFlow(false)
    val offlineModeEnabled: StateFlow<Boolean> = _offlineModeEnabled.asStateFlow()

    private val _downloadElevationData = MutableStateFlow(true)
    val downloadElevationData: StateFlow<Boolean> = _downloadElevationData.asStateFlow()

    private val _themeMode = MutableStateFlow("system")
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    private val _coordFormat = MutableStateFlow(CoordFormat.LATLNG)
    val coordFormat: StateFlow<CoordFormat> = _coordFormat.asStateFlow()

    private val _showWaymarkedTrails = MutableStateFlow(false)
    val showWaymarkedTrails: StateFlow<Boolean> = _showWaymarkedTrails.asStateFlow()

    private val _showSavedPoints = MutableStateFlow(true)
    val showSavedPoints: StateFlow<Boolean> = _showSavedPoints.asStateFlow()

    private val _showAvalancheZones = MutableStateFlow(false)
    val showAvalancheZones: StateFlow<Boolean> = _showAvalancheZones.asStateFlow()

    private val _showHillshade = MutableStateFlow(false)
    val showHillshade: StateFlow<Boolean> = _showHillshade.asStateFlow()

    private val _crosshairActive = MutableStateFlow(false)
    val crosshairActive: StateFlow<Boolean> = _crosshairActive.asStateFlow()

    private val _selectedMapLayer = MutableStateFlow(MapLayer.KARTVERKET)
    val selectedMapLayer: StateFlow<MapLayer> = _selectedMapLayer.asStateFlow()

    private val _searchHistory = MutableStateFlow<List<PlaceSearchClient.SearchResult>>(emptyList())
    val searchHistory: StateFlow<List<PlaceSearchClient.SearchResult>> = _searchHistory.asStateFlow()

    init {
        scope.launch {
            dataStore.data.collect { prefs ->
                _showCountyBorders.value = prefs[SHOW_COUNTY_BORDERS] ?: false
                _showWaymarkedTrails.value = prefs[SHOW_WAYMARKED_TRAILS] ?: false
                _showSavedPoints.value = prefs[SHOW_SAVED_POINTS] ?: true
                _showAvalancheZones.value = prefs[SHOW_AVALANCHE_ZONES] ?: false
                _showHillshade.value = prefs[SHOW_HILLSHADE] ?: false
                _crosshairActive.value = prefs[CROSSHAIR_ACTIVE] ?: false
                _selectedMapLayer.value = try { MapLayer.valueOf(prefs[SELECTED_MAP_LAYER] ?: "KARTVERKET") } catch (_: Exception) { MapLayer.KARTVERKET }
                _crashReportingEnabled.value = prefs[CRASH_REPORTING_ENABLED] ?: true
                _hasSeenTrackingInfo.value = prefs[HAS_SEEN_TRACKING_INFO] ?: false
                _onlineTrackingEnabled.value = prefs[ONLINE_TRACKING_ENABLED] ?: false
                _trackingServerUrl.value = prefs[TRACKING_SERVER_URL] ?: "https://where.synth.no"
                _offlineModeEnabled.value = prefs[OFFLINE_MODE_ENABLED] ?: false
                _downloadElevationData.value = prefs[DOWNLOAD_ELEVATION_DATA] ?: true
                _themeMode.value = prefs[THEME_MODE] ?: "system"
                _coordFormat.value = try { CoordFormat.valueOf(prefs[COORD_FORMAT] ?: "LATLNG") } catch (_: Exception) { CoordFormat.LATLNG }
                _searchHistory.value = deserializeSearchHistory(prefs[SEARCH_HISTORY])
            }
        }
    }

    fun updateShowCountyBorders(value: Boolean) {
        _showCountyBorders.value = value
        scope.launch {
            dataStore.edit { it[SHOW_COUNTY_BORDERS] = value }
        }
    }

    fun updateShowWaymarkedTrails(value: Boolean) {
        _showWaymarkedTrails.value = value
        scope.launch { dataStore.edit { it[SHOW_WAYMARKED_TRAILS] = value } }
    }

    fun updateShowSavedPoints(value: Boolean) {
        _showSavedPoints.value = value
        scope.launch { dataStore.edit { it[SHOW_SAVED_POINTS] = value } }
    }

    fun updateShowAvalancheZones(value: Boolean) {
        _showAvalancheZones.value = value
        scope.launch { dataStore.edit { it[SHOW_AVALANCHE_ZONES] = value } }
    }

    fun updateShowHillshade(value: Boolean) {
        _showHillshade.value = value
        scope.launch { dataStore.edit { it[SHOW_HILLSHADE] = value } }
    }

    fun updateCrosshairActive(value: Boolean) {
        _crosshairActive.value = value
        scope.launch { dataStore.edit { it[CROSSHAIR_ACTIVE] = value } }
    }

    fun updateSelectedMapLayer(value: MapLayer) {
        _selectedMapLayer.value = value
        scope.launch { dataStore.edit { it[SELECTED_MAP_LAYER] = value.name } }
    }

    fun updateCrashReportingEnabled(value: Boolean) {
        _crashReportingEnabled.value = value
        scope.launch {
            dataStore.edit { it[CRASH_REPORTING_ENABLED] = value }
        }
    }

    fun markTrackingInfoSeen() {
        _hasSeenTrackingInfo.value = true
        scope.launch { dataStore.edit { it[HAS_SEEN_TRACKING_INFO] = true } }
    }

    fun updateOnlineTrackingEnabled(value: Boolean) {
        _onlineTrackingEnabled.value = value
        scope.launch {
            dataStore.edit { it[ONLINE_TRACKING_ENABLED] = value }
        }
    }

    fun updateOfflineModeEnabled(value: Boolean) {
        _offlineModeEnabled.value = value
        scope.launch {
            dataStore.edit { it[OFFLINE_MODE_ENABLED] = value }
        }
    }

    fun updateDownloadElevationData(value: Boolean) {
        _downloadElevationData.value = value
        scope.launch { dataStore.edit { it[DOWNLOAD_ELEVATION_DATA] = value } }
    }

    fun updateThemeMode(value: String) {
        _themeMode.value = value
        scope.launch {
            dataStore.edit { it[THEME_MODE] = value }
        }
    }

    fun updateCoordFormat(value: CoordFormat) {
        _coordFormat.value = value
        scope.launch {
            dataStore.edit { it[COORD_FORMAT] = value.name }
        }
    }

    fun addSearchHistoryEntry(result: PlaceSearchClient.SearchResult) {
        val current = _searchHistory.value.toMutableList()
        current.removeAll { it.name == result.name && it.municipality == result.municipality }
        current.add(0, result)
        val updated = current.take(MAX_SEARCH_HISTORY)
        _searchHistory.value = updated
        scope.launch {
            dataStore.edit { it[SEARCH_HISTORY] = serializeSearchHistory(updated) }
        }
    }

    private fun serializeSearchHistory(results: List<PlaceSearchClient.SearchResult>): String =
        buildJsonArray {
            for (r in results) {
                add(buildJsonObject {
                    put("name", r.name)
                    put("type", r.type)
                    put("municipality", r.municipality)
                    put("lat", r.latLng.latitude)
                    put("lon", r.latLng.longitude)
                })
            }
        }.toString()

    private fun deserializeSearchHistory(json: String?): List<PlaceSearchClient.SearchResult> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            Json.parseToJsonElement(json).jsonArray.map { element ->
                val obj = element.jsonObject
                PlaceSearchClient.SearchResult(
                    name = obj["name"]?.jsonPrimitive?.content ?: "",
                    type = obj["type"]?.jsonPrimitive?.content ?: "",
                    municipality = obj["municipality"]?.jsonPrimitive?.content ?: "",
                    latLng = LatLng(
                        obj["lat"]?.jsonPrimitive?.double ?: 0.0,
                        obj["lon"]?.jsonPrimitive?.double ?: 0.0
                    )
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    companion object {
        private const val MAX_SEARCH_HISTORY = 10
        private val CRASH_REPORTING_ENABLED = booleanPreferencesKey("crash_reporting_enabled")
        private val SHOW_COUNTY_BORDERS = booleanPreferencesKey("show_county_borders")
        private val SHOW_WAYMARKED_TRAILS = booleanPreferencesKey("show_waymarked_trails")
        private val SHOW_SAVED_POINTS = booleanPreferencesKey("show_saved_points")
        private val SHOW_AVALANCHE_ZONES = booleanPreferencesKey("show_avalanche_zones")
        private val SHOW_HILLSHADE = booleanPreferencesKey("show_hillshade")
        private val CROSSHAIR_ACTIVE = booleanPreferencesKey("crosshair_active")
        private val SELECTED_MAP_LAYER = stringPreferencesKey("selected_map_layer")
        private val HAS_SEEN_TRACKING_INFO = booleanPreferencesKey("has_seen_tracking_info")
        private val ONLINE_TRACKING_ENABLED = booleanPreferencesKey("online_tracking_enabled")
        private val TRACKING_SERVER_URL = stringPreferencesKey("tracking_server_url")
        private val OFFLINE_MODE_ENABLED = booleanPreferencesKey("offline_mode_enabled")
        private val DOWNLOAD_ELEVATION_DATA = booleanPreferencesKey("download_elevation_data")
        private val THEME_MODE = stringPreferencesKey("theme_mode")
        private val COORD_FORMAT = stringPreferencesKey("coord_format")
        private val SEARCH_HISTORY = stringPreferencesKey("search_history")
    }
}
