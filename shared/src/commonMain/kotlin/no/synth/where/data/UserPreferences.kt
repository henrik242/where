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
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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

class UserPreferences(
    private val dataStore: DataStore<Preferences>,
    // Tests set this false to keep the in-memory cache empty, as it is on a cold start until the
    // collector below runs. Production always hydrates.
    hydrateFromDisk: Boolean = true,
) {
    // Parallelism 1 so the persist coroutines run in call order. On a multi-threaded dispatcher a
    // rapid toggle can write the older value last, and the init collector then reads that stale
    // value back over the newer in-memory state.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))

    val crashReportingEnabled: StateFlow<Boolean>
        field = MutableStateFlow(true)

    val hasSeenTrackingInfo: StateFlow<Boolean>
        field = MutableStateFlow(false)

    val offlineMigrationNoticeShown: StateFlow<Boolean>
        field = MutableStateFlow(false)

    val onlineTrackingEnabled: StateFlow<Boolean>
        field = MutableStateFlow(false)

    val liveShareUntilMillis: StateFlow<Long>
        field = MutableStateFlow(0L)
    private var liveShareExpiryJob: Job? = null
    private val liveShareExpiryMutex = Mutex()

    val trackingServerUrl: StateFlow<String>
        field = MutableStateFlow("https://where.synth.no")

    val viewerCount: StateFlow<Int>
        field = MutableStateFlow(0)

    fun updateViewerCount(count: Int) {
        viewerCount.value = count
    }

    val offlineModeEnabled: StateFlow<Boolean>
        field = MutableStateFlow(false)

    val downloadElevationData: StateFlow<Boolean>
        field = MutableStateFlow(true)

    val downloadMaxZoom: StateFlow<Int>
        field = MutableStateFlow(DEFAULT_DOWNLOAD_MAX_ZOOM)

    val themeMode: StateFlow<String>
        field = MutableStateFlow("system")

    val coordFormat: StateFlow<CoordFormat>
        field = MutableStateFlow(CoordFormat.LATLNG)

    val showWaymarkedTrails: StateFlow<Boolean>
        field = MutableStateFlow(false)

    val showOsmPaths: StateFlow<Boolean>
        field = MutableStateFlow(false)

    val showSavedPoints: StateFlow<Boolean>
        field = MutableStateFlow(true)

    val nveOverlay: StateFlow<NveOverlay?>
        field = MutableStateFlow<NveOverlay?>(null)

    val showCoverage: StateFlow<Boolean>
        field = MutableStateFlow(false)

    /** Last known erdetdekning.no dataset version, so the overlay works before (or without) a lookup. */
    val coverageOperator: StateFlow<CoverageOperator>
        field = MutableStateFlow(CoverageOperator.ALL)

    val coverageTilesVersion: StateFlow<String>
        field = MutableStateFlow(CoverageTilesClient.FALLBACK_VERSION)

    val showCoordGrid: StateFlow<Boolean>
        field = MutableStateFlow(false)

    val crosshairActive: StateFlow<Boolean>
        field = MutableStateFlow(false)

    val northLocked: StateFlow<Boolean>
        field = MutableStateFlow(false)

    val largeCompass: StateFlow<Boolean>
        field = MutableStateFlow(false)

    val selectedMapLayer: StateFlow<MapLayer>
        field = MutableStateFlow(MapLayer.KARTVERKET)

    val searchHistory: StateFlow<List<PlaceSearchClient.SearchResult>>
        field = MutableStateFlow<List<PlaceSearchClient.SearchResult>>(emptyList())

    val followedClientIds: StateFlow<List<String>>
        field = MutableStateFlow<List<String>>(emptyList())

    val followHistory: StateFlow<List<String>>
        field = MutableStateFlow<List<String>>(emptyList())

    // Local, per-device nicknames for followed client ids (clientId -> name). Never sent anywhere.
    val clientNicknames: StateFlow<Map<String, String>>
        field = MutableStateFlow<Map<String, String>>(emptyMap())

    // Strava (BYO credentials): the user creates their own Strava API app and enters its
    // client id + secret; OAuth token exchange happens on-device. clientId is exposed so the UI
    // can tell whether credentials are set; the secret and refresh token stay internal.
    val stravaClientId: StateFlow<String?>
        field = MutableStateFlow<String?>(null)
    private val _stravaClientSecret = MutableStateFlow<String?>(null)
    private val _stravaRefreshToken = MutableStateFlow<String?>(null)
    private val _stravaOAuthState = MutableStateFlow<String?>(null)

    val stravaConnected: StateFlow<Boolean>
        field = MutableStateFlow(false)

    val stravaAthleteId: StateFlow<Long>
        field = MutableStateFlow(0L)

    val stravaAccessToken: StateFlow<String?>
        field = MutableStateFlow<String?>(null)

    /** Access-token expiry, unix epoch seconds. */
    val stravaTokenExpiry: StateFlow<Long>
        field = MutableStateFlow(0L)

    init {
        if (hydrateFromDisk) scope.launch {
            dataStore.data.collect { prefs ->
                showWaymarkedTrails.value = prefs[SHOW_WAYMARKED_TRAILS] ?: false
                showOsmPaths.value = prefs[SHOW_OSM_PATHS] ?: false
                showSavedPoints.value = prefs[SHOW_SAVED_POINTS] ?: true
                val storedNveOverlay = prefs[NVE_OVERLAY]
                nveOverlay.value = if (storedNveOverlay == null) {
                    // Installs predating the steepness-only overlay only have the legacy boolean.
                    if (prefs[SHOW_AVALANCHE_ZONES] == true) NveOverlay.STEEPNESS_RUNOUT else null
                } else {
                    NveOverlay.entries.find { it.name == storedNveOverlay }
                }
                showCoverage.value = prefs[SHOW_COVERAGE] ?: false
                coverageOperator.value = CoverageOperator.entries.firstOrNull { it.name == prefs[COVERAGE_OPERATOR] } ?: CoverageOperator.ALL
                coverageTilesVersion.value = prefs[COVERAGE_TILES_VERSION] ?: CoverageTilesClient.FALLBACK_VERSION
                showCoordGrid.value = prefs[SHOW_COORD_GRID] ?: false
                crosshairActive.value = prefs[CROSSHAIR_ACTIVE] ?: false
                northLocked.value = prefs[NORTH_LOCKED] ?: false
                largeCompass.value = prefs[LARGE_COMPASS] ?: false
                selectedMapLayer.value = try { MapLayer.valueOf(prefs[SELECTED_MAP_LAYER] ?: "KARTVERKET") } catch (_: Exception) { MapLayer.KARTVERKET }
                crashReportingEnabled.value = prefs[CRASH_REPORTING_ENABLED] ?: true
                hasSeenTrackingInfo.value = prefs[HAS_SEEN_TRACKING_INFO] ?: false
                offlineMigrationNoticeShown.value = prefs[OFFLINE_MIGRATION_NOTICE_SHOWN] ?: false
                onlineTrackingEnabled.value = prefs[ONLINE_TRACKING_ENABLED] ?: false
                val rawUntil = prefs[LIVE_SHARE_UNTIL] ?: 0L
                val effectiveUntil = if (rawUntil > currentTimeMillis()) rawUntil else 0L
                if (liveShareUntilMillis.value != effectiveUntil) {
                    liveShareUntilMillis.value = effectiveUntil
                    scheduleLiveShareExpiry(effectiveUntil)
                }
                trackingServerUrl.value = prefs[TRACKING_SERVER_URL] ?: "https://where.synth.no"
                offlineModeEnabled.value = prefs[OFFLINE_MODE_ENABLED] ?: false
                downloadElevationData.value = prefs[DOWNLOAD_ELEVATION_DATA] ?: true
                downloadMaxZoom.value = prefs[DOWNLOAD_MAX_ZOOM] ?: DEFAULT_DOWNLOAD_MAX_ZOOM
                themeMode.value = prefs[THEME_MODE] ?: "system"
                coordFormat.value = try { CoordFormat.valueOf(prefs[COORD_FORMAT] ?: "LATLNG") } catch (_: Exception) { CoordFormat.LATLNG }
                searchHistory.value = deserializeSearchHistory(prefs[SEARCH_HISTORY])
                followedClientIds.value = LiveTrackingFollower.sanitize(
                    prefs[FOLLOWED_CLIENT_ID]?.split(",") ?: emptyList()
                )
                followHistory.value = prefs[FOLLOW_HISTORY]?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
                clientNicknames.value = deserializeNicknames(prefs[CLIENT_NICKNAMES])
                stravaClientId.value = prefs[STRAVA_CLIENT_ID]
                _stravaClientSecret.value = prefs[STRAVA_CLIENT_SECRET]
                _stravaRefreshToken.value = prefs[STRAVA_REFRESH_TOKEN]
                _stravaOAuthState.value = prefs[STRAVA_OAUTH_STATE]
                stravaConnected.value = prefs[STRAVA_CONNECTED] ?: false
                stravaAthleteId.value = prefs[STRAVA_ATHLETE_ID] ?: 0L
                stravaAccessToken.value = prefs[STRAVA_ACCESS_TOKEN]
                stravaTokenExpiry.value = prefs[STRAVA_TOKEN_EXPIRY] ?: 0L
            }
        }
    }

    // Cached (synchronous) reads for StravaTokenManager. Not exposed as flows, to keep the secret
    // contained. Use the suspending read* variants when correctness across a cold start matters
    // (the OAuth redirect can re-launch the app before the init collector has hydrated these).
    fun stravaClientIdValue(): String? = stravaClientId.value

    /** Reads the persisted deadline directly: [liveShareUntilMillis] is still 0 until the
     *  DataStore collector in `init` has run, which is too late for cold-start checks. */
    suspend fun readLiveShareUntil(): Long = dataStore.data.map { it[LIVE_SHARE_UNTIL] ?: 0L }.first()

    suspend fun readStravaClientId(): String? = dataStore.data.map { it[STRAVA_CLIENT_ID] }.first()
    suspend fun readStravaClientSecret(): String? = dataStore.data.map { it[STRAVA_CLIENT_SECRET] }.first()
    suspend fun readStravaRefreshToken(): String? = dataStore.data.map { it[STRAVA_REFRESH_TOKEN] }.first()
    suspend fun readStravaOAuthState(): String? = dataStore.data.map { it[STRAVA_OAUTH_STATE] }.first()
    /** Access token and its expiry in one snapshot, so a refresh can't tear the pair apart. */
    suspend fun readStravaTokens(): Pair<String?, Long> =
        dataStore.data.map { it[STRAVA_ACCESS_TOKEN] to (it[STRAVA_TOKEN_EXPIRY] ?: 0L) }.first()
    suspend fun readStravaAthleteId(): Long = dataStore.data.map { it[STRAVA_ATHLETE_ID] ?: 0L }.first()

    fun setStravaCredentials(clientId: String, clientSecret: String) {
        stravaClientId.value = clientId
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

    /**
     * Cache freshly minted tokens and mark the account connected. Posted to [scope] like every
     * other persist here so it stays ordered, and awaited so a failed write throws instead of
     * handing back a token whose rotated refresh token never reached disk. [scope] also keeps the
     * write alive when the caller (a closed screen) is cancelled.
     */
    suspend fun cacheStravaTokens(accessToken: String, refreshToken: String, expirySeconds: Long, athleteId: Long) {
        stravaAccessToken.value = accessToken
        _stravaRefreshToken.value = refreshToken
        stravaTokenExpiry.value = expirySeconds
        stravaConnected.value = true
        if (athleteId > 0L) stravaAthleteId.value = athleteId
        scope.async {
            dataStore.edit {
                it[STRAVA_ACCESS_TOKEN] = accessToken
                it[STRAVA_REFRESH_TOKEN] = refreshToken
                it[STRAVA_TOKEN_EXPIRY] = expirySeconds
                it[STRAVA_CONNECTED] = true
                if (athleteId > 0L) it[STRAVA_ATHLETE_ID] = athleteId
            }
        }.await()
    }

    /** Forget the user's stored Strava app credentials (client id + secret). */
    fun clearStravaCredentials() {
        stravaClientId.value = null
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
        stravaConnected.value = false
        stravaAthleteId.value = 0L
        stravaAccessToken.value = null
        stravaTokenExpiry.value = 0L
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
        showWaymarkedTrails.value = value
        scope.launch { dataStore.edit { it[SHOW_WAYMARKED_TRAILS] = value } }
    }

    fun updateShowOsmPaths(value: Boolean) {
        showOsmPaths.value = value
        scope.launch { dataStore.edit { it[SHOW_OSM_PATHS] = value } }
    }

    fun updateShowSavedPoints(value: Boolean) {
        showSavedPoints.value = value
        scope.launch { dataStore.edit { it[SHOW_SAVED_POINTS] = value } }
    }

    fun updateNveOverlay(value: NveOverlay?) {
        nveOverlay.value = value
        // Stored as "" rather than a missing key, so turning the overlay off doesn't fall back to
        // the legacy boolean and resurrect the old choice.
        scope.launch { dataStore.edit { it[NVE_OVERLAY] = value?.name ?: "" } }
    }

    /** Tapping the active overlay turns it off. */
    fun toggleNveOverlay(value: NveOverlay) =
        updateNveOverlay(if (nveOverlay.value == value) null else value)

    fun updateShowCoverage(value: Boolean) {
        showCoverage.value = value
        scope.launch { dataStore.edit { it[SHOW_COVERAGE] = value } }
    }

    fun updateCoverageOperator(value: CoverageOperator) {
        coverageOperator.value = value
        scope.launch { dataStore.edit { it[COVERAGE_OPERATOR] = value.name } }
    }

    fun updateCoverageTilesVersion(value: String) {
        if (coverageTilesVersion.value == value) return
        coverageTilesVersion.value = value
        scope.launch { dataStore.edit { it[COVERAGE_TILES_VERSION] = value } }
    }

    fun updateShowCoordGrid(value: Boolean) {
        showCoordGrid.value = value
        scope.launch { dataStore.edit { it[SHOW_COORD_GRID] = value } }
    }

    fun updateCrosshairActive(value: Boolean) {
        crosshairActive.value = value
        scope.launch { dataStore.edit { it[CROSSHAIR_ACTIVE] = value } }
    }

    fun updateNorthLocked(value: Boolean) {
        northLocked.value = value
        scope.launch { dataStore.edit { it[NORTH_LOCKED] = value } }
    }

    fun updateLargeCompass(value: Boolean) {
        largeCompass.value = value
        scope.launch { dataStore.edit { it[LARGE_COMPASS] = value } }
    }

    fun updateSelectedMapLayer(value: MapLayer) {
        selectedMapLayer.value = value
        scope.launch { dataStore.edit { it[SELECTED_MAP_LAYER] = value.name } }
    }

    fun updateCrashReportingEnabled(value: Boolean) {
        crashReportingEnabled.value = value
        scope.launch {
            dataStore.edit { it[CRASH_REPORTING_ENABLED] = value }
        }
    }

    fun markTrackingInfoSeen() {
        hasSeenTrackingInfo.value = true
        scope.launch { dataStore.edit { it[HAS_SEEN_TRACKING_INFO] = true } }
    }

    fun markOfflineMigrationNoticeShown() {
        offlineMigrationNoticeShown.value = true
        scope.launch { dataStore.edit { it[OFFLINE_MIGRATION_NOTICE_SHOWN] = true } }
    }

    fun confirmTrackingInfoAndEnable() {
        markTrackingInfoSeen()
        updateOnlineTrackingEnabled(true)
    }

    fun updateOnlineTrackingEnabled(value: Boolean) {
        onlineTrackingEnabled.value = value
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
        liveShareUntilMillis.value = value
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
                    if (liveShareUntilMillis.value == until) {
                        updateLiveShareUntil(0L)
                    }
                }
            }
        }
    }

    fun updateOfflineModeEnabled(value: Boolean) {
        offlineModeEnabled.value = value
        scope.launch {
            dataStore.edit { it[OFFLINE_MODE_ENABLED] = value }
        }
    }

    fun updateDownloadElevationData(value: Boolean) {
        downloadElevationData.value = value
        scope.launch { dataStore.edit { it[DOWNLOAD_ELEVATION_DATA] = value } }
    }

    fun updateDownloadMaxZoom(value: Int) {
        downloadMaxZoom.value = value
        scope.launch { dataStore.edit { it[DOWNLOAD_MAX_ZOOM] = value } }
    }

    fun updateThemeMode(value: String) {
        themeMode.value = value
        scope.launch {
            dataStore.edit { it[THEME_MODE] = value }
        }
    }

    fun updateCoordFormat(value: CoordFormat) {
        coordFormat.value = value
        scope.launch {
            dataStore.edit { it[COORD_FORMAT] = value.name }
        }
    }

    /** False when [clientId] is already followed or the set is full. */
    fun addFollowedClientId(clientId: String): Boolean {
        val current = followedClientIds.value
        if (clientId in current || current.size >= LiveTrackingFollower.MAX_FOLLOWED) return false
        setFollowedClientIds(current + clientId)
        return true
    }

    fun removeFollowedClientId(clientId: String) {
        setFollowedClientIds(followedClientIds.value - clientId)
    }

    fun clearFollowedClientIds() {
        setFollowedClientIds(emptyList())
    }

    /** Replaces the followed set; a group deep link carries a whole group at once. */
    fun setFollowedClientIds(value: List<String>) {
        val sanitized = LiveTrackingFollower.sanitize(value)
        followedClientIds.value = sanitized
        scope.launch {
            dataStore.edit {
                if (sanitized.isNotEmpty()) {
                    it[FOLLOWED_CLIENT_ID] = sanitized.joinToString(",")
                } else {
                    it.remove(FOLLOWED_CLIENT_ID)
                }
            }
        }
    }

    /** Sets or (when [nickname] is blank) clears the local nickname for [clientId]. */
    fun setClientNickname(clientId: String, nickname: String) {
        val trimmed = nickname.trim()
        val current = clientNicknames.value
        val updated = if (trimmed.isEmpty()) current - clientId else current + (clientId to trimmed)
        if (updated == current) return
        clientNicknames.value = updated
        scope.launch {
            dataStore.edit {
                if (updated.isEmpty()) it.remove(CLIENT_NICKNAMES)
                else it[CLIENT_NICKNAMES] = serializeNicknames(updated)
            }
        }
    }

    private fun serializeNicknames(map: Map<String, String>): String =
        buildJsonObject { for ((k, v) in map) put(k, v) }.toString()

    private fun deserializeNicknames(json: String?): Map<String, String> {
        if (json.isNullOrBlank()) return emptyMap()
        return try {
            Json.parseToJsonElement(json).jsonObject.mapValues { it.value.jsonPrimitive.content }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun removeFollowHistoryEntry(clientId: String) {
        val updated = followHistory.value - clientId
        if (updated.size == followHistory.value.size) return
        followHistory.value = updated
        scope.launch {
            dataStore.edit { it[FOLLOW_HISTORY] = updated.joinToString(",") }
        }
    }

    fun addFollowHistoryEntry(clientId: String) {
        val current = followHistory.value.toMutableList()
        current.remove(clientId)
        current.add(0, clientId)
        val updated = current.take(MAX_FOLLOW_HISTORY)
        followHistory.value = updated
        scope.launch {
            dataStore.edit { it[FOLLOW_HISTORY] = updated.joinToString(",") }
        }
    }

    fun addSearchHistoryEntry(result: PlaceSearchClient.SearchResult) {
        val current = searchHistory.value.toMutableList()
        current.removeAll { it.name == result.name && it.municipality == result.municipality }
        current.add(0, result)
        val updated = current.take(MAX_SEARCH_HISTORY)
        searchHistory.value = updated
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
        private val SHOW_COVERAGE = booleanPreferencesKey("show_coverage")
        private val COVERAGE_OPERATOR = stringPreferencesKey("coverage_operator")
        private val COVERAGE_TILES_VERSION = stringPreferencesKey("coverage_tiles_version")

        // Read-only legacy key: superseded by NVE_OVERLAY, still read to migrate old installs.
        private val SHOW_AVALANCHE_ZONES = booleanPreferencesKey("show_avalanche_zones")
        private val SHOW_COORD_GRID = booleanPreferencesKey("show_coord_grid")
        private val CROSSHAIR_ACTIVE = booleanPreferencesKey("crosshair_active")
        private val NORTH_LOCKED = booleanPreferencesKey("north_locked")
        private val LARGE_COMPASS = booleanPreferencesKey("large_compass")
        private val SELECTED_MAP_LAYER = stringPreferencesKey("selected_map_layer")
        private val HAS_SEEN_TRACKING_INFO = booleanPreferencesKey("has_seen_tracking_info")
        private val OFFLINE_MIGRATION_NOTICE_SHOWN = booleanPreferencesKey("offline_migration_notice_shown")
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
        // Comma-joined; the key predates following more than one client at a time.
        private val FOLLOWED_CLIENT_ID = stringPreferencesKey("followed_client_id")
        private val FOLLOW_HISTORY = stringPreferencesKey("follow_history")
        // JSON object of clientId -> nickname.
        private val CLIENT_NICKNAMES = stringPreferencesKey("client_nicknames")
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
