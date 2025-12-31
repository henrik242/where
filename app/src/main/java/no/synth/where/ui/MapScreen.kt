package no.synth.where.ui

import android.Manifest
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

@Composable
fun MapScreen(
    onDownloadClick: () -> Unit
) {
    val context = LocalContext.current


    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Handle permission results if needed
    }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        )
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = onDownloadClick) {
                Icon(Icons.Filled.Download, contentDescription = "Download Maps")
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            OsmMapView()
        }
    }
}

@Composable
fun OsmMapView() {
    val context = LocalContext.current
    val mapView = remember { MapView(context) }

    AndroidView(
        factory = {
            mapView.apply {
                setMultiTouchControls(true)

                // Set Kartverket Tile Source
                setTileSource(no.synth.where.data.KartverketTileSource)

                // Wireframe for non-downloaded parts
                overlayManager.tilesOverlay.loadingBackgroundColor = android.graphics.Color.TRANSPARENT
                overlayManager.tilesOverlay.loadingLineColor = android.graphics.Color.LTGRAY

                // Default location (Norway)
                controller.setZoom(5.0)
                controller.setCenter(GeoPoint(65.0, 10.0))

                // My Location Overlay
                val locationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(context), this)
                locationOverlay.enableMyLocation()
                overlays.add(locationOverlay)
            }
        },
        modifier = Modifier.fillMaxSize(),
        onRelease = {
            it.onDetach()
        }
    )
}

