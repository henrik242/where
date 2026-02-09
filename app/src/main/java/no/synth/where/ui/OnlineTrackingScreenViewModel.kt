package no.synth.where.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import no.synth.where.data.ClientIdManager
import no.synth.where.data.UserPreferences
import javax.inject.Inject

@HiltViewModel
class OnlineTrackingScreenViewModel @Inject constructor(
    private val userPreferences: UserPreferences,
    private val clientIdManager: ClientIdManager
) : ViewModel() {

    val onlineTrackingEnabled = userPreferences.onlineTrackingEnabled
    val trackingServerUrl = userPreferences.trackingServerUrl

    private val _clientId = MutableStateFlow("")
    val clientId: StateFlow<String> = _clientId.asStateFlow()

    init {
        viewModelScope.launch {
            _clientId.value = clientIdManager.getClientId()
        }
    }

    fun toggleTracking(enabled: Boolean) {
        userPreferences.updateOnlineTrackingEnabled(enabled)
    }

    fun regenerateClientId() {
        viewModelScope.launch {
            _clientId.value = clientIdManager.regenerateClientId()
        }
    }
}
