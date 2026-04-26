package no.synth.where.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import no.synth.where.data.ClientIdManager
import no.synth.where.data.LiveTrackingFollower
import no.synth.where.data.UserPreferences

class OnlineTrackingScreenViewModel(
    private val userPreferences: UserPreferences,
    private val clientIdManager: ClientIdManager,
    private val liveTrackingFollower: LiveTrackingFollower
) : ViewModel() {

    val onlineTrackingEnabled = userPreferences.onlineTrackingEnabled
    val hasSeenTrackingInfo = userPreferences.hasSeenTrackingInfo
    val trackingServerUrl = userPreferences.trackingServerUrl
    val viewerCount = userPreferences.viewerCount
    val followedClientId = userPreferences.followedClientId
    val followHistory = userPreferences.followHistory
    val alwaysShareUntilMillis = userPreferences.alwaysShareUntilMillis

    private val _clientId = MutableStateFlow("")
    val clientId: StateFlow<String> = _clientId.asStateFlow()

    private val _followClientIdInput = MutableStateFlow("")
    val followClientIdInput: StateFlow<String> = _followClientIdInput.asStateFlow()

    init {
        viewModelScope.launch {
            _clientId.value = clientIdManager.getClientId()
        }
    }

    fun toggleTracking(enabled: Boolean) {
        userPreferences.updateOnlineTrackingEnabled(enabled)
    }

    fun startAlwaysShare(durationMillis: Long) {
        userPreferences.startAlwaysShare(durationMillis)
    }

    fun stopAlwaysShare() {
        userPreferences.stopAlwaysShare()
    }

    fun confirmTrackingInfoAndEnable() {
        userPreferences.confirmTrackingInfoAndEnable()
    }

    fun regenerateClientId() {
        userPreferences.stopAlwaysShare()
        viewModelScope.launch {
            _clientId.value = clientIdManager.regenerateClientId()
        }
    }

    fun updateFollowClientIdInput(value: String) {
        _followClientIdInput.value = value
    }

    fun startFollowing() {
        val followId = _followClientIdInput.value
        if (!LiveTrackingFollower.CLIENT_ID_REGEX.matches(followId)) return
        if (followId == _clientId.value) return // prevent self-follow
        userPreferences.updateFollowedClientId(followId)
        userPreferences.addFollowHistoryEntry(followId)
        liveTrackingFollower.follow(followId)
        _followClientIdInput.value = ""
    }

    fun stopFollowing() {
        userPreferences.updateFollowedClientId(null)
        liveTrackingFollower.stopFollowing()
    }
}
