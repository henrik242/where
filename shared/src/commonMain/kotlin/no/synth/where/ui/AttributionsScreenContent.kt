package no.synth.where.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import no.synth.where.resources.Res
import no.synth.where.resources.*
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private data class Attribution(val name: String, val description: String, val license: String)

private val dataSources = listOf(
    Attribution("Kartverket", "Topographic maps, elevation data, place names, and county boundaries", "CC BY 4.0"),
    Attribution("OpenStreetMap", "Map data and tiles", "ODbL"),
    Attribution("OpenTopoMap", "Topographic map style", "CC-BY-SA"),
    Attribution("MapAnt.no", "Orienteering maps for Norway", ""),
    Attribution("Waymarked Trails", "Hiking trail overlay", "CC-BY-SA"),
    Attribution("NVE", "Avalanche terrain data", "NLOD"),
    Attribution("Tilezen Joerd", "Terrain hillshade and elevation tiles", "Public Domain"),
    Attribution("Open-Meteo", "Elevation data", "CC BY 4.0"),
    Attribution("Nominatim", "Geocoding and reverse geocoding", "ODbL"),
)

private val libraries = listOf(
    Attribution("MapLibre", "Map rendering engine", "BSD 3-Clause"),
    Attribution("Jetpack Compose", "UI framework", "Apache 2.0"),
    Attribution("Ktor", "HTTP client", "Apache 2.0"),
    Attribution("Room", "Local database", "Apache 2.0"),
    Attribution("kotlinx-serialization", "JSON parsing", "Apache 2.0"),
    Attribution("Firebase Crashlytics", "Crash reporting", "Apache 2.0"),
    Attribution("Timber", "Logging", "Apache 2.0"),
    Attribution("Protomaps", "Map font glyphs", "BSD 3-Clause"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttributionsScreenContent(
    onBackClick: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.attributions)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(painterResource(Res.drawable.ic_arrow_back), contentDescription = stringResource(Res.string.back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            SectionHeader(stringResource(Res.string.data_sources))
            dataSources.forEach { AttributionItem(it) }

            Spacer(modifier = Modifier.height(16.dp))

            SectionHeader(stringResource(Res.string.open_source_libraries))
            libraries.forEach { AttributionItem(it) }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
    )
}

@Composable
private fun AttributionItem(attribution: Attribution) {
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Text(
            text = if (attribution.license.isNotEmpty()) "${attribution.name} (${attribution.license})" else attribution.name,
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = attribution.description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
