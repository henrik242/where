package no.synth.where.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import no.synth.where.data.ClientIdManager
import no.synth.where.data.LiveTrackingFollower
import no.synth.where.data.UserPreferences
import no.synth.where.data.setNickname
import no.synth.where.data.startFollowing
import no.synth.where.data.stopFollowingAll
import no.synth.where.data.unfollow
import no.synth.where.resources.Res
import no.synth.where.resources.share_tracking_message
import no.synth.where.ui.map.followedFriends
import org.jetbrains.compose.resources.stringResource

/** State and actions of the online tracking screen. Android holds it in a ViewModel so it survives rotation. */
class OnlineTrackingState(
    val userPreferences: UserPreferences,
    val follower: LiveTrackingFollower,
    private val clientIdManager: ClientIdManager,
    private val scope: CoroutineScope,
) {
    var clientId by mutableStateOf("")
        private set
    var followClientIdInput by mutableStateOf("")
    var showRegenerateDialog by mutableStateOf(false)
    var showTrackingInfoDialog by mutableStateOf(false)

    init {
        scope.launch { clientId = clientIdManager.getClientId() }
    }

    fun toggleTracking(enabled: Boolean) {
        if (enabled && !userPreferences.hasSeenTrackingInfo.value) {
            showTrackingInfoDialog = true
        } else {
            userPreferences.updateOnlineTrackingEnabled(enabled)
        }
    }

    fun confirmTrackingInfo() {
        showTrackingInfoDialog = false
        userPreferences.confirmTrackingInfoAndEnable()
    }

    fun regenerateClientId() {
        showRegenerateDialog = false
        userPreferences.stopLiveShare()
        scope.launch { clientId = clientIdManager.regenerateClientId() }
    }

    /** Returns true when the id was added, so the caller can jump to the map. */
    fun startFollowing(): Boolean {
        val added = startFollowing(userPreferences, follower, followClientIdInput, clientId)
        if (added) followClientIdInput = ""
        return added
    }
}

/**
 * The online tracking screen on every platform. [onShare] gets the localized share text;
 * [onLiveShareStarted] lets Android start its location service.
 */
@Composable
fun OnlineTrackingScreen(
    state: OnlineTrackingState,
    onBackClick: () -> Unit,
    onNavigateToMap: () -> Unit,
    openUrl: (String) -> Unit,
    onShare: (String) -> Unit,
    onLiveShareStarted: () -> Unit = {},
    backgroundLocationMissing: Boolean = false,
    onOpenLocationSettings: () -> Unit = {},
) {
    val prefs = state.userPreferences
    val isTrackingEnabled by prefs.onlineTrackingEnabled.collectAsState()
    val trackingServerUrl by prefs.trackingServerUrl.collectAsState()
    val viewerCount by prefs.viewerCount.collectAsState()
    val followedClientIds by prefs.followedClientIds.collectAsState()
    val followHistory by prefs.followHistory.collectAsState()
    val clientNicknames by prefs.clientNicknames.collectAsState()
    val liveShareUntilMillis by prefs.liveShareUntilMillis.collectAsState()
    val offlineModeEnabled by prefs.offlineModeEnabled.collectAsState()
    val followState by state.follower.state.collectAsState()
    val followedFriends = remember(followedClientIds, followState, clientNicknames) {
        followedFriends(
            followedClientIds,
            (followState as? LiveTrackingFollower.FollowState.Following)?.tracks ?: emptyList(),
            clientNicknames,
        )
    }
    val trackingUrl = "$trackingServerUrl/${state.clientId}"
    val shareText = stringResource(Res.string.share_tracking_message, trackingUrl)

    OnlineTrackingScreenContent(
        isTrackingEnabled = isTrackingEnabled,
        clientId = state.clientId,
        viewerCount = viewerCount,
        showRegenerateDialog = state.showRegenerateDialog,
        showTrackingInfoDialog = state.showTrackingInfoDialog,
        onBackClick = onBackClick,
        onToggleTracking = state::toggleTracking,
        onViewOnWeb = { openUrl(trackingUrl) },
        onShare = { onShare(shareText) },
        onRegenerateClick = { state.showRegenerateDialog = true },
        onConfirmRegenerate = state::regenerateClientId,
        onDismissRegenerate = { state.showRegenerateDialog = false },
        onConfirmTrackingInfo = state::confirmTrackingInfo,
        onDismissTrackingInfo = { state.showTrackingInfoDialog = false },
        liveShareUntilMillis = liveShareUntilMillis,
        onStartLiveShare = { durationMillis ->
            prefs.startLiveShare(durationMillis)
            onLiveShareStarted()
        },
        onStopLiveShare = { prefs.stopLiveShare() },
        offlineModeEnabled = offlineModeEnabled,
        onDisableOfflineMode = { prefs.updateOfflineModeEnabled(false) },
        backgroundLocationMissing = backgroundLocationMissing,
        onOpenLocationSettings = onOpenLocationSettings,
        followedFriends = followedFriends,
        followClientIdInput = state.followClientIdInput,
        followHistory = followHistory,
        onFollowClientIdChange = { state.followClientIdInput = it },
        onStartFollowing = { if (state.startFollowing()) onNavigateToMap() },
        onUnfollow = { unfollow(prefs, state.follower, it) },
        onSetNickname = { id, nickname -> setNickname(prefs, state.follower, id, nickname) },
        onStopFollowing = { stopFollowingAll(prefs, state.follower) },
    )
}
