package no.synth.where.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import no.synth.where.data.DownloadStatus
import no.synth.where.data.QueuedDownload
import no.synth.where.data.isTerminal
import no.synth.where.data.summary
import no.synth.where.resources.Res
import no.synth.where.resources.*
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadQueueScreenContent(
    queue: List<QueuedDownload>,
    onCancelDownload: (id: String) -> Unit,
    onClearFinished: () -> Unit,
    onBackClick: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.downloads)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(painterResource(Res.drawable.ic_arrow_back), contentDescription = stringResource(Res.string.back))
                    }
                }
            )
        }
    ) { paddingValues ->
        if (queue.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stringResource(Res.string.no_downloads),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.padding(paddingValues).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                val summary = queue.summary()
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = when {
                            summary.allSucceeded -> stringResource(Res.string.all_downloads_complete)
                            summary.allDone -> stringResource(Res.string.downloads_finished)
                            else -> stringResource(Res.string.queue_progress, summary.position, summary.total)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (queue.any { it.status.isTerminal }) {
                        TextButton(onClick = onClearFinished) {
                            Text(stringResource(Res.string.clear_finished))
                        }
                    }
                }
            }

            items(queue, key = { it.id }) { item ->
                QueueItemRow(item = item, onCancel = { onCancelDownload(item.id) })
            }
        }
    }
}

@Composable
private fun QueueItemRow(item: QueuedDownload, onCancel: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(item.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    item.layerDisplayName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                when (item.status) {
                    DownloadStatus.DOWNLOADING -> {
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { item.overallProgress / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                        val caption = if (item.demProgress >= 0) {
                            "${stringResource(Res.string.map_tiles)} ${item.mapProgress}%  ·  " +
                                "${stringResource(Res.string.elevation_data)} ${item.demProgress}%"
                        } else {
                            "${item.mapProgress}%"
                        }
                        Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    DownloadStatus.QUEUED -> Text(
                        stringResource(Res.string.queued),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    DownloadStatus.COMPLETED -> Text(
                        stringResource(Res.string.downloaded),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    DownloadStatus.FAILED -> Text(
                        stringResource(Res.string.download_failed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    DownloadStatus.CANCELLED -> Text(
                        stringResource(Res.string.cancelled),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            when (item.status) {
                DownloadStatus.DOWNLOADING -> TextButton(onClick = onCancel) { Text(stringResource(Res.string.stop)) }
                DownloadStatus.QUEUED -> TextButton(onClick = onCancel) { Text(stringResource(Res.string.cancel)) }
                else -> {}
            }
        }
    }
}
