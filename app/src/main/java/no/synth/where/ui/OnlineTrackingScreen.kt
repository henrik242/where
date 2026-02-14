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
import org.koin.androidx.compose.koinViewModel
import no.synth.where.R

@Composable
fun OnlineTrackingScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val resources = context.resources
    val viewModel: OnlineTrackingScreenViewModel = koinViewModel()
    val isTrackingEnabled by viewModel.onlineTrackingEnabled.collectAsState()
    val clientId by viewModel.clientId.collectAsState()
    val trackingServerUrl by viewModel.trackingServerUrl.collectAsState()
    var showRegenerateDialog by remember { mutableStateOf(false) }

    OnlineTrackingScreenContent(
        isTrackingEnabled = isTrackingEnabled,
        clientId = clientId,
        showRegenerateDialog = showRegenerateDialog,
        onBackClick = onBackClick,
        onToggleTracking = { viewModel.toggleTracking(it) },
        onViewOnWeb = {
            val url = "${trackingServerUrl}?clients=$clientId"
            context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        },
        onShare = {
            val url = "${trackingServerUrl}?clients=$clientId"
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, resources.getString(R.string.share_tracking_message, url))
                putExtra(Intent.EXTRA_SUBJECT, resources.getString(R.string.live_location_tracking))
            }
            context.startActivity(Intent.createChooser(shareIntent, resources.getString(R.string.share_tracking_chooser)))
        },
        onRegenerateClick = { showRegenerateDialog = true },
        onConfirmRegenerate = {
            viewModel.regenerateClientId()
            showRegenerateDialog = false
        },
        onDismissRegenerate = { showRegenerateDialog = false }
    )
}
