package no.synth.where.ui

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import no.synth.where.data.LiveTrackingFollower
import no.synth.where.resources.Res
import no.synth.where.resources.*
import no.synth.where.service.LocationTrackingService
import no.synth.where.ui.map.followedFriends
import org.jetbrains.compose.resources.stringResource

@Composable
fun OnlineTrackingScreen(
    onBackClick: () -> Unit,
    onNavigateToMap: () -> Unit = onBackClick
) {
    val context = LocalContext.current
    val app = context.applicationContext as no.synth.where.WhereApplication
    val viewModel: OnlineTrackingScreenViewModel = viewModel {
        OnlineTrackingScreenViewModel(app.userPreferences, app.clientIdManager, app.liveTrackingFollower)
    }
    val isTrackingEnabled by viewModel.onlineTrackingEnabled.collectAsState()
    val hasSeenTrackingInfo by viewModel.hasSeenTrackingInfo.collectAsState()
    val clientId by viewModel.clientId.collectAsState()
    val viewerCount by viewModel.viewerCount.collectAsState()
    val trackingServerUrl by viewModel.trackingServerUrl.collectAsState()
    val followedClientIds by viewModel.followedClientIds.collectAsState()
    val followState by viewModel.followState.collectAsState()
    val followedFriends = remember(followedClientIds, followState) {
        followedFriends(followedClientIds, (followState as? LiveTrackingFollower.FollowState.Following)?.tracks ?: emptyList())
    }
    val followClientIdInput by viewModel.followClientIdInput.collectAsState()
    val followHistory by viewModel.followHistory.collectAsState()
    val liveShareUntilMillis by viewModel.liveShareUntilMillis.collectAsState()
    val offlineModeEnabled by viewModel.offlineModeEnabled.collectAsState()
    var showRegenerateDialog by remember { mutableStateOf(false) }
    var showTrackingInfoDialog by remember { mutableStateOf(false) }

    // Pre-resolve strings for use in non-composable lambdas
    val shareTrackingChooserStr = stringResource(Res.string.share_tracking_chooser)
    val shareTrackingMessageFmt = stringResource(Res.string.share_tracking_message)
    val liveLocationTrackingStr = stringResource(Res.string.live_location_tracking)

    OnlineTrackingScreenContent(
        isTrackingEnabled = isTrackingEnabled,
        clientId = clientId,
        viewerCount = viewerCount,
        showRegenerateDialog = showRegenerateDialog,
        showTrackingInfoDialog = showTrackingInfoDialog,
        onBackClick = onBackClick,
        onToggleTracking = { enabled ->
            if (enabled && !hasSeenTrackingInfo) {
                showTrackingInfoDialog = true
            } else {
                viewModel.toggleTracking(enabled)
            }
        },
        onViewOnWeb = {
            val url = "${trackingServerUrl}/$clientId"
            context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        },
        onShare = {
            val url = "${trackingServerUrl}/$clientId"
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, String.format(shareTrackingMessageFmt, url))
                putExtra(Intent.EXTRA_SUBJECT, liveLocationTrackingStr)
            }
            context.startActivity(Intent.createChooser(shareIntent, shareTrackingChooserStr))
        },
        onRegenerateClick = { showRegenerateDialog = true },
        onConfirmRegenerate = {
            viewModel.regenerateClientId()
            showRegenerateDialog = false
        },
        onDismissRegenerate = { showRegenerateDialog = false },
        onConfirmTrackingInfo = {
            showTrackingInfoDialog = false
            viewModel.confirmTrackingInfoAndEnable()
        },
        onDismissTrackingInfo = { showTrackingInfoDialog = false },
        liveShareUntilMillis = liveShareUntilMillis,
        onStartLiveShare = { durationMillis ->
            viewModel.startLiveShare(durationMillis)
            LocationTrackingService.start(context)
        },
        onStopLiveShare = { viewModel.stopLiveShare() },
        offlineModeEnabled = offlineModeEnabled,
        onDisableOfflineMode = { viewModel.disableOfflineMode() },
        followedFriends = followedFriends,
        followClientIdInput = followClientIdInput,
        followHistory = followHistory,
        onFollowClientIdChange = { viewModel.updateFollowClientIdInput(it) },
        onStartFollowing = {
            if (viewModel.startFollowing()) onNavigateToMap()
        },
        onUnfollow = { viewModel.unfollow(it) },
        onStopFollowing = { viewModel.stopFollowing() }
    )
}
