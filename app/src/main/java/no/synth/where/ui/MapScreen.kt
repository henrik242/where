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
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat

enum class MapLayer {
    OSM,
    OPENTOPOMAP,
    KARTVERKET,
    TOPORASTER,
    SJOKARTRASTER
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
                                Text(if (selectedLayer == MapLayer.SJOKARTRASTER) "✓ Sjøkartraster (Nautical)" else "Sjøkartraster (Nautical)")
                            },
                            onClick = {
                                selectedLayer = MapLayer.SJOKARTRASTER
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
    if (!hasPermission) return

    try {
        val locationComponent = map.locationComponent

        if (locationComponent.isLocationComponentActivated) {
            locationComponent.isLocationComponentEnabled = true
            locationComponent.renderMode = RenderMode.COMPASS
            if (locationComponent.lastKnownLocation == null && isEmulator()) {
                forceLocationOnEmulator(map, locationComponent)
            }
            return
        }

        locationComponent.activateLocationComponent(
            LocationComponentActivationOptions.builder(context, style)
                .useDefaultLocationEngine(true)
                .build()
        )

        locationComponent.isLocationComponentEnabled = true
        locationComponent.renderMode = RenderMode.COMPASS

        if (locationComponent.lastKnownLocation == null && isEmulator()) {
            forceLocationOnEmulator(map, locationComponent)
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

private fun isEmulator(): Boolean {
    return (Build.FINGERPRINT.startsWith("google/sdk_gphone")
            || Build.FINGERPRINT.startsWith("generic")
            || Build.FINGERPRINT.contains("unknown")
            || Build.MODEL.contains("google_sdk")
            || Build.MODEL.contains("Emulator")
            || Build.MODEL.contains("Android SDK built for")
            || Build.MANUFACTURER.contains("Genymotion")
            || Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic")
            || "google_sdk" == Build.PRODUCT)
}

@SuppressWarnings("MissingPermission")
private fun forceLocationOnEmulator(map: MapLibreMap, locationComponent: LocationComponent) {
    try {
        val mockLocation = Location("emulator_mock").apply {
            latitude = 59.9139
            longitude = 10.7522
            accuracy = 10f
            time = System.currentTimeMillis()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                elapsedRealtimeNanos = android.os.SystemClock.elapsedRealtimeNanos()
            }
        }
        locationComponent.forceLocationUpdate(mockLocation)
    } catch (e: Exception) {
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

    LaunchedEffect(selectedLayer, map) {
        map?.let { mapInstance ->
            try {
                val styleJson = MapStyle.getStyle(context, selectedLayer)
                mapInstance.setStyle(Style.Builder().fromJson(styleJson), object : Style.OnStyleLoaded {
                    override fun onStyleLoaded(style: Style) {
                        enableLocationComponent(mapInstance, style, context, hasLocationPermission)
                    }
                })
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    LaunchedEffect(hasLocationPermission, map) {
        val mapInstance = map
        if (hasLocationPermission && mapInstance != null) {
            mapInstance.style?.let { style ->
                enableLocationComponent(mapInstance, style, context, hasLocationPermission)
            }
        }
    }

    AndroidView(
        factory = { ctx ->
            MapView(ctx).also { mv ->
                mapView = mv
                mv.onCreate(null)

                mv.getMapAsync { mapInstance ->
                    map = mapInstance
                    onMapReady(mapInstance)

                    mapInstance.cameraPosition = CameraPosition.Builder()
                        .target(LatLng(65.0, 10.0))
                        .zoom(5.0)
                        .build()

                    try {
                        val styleJson = MapStyle.getStyle(ctx, selectedLayer)
                        mapInstance.setStyle(Style.Builder().fromJson(styleJson), object : Style.OnStyleLoaded {
                            override fun onStyleLoaded(style: Style) {
                                enableLocationComponent(mapInstance, style, ctx, hasLocationPermission)
                                mapInstance.triggerRepaint()
                            }
                        })
                    } catch (e: Exception) {
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
