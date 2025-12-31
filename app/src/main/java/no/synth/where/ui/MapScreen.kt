package no.synth.where.ui

import android.Manifest
import android.util.Log
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
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import no.synth.where.data.MapStyle
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

@Composable
fun MapScreen(
    onDownloadClick: () -> Unit
) {
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
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
            MapLibreMapView()
        }
    }
}

@Composable
fun MapLibreMapView() {
    val lifecycleOwner = LocalLifecycleOwner.current
    var mapView by remember { mutableStateOf<MapView?>(null) }

    AndroidView(
        factory = { ctx ->
            Log.d("MapScreen", "Creating MapView in factory")
            MapView(ctx).also { mv ->
                mapView = mv
                Log.d("MapScreen", "MapView created, calling onCreate")
                mv.onCreate(null)
                Log.d("MapScreen", "onCreate called, setting up map")

                mv.getMapAsync { map ->
                    Log.d("MapScreen", "Map is ready - getMapAsync callback triggered")

                    map.cameraPosition = CameraPosition.Builder()
                        .target(LatLng(65.0, 10.0))
                        .zoom(5.0)
                        .build()

                    Log.d("MapScreen", "Camera position set")

                    try {
                        val styleJson = MapStyle.getStyle()
                        Log.d("MapScreen", "Generated style JSON (length: ${styleJson.length})")
                        Log.d("MapScreen", "Style preview: ${styleJson.take(300)}...")


                        map.setStyle(Style.Builder().fromJson(styleJson), object : Style.OnStyleLoaded {
                            override fun onStyleLoaded(style: Style) {
                                Log.d("MapScreen", "Custom Kartverket style loaded successfully!")
                                Log.d("MapScreen", "Map sources: ${style.sources?.joinToString { it.id }}")
                                Log.d("MapScreen", "Map layers: ${style.layers?.joinToString { it.id }}")

                                // Force a re-render
                                map.triggerRepaint()
                                Log.d("MapScreen", "Triggered map repaint")
                            }
                        })
                    } catch (e: Exception) {
                        Log.e("MapScreen", "Failed to set map style", e)
                        e.printStackTrace()
                    }
                }
            }
        },
        modifier = Modifier.fillMaxSize()
    )

    // Manage Lifecycle
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            mapView?.let { mv ->
                when (event) {
                    Lifecycle.Event.ON_START -> mv.onStart()
                    Lifecycle.Event.ON_RESUME -> mv.onResume()
                    Lifecycle.Event.ON_PAUSE -> mv.onPause()
                    Lifecycle.Event.ON_STOP -> mv.onStop()
                    Lifecycle.Event.ON_DESTROY -> mv.onDestroy()
                    else -> {}
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView?.onDestroy()
        }
    }
}

