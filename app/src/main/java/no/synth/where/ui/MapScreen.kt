package no.synth.where.ui

import android.Manifest
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import no.synth.where.data.MapStyle
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style

@Composable
fun MapScreen(
    onDownloadClick: () -> Unit
) {
    var mapInstance by remember { mutableStateOf<MapLibreMap?>(null) }
    var useKartverket by remember { mutableStateOf(true) }
    var showLayerMenu by remember { mutableStateOf(false) }

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

    // Update map style when layer selection changes
    LaunchedEffect(useKartverket) {
        mapInstance?.let { map ->
            val context = map.style?.let { (it as? Any)?.let { null } } ?: return@let
            // Map style will be regenerated on next load
            Log.d("MapScreen", "Layer changed to: ${if (useKartverket) "Kartverket" else "OSM"}")
        }
    }

    Scaffold(
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                // Layer selector dropdown
                Box {
                    SmallFloatingActionButton(
                        onClick = { showLayerMenu = true },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Filled.Layers, contentDescription = "Select Layer")
                    }

                    DropdownMenu(
                        expanded = showLayerMenu,
                        onDismissRequest = { showLayerMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(if (useKartverket) "✓ Kartverket" else "Kartverket")
                            },
                            onClick = {
                                useKartverket = true
                                showLayerMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(if (!useKartverket) "✓ OpenStreetMap" else "OpenStreetMap")
                            },
                            onClick = {
                                useKartverket = false
                                showLayerMenu = false
                            }
                        )
                    }
                }

                androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(8.dp))

                // Zoom controls
                SmallFloatingActionButton(
                    onClick = {
                        mapInstance?.let { map ->
                            val currentZoom = map.cameraPosition.zoom
                            map.animateCamera(
                                org.maplibre.android.camera.CameraUpdateFactory.zoomTo(currentZoom + 1)
                            )
                        }
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Zoom In")
                }

                androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(8.dp))

                SmallFloatingActionButton(
                    onClick = {
                        mapInstance?.let { map ->
                            val currentZoom = map.cameraPosition.zoom
                            map.animateCamera(
                                org.maplibre.android.camera.CameraUpdateFactory.zoomTo(currentZoom - 1)
                            )
                        }
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Filled.Remove, contentDescription = "Zoom Out")
                }

                androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(8.dp))

                FloatingActionButton(onClick = onDownloadClick) {
                    Icon(Icons.Filled.Download, contentDescription = "Download Maps")
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            MapLibreMapView(
                onMapReady = { mapInstance = it },
                useKartverket = useKartverket
            )
        }
    }
}

@Composable
fun MapLibreMapView(
    onMapReady: (MapLibreMap) -> Unit = {},
    useKartverket: Boolean = true
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    val context = LocalContext.current

    // Update style when layer selection changes
    LaunchedEffect(useKartverket, map) {
        map?.let { mapInstance ->
            try {
                val styleJson = MapStyle.getStyle(context, useKartverket)
                Log.d("MapScreen", "Switching to ${if (useKartverket) "Kartverket" else "OSM"}")

                mapInstance.setStyle(Style.Builder().fromJson(styleJson), object : Style.OnStyleLoaded {
                    override fun onStyleLoaded(style: Style) {
                        Log.d("MapScreen", "Layer switched successfully")
                    }
                })
            } catch (e: Exception) {
                Log.e("MapScreen", "Failed to switch map layer", e)
            }
        }
    }

    AndroidView(
        factory = { ctx ->
            Log.d("MapScreen", "Creating MapView in factory")
            MapView(ctx).also { mv ->
                mapView = mv
                Log.d("MapScreen", "MapView created, calling onCreate")
                mv.onCreate(null)
                Log.d("MapScreen", "onCreate called, setting up map")

                mv.getMapAsync { mapInstance ->
                    Log.d("MapScreen", "Map is ready - getMapAsync callback triggered")

                    map = mapInstance
                    onMapReady(mapInstance)

                    mapInstance.cameraPosition = CameraPosition.Builder()
                        .target(LatLng(65.0, 10.0))
                        .zoom(5.0)
                        .build()

                    Log.d("MapScreen", "Camera position set")

                    try {
                        val styleJson = MapStyle.getStyle(ctx, useKartverket)
                        Log.d("MapScreen", "Generated style JSON (length: ${styleJson.length})")
                        Log.d("MapScreen", "Style preview: ${styleJson.take(300)}...")

                        mapInstance.setStyle(Style.Builder().fromJson(styleJson), object : Style.OnStyleLoaded {
                            override fun onStyleLoaded(style: Style) {
                                Log.d("MapScreen", "Custom map style loaded successfully!")
                                Log.d("MapScreen", "Map sources: ${style.sources.joinToString { it.id }}")
                                Log.d("MapScreen", "Map layers: ${style.layers.joinToString { it.id }}")

                                // Force a re-render
                                mapInstance.triggerRepaint()
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
