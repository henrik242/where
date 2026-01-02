package no.synth.where.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import no.synth.where.data.MapDownloadManager
import no.synth.where.data.Region
import no.synth.where.data.RegionsRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val downloadManager = remember { MapDownloadManager(context) }
    val regions = remember { RegionsRepository.getRegions(context) }

    var downloadingRegion by remember { mutableStateOf<Region?>(null) }
    var downloadProgress by remember { mutableStateOf(0) }
    var downloadStatus by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Download Offline Maps") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            if (downloadingRegion != null) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Downloading ${downloadingRegion?.name}...")
                    Spacer(modifier = Modifier.height(16.dp))
                    LinearProgressIndicator(progress = { downloadProgress / 100f })
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("$downloadProgress%")
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(regions) { region ->
                        ListItem(
                            headlineContent = { Text(region.name) },
                            supportingContent = { Text("Download map for offline use") },
                            trailingContent = {
                                Button(onClick = {
                                    downloadingRegion = region
                                    downloadStatus = "Starting..."
                                    scope.launch {
                                        downloadManager.downloadRegion(
                                            region = region,
                                            minZoom = 5,
                                            maxZoom = 12, // Limit zoom to save space/time for demo
                                            onProgress = { progress ->
                                                downloadProgress = progress
                                            },
                                            onComplete = { success ->
                                                downloadingRegion = null
                                                downloadProgress = 0
                                                downloadStatus = if (success) "Done" else "Failed"
                                            }
                                        )
                                    }
                                }) {
                                    Text("Download")
                                }
                            }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

