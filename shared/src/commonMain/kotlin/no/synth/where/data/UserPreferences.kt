package no.synth.where.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import no.synth.where.util.currentTimeMillis
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
import no.synth.where.ui.map.NveOverlay

class UserPreferences(private val dataStore: DataStore<Preferences>) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _crashReportingEnabled = MutableStateFlow(true)
    val crashReportingEnabled: StateFlow<Boolean> = _crashReportingEnabled.asStateFlow()

    private val _hasSeenTrackingInfo = MutableStateFlow(false)
    val hasSeenTrackingInfo: StateFlow<Boolean> = _hasSeenTrackingInfo.asStateFlow()

    private val _onlineTrackingEnabled = MutableStateFlow(false)
    val onlineTrackingEnabled: StateFlow<Boolean> = _onlineTrackingEnabled.asStateFlow()

    private val _liveShareUntilMillis = MutableStateFlow(0L)
    val liveShareUntilMillis: StateFlow<Long> = _liveShareUntilMillis.asStateFlow()
    private var liveShareExpiryJob: Job? = null
    private val liveShareExpiryMutex = Mutex()

    private val _trackingServerUrl = MutableStateFlow("https://where.synth.no")
    val trackingServerUrl: StateFlow<String> = _trackingServerUrl.asStateFlow()

    private val _viewerCount = MutableStateFlow(0)
    val viewerCount: StateFlow<Int> = _viewerCount.asStateFlow()

    fun updateViewerCount(count: Int) {
        _viewerCount.value = count
    }

    private val _offlineModeEnabled = MutableStateFlow(false)
    val offlineModeEnabled: StateFlow<Boolean> = _offlineModeEnabled.asStateFlow()

    private val _downloadElevationData = MutableStateFlow(true)
    val downloadElevationData: StateFlow<Boolean> = _downloadElevationData.asStateFlow()

    private val _downloadMaxZoom = MutableStateFlow(DEFAULT_DOWNLOAD_MAX_ZOOM)
    val downloadMaxZoom: StateFlow<Int> = _downloadMaxZoom.asStateFlow()

    private val _themeMode = MutableStateFlow("system")
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    private val _coordFormat = MutableStateFlow(CoordFormat.LATLNG)
    val coordFormat: StateFlow<CoordFormat> = _coordFormat.asStateFlow()

    private val _showWaymarkedTrails = MutableStateFlow(false)
    val showWaymarkedTrails: StateFlow<Boolean> = _showWaymarkedTrails.asStateFlow()

    private val _showOsmPaths = MutableStateFlow(false)
    val showOsmPaths: StateFlow<Boolean> = _showOsmPaths.asStateFlow()

    private val _showSavedPoints = MutableStateFlow(true)
    val showSavedPoints: StateFlow<Boolean> = _showSavedPoints.asStateFlow()

    private val _nveOverlay = MutableStateFlow<NveOverlay?>(null)
    val nveOverlay: StateFlow<NveOverlay?> = _nveOverlay.asStateFlow()

    private val _showCoordGrid = MutableStateFlow(false)
    val showCoordGrid: StateFlow<Boolean> = _showCoordGrid.asStateFlow()

    private val _crosshairActive = MutableStateFlow(false)
    val crosshairActive: StateFlow<Boolean> = _crosshairActive.asStateFlow()

    private val _northLocked = MutableStateFlow(false)
    val northLocked: StateFlow<Boolean> = _northLocked.asStateFlow()

    private val _selectedMapLayer = MutableStateFlow(MapLayer.KARTVERKET)
    val selectedMapLayer: StateFlow<MapLayer> = _selectedMapLayer.asStateFlow()

    private val _searchHistory = MutableStateFlow<List<PlaceSearchClient.SearchResult>>(emptyList())
    val searchHistory: StateFlow<List<PlaceSearchClient.SearchResult>> = _searchHistory.asStateFlow()

    private val _followedClientId = MutableStateFlow<String?>(null)
    val followedClientId: StateFlow<String?> = _followedClientId.asStateFlow()

    private val _followHistory = MutableStateFlow<List<String>>(emptyList())
    val followHistory: StateFlow<List<String>> = _followHistory.asStateFlow()

    // Strava (BYO credentials): the user creates their own Strava API app and enters its
    // client id + secret; OAuth token exchange happens on-device. clientId is exposed so the UI
    // can tell whether credentials are set; the secret and refresh token stay internal.
    private val _stravaClientId = MutableStateFlow<String?>(null)
    val stravaClientId: StateFlow<String?> = _stravaClientId.asStateFlow()
    private val _stravaClientSecret = MutableStateFlow<String?>(null)
    private val _stravaRefreshToken = MutableStateFlow<String?>(null)
    private val _stravaOAuthState = MutableStateFlow<String?>(null)

    private val _stravaConnected = MutableStateFlow(false)
    val stravaConnected: StateFlow<Boolean> = _stravaConnected.asStateFlow()

    private val _stravaAthleteId = MutableStateFlow(0L)
    val stravaAthleteId: StateFlow<Long> = _stravaAthleteId.asStateFlow()

    private val _stravaAccessToken = MutableStateFlow<String?>(null)
    val stravaAccessToken: StateFlow<String?> = _stravaAccessToken.asStateFlow()

    /** Access-token expiry, unix epoch seconds. */
    private val _stravaTokenExpiry = MutableStateFlow(0L)
    val stravaTokenExpiry: StateFlow<Long> = _stravaTokenExpiry.asStateFlow()

    init {
        scope.launch {
            dataStore.data.collect { prefs ->
                _showWaymarkedTrails.value = prefs[SHOW_WAYMARKED_TRAILS] ?: false
                _showOsmPaths.value = prefs[SHOW_OSM_PATHS] ?: false
                _showSavedPoints.value = prefs[SHOW_SAVED_POINTS] ?: true
                val storedNveOverlay = prefs[NVE_OVERLAY]
                _nveOverlay.value = if (storedNveOverlay == null) {
                    // Installs predating the steepness-only overlay only have the legacy boolean.
                    if (prefs[SHOW_AVALANCHE_ZONES] == true) NveOverlay.STEEPNESS_RUNOUT else null
                } else {
                    NveOverlay.entries.find { it.name == storedNveOverlay }
                }
                _showCoordGrid.value = prefs[SHOW_COORD_GRID] ?: false
                _crosshairActive.value = prefs[CROSSHAIR_ACTIVE] ?: false
                _northLocked.value = prefs[NORTH_LOCKED] ?: false
                _selectedMapLayer.value = try { MapLayer.valueOf(prefs[SELECTED_MAP_LAYER] ?: "KARTVERKET") } catch (_: Exception) { MapLayer.KARTVERKET }
                _crashReportingEnabled.value = prefs[CRASH_REPORTING_ENABLED] ?: true
                _hasSeenTrackingInfo.value = prefs[HAS_SEEN_TRACKING_INFO] ?: false
                _onlineTrackingEnabled.value = prefs[ONLINE_TRACKING_ENABLED] ?: false
                val rawUntil = prefs[LIVE_SHARE_UNTIL] ?: 0L
                val effectiveUntil = if (rawUntil > currentTimeMillis()) rawUntil else 0L
                if (_liveShareUntilMillis.value != effectiveUntil) {
                    _liveShareUntilMillis.value = effectiveUntil
                    scheduleLiveShareExpiry(effectiveUntil)
                }
                _trackingServerUrl.value = prefs[TRACKING_SERVER_URL] ?: "https://where.synth.no"
                _offlineModeEnabled.value = prefs[OFFLINE_MODE_ENABLED] ?: false
                _downloadElevationData.value = prefs[DOWNLOAD_ELEVATION_DATA] ?: true
                _downloadMaxZoom.value = prefs[DOWNLOAD_MAX_ZOOM] ?: DEFAULT_DOWNLOAD_MAX_ZOOM
                _themeMode.value = prefs[THEME_MODE] ?: "system"
                _coordFormat.value = try { CoordFormat.valueOf(prefs[COORD_FORMAT] ?: "LATLNG") } catch (_: Exception) { CoordFormat.LATLNG }
                _searchHistory.value = deserializeSearchHistory(prefs[SEARCH_HISTORY])
                _followedClientId.value = prefs[FOLLOWED_CLIENT_ID]
                _followHistory.value = prefs[FOLLOW_HISTORY]?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
                _stravaClientId.value = prefs[STRAVA_CLIENT_ID]
                _stravaClientSecret.value = prefs[STRAVA_CLIENT_SECRET]
                _stravaRefreshToken.value = prefs[STRAVA_REFRESH_TOKEN]
                _stravaOAuthState.value = prefs[STRAVA_OAUTH_STATE]
                _stravaConnected.value = prefs[STRAVA_CONNECTED] ?: false
                _stravaAthleteId.value = prefs[STRAVA_ATHLETE_ID] ?: 0L
                _stravaAccessToken.value = prefs[STRAVA_ACCESS_TOKEN]
                _stravaTokenExpiry.value = prefs[STRAVA_TOKEN_EXPIRY] ?: 0L
            }
        }
    }

    // Cached (synchronous) reads for StravaTokenManager. Not exposed as flows, to keep the secret
    // contained. Use the suspending read* variants when correctness across a cold start matters
    // (the OAuth redirect can re-launch the app before the init collector has hydrated these).
    fun stravaClientIdValue(): String? = _stravaClientId.value

    suspend fun readStravaClientId(): String? = dataStore.data.map { it[STRAVA_CLIENT_ID] }.first()
    suspend fun readStravaClientSecret(): String? = dataStore.data.map { it[STRAVA_CLIENT_SECRET] }.first()
    suspend fun readStravaRefreshToken(): String? = dataStore.data.map { it[STRAVA_REFRESH_TOKEN] }.first()
    suspend fun readStravaOAuthState(): String? = dataStore.data.map { it[STRAVA_OAUTH_STATE] }.first()

    fun setStravaCredentials(clientId: String, clientSecret: String) {
        _stravaClientId.value = clientId
        _stravaClientSecret.value = clientSecret
        scope.launch {
            dataStore.edit {
                it[STRAVA_CLIENT_ID] = clientId
                it[STRAVA_CLIENT_SECRET] = clientSecret
            }
        }
    }

    /** Pending OAuth CSRF state, persisted so it survives the browser round-trip / process death. */
    fun setStravaOAuthState(state: String?) {
        _stravaOAuthState.value = state
        scope.launch {
            dataStore.edit {
                if (state != null) it[STRAVA_OAUTH_STATE] = state else it.remove(STRAVA_OAUTH_STATE)
            }
        }
    }

    /** Cache freshly minted tokens and mark the account connected. */
    fun cacheStravaTokens(accessToken: String, refreshToken: String, expirySeconds: Long, athleteId: Long) {
        _stravaAccessToken.value = accessToken
        _stravaRefreshToken.value = refreshToken
        _stravaTokenExpiry.value = expirySeconds
        _stravaConnected.value = true
        if (athleteId > 0L) _stravaAthleteId.value = athleteId
        scope.launch {
            dataStore.edit {
                it[STRAVA_ACCESS_TOKEN] = accessToken
                it[STRAVA_REFRESH_TOKEN] = refreshToken
                it[STRAVA_TOKEN_EXPIRY] = expirySeconds
                it[STRAVA_CONNECTED] = true
                if (athleteId > 0L) it[STRAVA_ATHLETE_ID] = athleteId
            }
        }
    }

    /** Forget the user's stored Strava app credentials (client id + secret). */
    fun clearStravaCredentials() {
        _stravaClientId.value = null
        _stravaClientSecret.value = null
        scope.launch {
            dataStore.edit {
                it.remove(STRAVA_CLIENT_ID)
                it.remove(STRAVA_CLIENT_SECRET)
            }
        }
    }

    /** Forget the connected session but keep the entered credentials so reconnect is one tap. */
    fun clearStravaTokens() {
        _stravaConnected.value = false
        _stravaAthleteId.value = 0L
        _stravaAccessToken.value = null
        _stravaTokenExpiry.value = 0L
        _stravaRefreshToken.value = null
        _stravaOAuthState.value = null
        scope.launch {
            dataStore.edit {
                it.remove(STRAVA_CONNECTED)
                it.remove(STRAVA_ATHLETE_ID)
                it.remove(STRAVA_ACCESS_TOKEN)
                it.remove(STRAVA_TOKEN_EXPIRY)
                it.remove(STRAVA_REFRESH_TOKEN)
                it.remove(STRAVA_OAUTH_STATE)
            }
        }
    }

    fun updateShowWaymarkedTrails(value: Boolean) {
        _showWaymarkedTrails.value = value
        scope.launch { dataStore.edit { it[SHOW_WAYMARKED_TRAILS] = value } }
    }

    fun updateShowOsmPaths(value: Boolean) {
        _showOsmPaths.value = value
        scope.launch { dataStore.edit { it[SHOW_OSM_PATHS] = value } }
    }

    fun updateShowSavedPoints(value: Boolean) {
        _showSavedPoints.value = value
        scope.launch { dataStore.edit { it[SHOW_SAVED_POINTS] = value } }
    }

    fun updateNveOverlay(value: NveOverlay?) {
        _nveOverlay.value = value
        // Stored as "" rather than a missing key, so turning the overlay off doesn't fall back to
        // the legacy boolean and resurrect the old choice.
        scope.launch { dataStore.edit { it[NVE_OVERLAY] = value?.name ?: "" } }
    }

    /** Tapping the active overlay turns it off. */
    fun toggleNveOverlay(value: NveOverlay) =
        updateNveOverlay(if (_nveOverlay.value == value) null else value)

    fun updateShowCoordGrid(value: Boolean) {
        _showCoordGrid.value = value
        scope.launch { dataStore.edit { it[SHOW_COORD_GRID] = value } }
    }

    fun updateCrosshairActive(value: Boolean) {
        _crosshairActive.value = value
        scope.launch { dataStore.edit { it[CROSSHAIR_ACTIVE] = value } }
    }

    fun updateNorthLocked(value: Boolean) {
        _northLocked.value = value
        scope.launch { dataStore.edit { it[NORTH_LOCKED] = value } }
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

    fun confirmTrackingInfoAndEnable() {
        markTrackingInfoSeen()
        updateOnlineTrackingEnabled(true)
    }

    fun updateOnlineTrackingEnabled(value: Boolean) {
        _onlineTrackingEnabled.value = value
        scope.launch {
            dataStore.edit { it[ONLINE_TRACKING_ENABLED] = value }
        }
        if (!value) updateLiveShareUntil(0L)
    }

    fun startLiveShare(durationMillis: Long) {
        val until = currentTimeMillis() + durationMillis
        updateLiveShareUntil(until)
    }

    fun stopLiveShare() {
        updateLiveShareUntil(0L)
    }

    private fun updateLiveShareUntil(value: Long) {
        _liveShareUntilMillis.value = value
        scope.launch {
            dataStore.edit {
                if (value > 0L) it[LIVE_SHARE_UNTIL] = value else it.remove(LIVE_SHARE_UNTIL)
            }
        }
        scheduleLiveShareExpiry(value)
    }

    private fun scheduleLiveShareExpiry(until: Long) {
        scope.launch {
            liveShareExpiryMutex.withLock {
                liveShareExpiryJob?.cancel()
                liveShareExpiryJob = null
                if (until <= 0L) return@withLock
                val delayMs = until - currentTimeMillis()
                if (delayMs <= 0L) {
                    updateLiveShareUntil(0L)
                    return@withLock
                }
                liveShareExpiryJob = scope.launch {
                    delay(delayMs)
                    if (_liveShareUntilMillis.value == until) {
                        updateLiveShareUntil(0L)
                    }
                }
            }
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

    fun updateDownloadMaxZoom(value: Int) {
        _downloadMaxZoom.value = value
        scope.launch { dataStore.edit { it[DOWNLOAD_MAX_ZOOM] = value } }
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

    fun updateFollowedClientId(value: String?) {
        _followedClientId.value = value
        scope.launch {
            dataStore.edit {
                if (value != null) {
                    it[FOLLOWED_CLIENT_ID] = value
                } else {
                    it.remove(FOLLOWED_CLIENT_ID)
                }
            }
        }
    }

    fun addFollowHistoryEntry(clientId: String) {
        val current = _followHistory.value.toMutableList()
        current.remove(clientId)
        current.add(0, clientId)
        val updated = current.take(MAX_FOLLOW_HISTORY)
        _followHistory.value = updated
        scope.launch {
            dataStore.edit { it[FOLLOW_HISTORY] = updated.joinToString(",") }
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
        const val DEFAULT_DOWNLOAD_MAX_ZOOM = 12
        private const val MAX_FOLLOW_HISTORY = 5
        private const val MAX_SEARCH_HISTORY = 10
        private val CRASH_REPORTING_ENABLED = booleanPreferencesKey("crash_reporting_enabled")
        private val SHOW_WAYMARKED_TRAILS = booleanPreferencesKey("show_waymarked_trails")
        private val SHOW_OSM_PATHS = booleanPreferencesKey("show_osm_paths")
        private val SHOW_SAVED_POINTS = booleanPreferencesKey("show_saved_points")
        private val NVE_OVERLAY = stringPreferencesKey("nve_overlay")

        // Read-only legacy key: superseded by NVE_OVERLAY, still read to migrate old installs.
        private val SHOW_AVALANCHE_ZONES = booleanPreferencesKey("show_avalanche_zones")
        private val SHOW_COORD_GRID = booleanPreferencesKey("show_coord_grid")
        private val CROSSHAIR_ACTIVE = booleanPreferencesKey("crosshair_active")
        private val NORTH_LOCKED = booleanPreferencesKey("north_locked")
        private val SELECTED_MAP_LAYER = stringPreferencesKey("selected_map_layer")
        private val HAS_SEEN_TRACKING_INFO = booleanPreferencesKey("has_seen_tracking_info")
        private val ONLINE_TRACKING_ENABLED = booleanPreferencesKey("online_tracking_enabled")
        // DataStore key string left as "always_share_until_millis" so existing
        // installs don't lose state on upgrade.
        private val LIVE_SHARE_UNTIL = longPreferencesKey("always_share_until_millis")
        private val TRACKING_SERVER_URL = stringPreferencesKey("tracking_server_url")
        private val OFFLINE_MODE_ENABLED = booleanPreferencesKey("offline_mode_enabled")
        private val DOWNLOAD_ELEVATION_DATA = booleanPreferencesKey("download_elevation_data")
        private val DOWNLOAD_MAX_ZOOM = intPreferencesKey("download_max_zoom")
        private val THEME_MODE = stringPreferencesKey("theme_mode")
        private val COORD_FORMAT = stringPreferencesKey("coord_format")
        private val SEARCH_HISTORY = stringPreferencesKey("search_history")
        private val FOLLOWED_CLIENT_ID = stringPreferencesKey("followed_client_id")
        private val FOLLOW_HISTORY = stringPreferencesKey("follow_history")
        private val STRAVA_CLIENT_ID = stringPreferencesKey("strava_client_id")
        private val STRAVA_CLIENT_SECRET = stringPreferencesKey("strava_client_secret")
        private val STRAVA_REFRESH_TOKEN = stringPreferencesKey("strava_refresh_token")
        private val STRAVA_OAUTH_STATE = stringPreferencesKey("strava_oauth_state")
        private val STRAVA_CONNECTED = booleanPreferencesKey("strava_connected")
        private val STRAVA_ATHLETE_ID = longPreferencesKey("strava_athlete_id")
        private val STRAVA_ACCESS_TOKEN = stringPreferencesKey("strava_access_token")
        private val STRAVA_TOKEN_EXPIRY = longPreferencesKey("strava_token_expiry")
    }
}
