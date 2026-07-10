package no.synth.where.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import no.synth.where.WhereApplication

@Composable
fun DownloadQueueScreen(onBackClick: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as WhereApplication
    val queue by app.downloadQueueManager.queue.collectAsState()

    DownloadQueueScreenContent(
        queue = queue,
        onCancelDownload = { id -> app.downloadQueueManager.cancel(id) },
        onClearFinished = { app.downloadQueueManager.clearFinished() },
        onBackClick = onBackClick,
    )
}
