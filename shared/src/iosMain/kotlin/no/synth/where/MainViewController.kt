package no.synth.where

import androidx.compose.ui.window.ComposeUIViewController
import no.synth.where.data.OfflineMapManager
import no.synth.where.ui.map.MapViewProvider
import platform.UIKit.UIViewController

fun MainViewController(
    mapViewProvider: MapViewProvider,
    offlineMapManager: OfflineMapManager,
    hexMapViewProvider: MapViewProvider
): UIViewController =
    ComposeUIViewController { IosApp(mapViewProvider, offlineMapManager, hexMapViewProvider) }
