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
import no.synth.where.resources.Res
import no.synth.where.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
fun OnlineTrackingScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as no.synth.where.WhereApplication
    val viewModel: OnlineTrackingScreenViewModel = viewModel { OnlineTrackingScreenViewModel(app.userPreferences, app.clientIdManager) }
    val isTrackingEnabled by viewModel.onlineTrackingEnabled.collectAsState()
    val clientId by viewModel.clientId.collectAsState()
    val trackingServerUrl by viewModel.trackingServerUrl.collectAsState()
    var showRegenerateDialog by remember { mutableStateOf(false) }

    // Pre-resolve strings for use in non-composable lambdas
    val shareTrackingChooserStr = stringResource(Res.string.share_tracking_chooser)
    val shareTrackingMessageFmt = stringResource(Res.string.share_tracking_message)
    val liveLocationTrackingStr = stringResource(Res.string.live_location_tracking)

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
        onDismissRegenerate = { showRegenerateDialog = false }
    )
}
