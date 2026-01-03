package no.synth.where.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    onDownloadClick: () -> Unit,
    showCountyBorders: Boolean,
    onShowCountyBordersChange: (Boolean) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // County borders toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Show County Borders",
                    style = MaterialTheme.typography.bodyLarge
                )
                Switch(
                    checked = showCountyBorders,
                    onCheckedChange = onShowCountyBordersChange
                )
            }

            HorizontalDivider()

            // Download Manager option
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onDownloadClick() }
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Download Manager",
                    style = MaterialTheme.typography.bodyLarge
                )
                Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = "Go to Download Manager"
                )
            }

            HorizontalDivider()
        }
    }
}

