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
import androidx.compose.material.icons.filled.MyLocation
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
import org.maplibre.android.location.LocationComponent
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import android.content.Context
import android.location.LocationManager
import androidx.core.content.ContextCompat

enum class MapLayer {
    OSM,
    OPENTOPOMAP,
    KARTVERKET,
    TOPORASTER
}

@Composable
fun MapScreen(
    onDownloadClick: () -> Unit
) {
    var mapInstance by remember { mutableStateOf<MapLibreMap?>(null) }
    var selectedLayer by remember { mutableStateOf(MapLayer.KARTVERKET) }
    var showLayerMenu by remember { mutableStateOf(false) }
    var hasLocationPermission by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasLocationPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        Log.d("MapScreen", "Location permission granted: $hasLocationPermission")
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
    LaunchedEffect(selectedLayer) {
        // Style will be updated via MapLibreMapView's LaunchedEffect
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
                                Text(if (selectedLayer == MapLayer.KARTVERKET) "✓ Kartverket" else "Kartverket")
                            },
                            onClick = {
                                selectedLayer = MapLayer.KARTVERKET
                                showLayerMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(if (selectedLayer == MapLayer.TOPORASTER) "✓ Toporaster (Hiking)" else "Toporaster (Hiking)")
                            },
                            onClick = {
                                selectedLayer = MapLayer.TOPORASTER
                                showLayerMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(if (selectedLayer == MapLayer.OSM) "✓ OpenStreetMap" else "OpenStreetMap")
                            },
                            onClick = {
                                selectedLayer = MapLayer.OSM
                                showLayerMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(if (selectedLayer == MapLayer.OPENTOPOMAP) "✓ OpenTopoMap (Hiking)" else "OpenTopoMap (Hiking)")
                            },
                            onClick = {
                                selectedLayer = MapLayer.OPENTOPOMAP
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

                SmallFloatingActionButton(
                    onClick = onDownloadClick,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Filled.Download, contentDescription = "Download Maps")
                }

                androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(8.dp))

                // Go to my location button
                SmallFloatingActionButton(
                    onClick = {
                        mapInstance?.let { map ->
                            val locationComponent = map.locationComponent
                            if (locationComponent.isLocationComponentEnabled) {
                                locationComponent.lastKnownLocation?.let { location ->
                                    map.animateCamera(
                                        org.maplibre.android.camera.CameraUpdateFactory.newLatLngZoom(
                                            LatLng(location.latitude, location.longitude),
                                            15.0
                                        )
                                    )
                                }
                            }
                        }
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Filled.MyLocation, contentDescription = "My Location")
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            MapLibreMapView(
                onMapReady = { mapInstance = it },
                selectedLayer = selectedLayer,
                hasLocationPermission = hasLocationPermission
            )
        }
    }
}

@SuppressWarnings("MissingPermission")
private fun enableLocationComponent(map: MapLibreMap, style: Style, context: Context, hasPermission: Boolean) {
    if (!hasPermission) {
        Log.w("MapScreen", "Cannot enable location component - permission not granted")
        return
    }

    try {
        val locationComponent = map.locationComponent

        // Check if already activated
        if (locationComponent.isLocationComponentActivated) {
            Log.d("MapScreen", "Location component already activated, just ensuring it's enabled")
            locationComponent.isLocationComponentEnabled = true
            locationComponent.renderMode = RenderMode.COMPASS
            return
        }

        Log.d("MapScreen", "Activating location component...")

        // Activate with options - use default location engine
        locationComponent.activateLocationComponent(
            LocationComponentActivationOptions.builder(context, style)
                .useDefaultLocationEngine(true)
                .build()
        )

        // Enable location component
        locationComponent.isLocationComponentEnabled = true

        Log.d("MapScreen", "Location component enabled: ${locationComponent.isLocationComponentEnabled}")

        // Set render mode to show compass (includes heading/bearing)
        locationComponent.renderMode = RenderMode.COMPASS

        Log.d("MapScreen", "Render mode set to: ${locationComponent.renderMode}")


        Log.d("MapScreen", "Location component fully configured with heading support")
    } catch (e: SecurityException) {
        Log.e("MapScreen", "Location permission not granted", e)
    } catch (e: Exception) {
        Log.e("MapScreen", "Failed to enable location component", e)
        e.printStackTrace()
    }
}

@Composable
fun MapLibreMapView(
    onMapReady: (MapLibreMap) -> Unit = {},
    selectedLayer: MapLayer = MapLayer.KARTVERKET,
    hasLocationPermission: Boolean = false
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    val context = LocalContext.current

    // Update style when layer selection changes
    LaunchedEffect(selectedLayer, map) {
        map?.let { mapInstance ->
            try {
                val styleJson = MapStyle.getStyle(context, selectedLayer)
                Log.d("MapScreen", "Switching to $selectedLayer")

                mapInstance.setStyle(Style.Builder().fromJson(styleJson), object : Style.OnStyleLoaded {
                    override fun onStyleLoaded(style: Style) {
                        Log.d("MapScreen", "Layer switched successfully")

                        // Enable location component after style is loaded
                        enableLocationComponent(mapInstance, style, context, hasLocationPermission)
                    }
                })
            } catch (e: Exception) {
                Log.e("MapScreen", "Failed to switch map layer", e)
            }
        }
    }

    // Re-enable location when permission is granted
    LaunchedEffect(hasLocationPermission, map) {
        val mapInstance = map
        if (hasLocationPermission && mapInstance != null) {
            Log.d("MapScreen", "Permission granted, enabling location on existing map")
            mapInstance.style?.let { style ->
                enableLocationComponent(mapInstance, style, context, hasLocationPermission)
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
                        val styleJson = MapStyle.getStyle(ctx, selectedLayer)
                        Log.d("MapScreen", "Generated style JSON (length: ${styleJson.length})")
                        Log.d("MapScreen", "Style preview: ${styleJson.take(300)}...")

                        mapInstance.setStyle(Style.Builder().fromJson(styleJson), object : Style.OnStyleLoaded {
                            override fun onStyleLoaded(style: Style) {
                                Log.d("MapScreen", "Custom map style loaded successfully!")
                                Log.d("MapScreen", "Map sources: ${style.sources.joinToString { it.id }}")
                                Log.d("MapScreen", "Map layers: ${style.layers.joinToString { it.id }}")

                                // Enable location component
                                enableLocationComponent(mapInstance, style, ctx, hasLocationPermission)

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
