import UIKit
import MapLibre
import Shared

class MapViewFactory: MapViewProvider {
    private var mapView: MLNMapView?

    func createMapView() -> UIView {
        let map = MLNMapView(frame: .zero)
        map.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        map.logoView.isHidden = true
        map.attributionButton.isHidden = true
        // Default camera: center of Norway
        map.setCenter(CLLocationCoordinate2D(latitude: 65.0, longitude: 14.0), zoomLevel: 4, animated: false)
        self.mapView = map
        return map
    }

    func setStyle(json: String) {
        guard let mapView = self.mapView else { return }

        // Write style JSON to temp file and load as file:// URL
        let tempDir = NSTemporaryDirectory()
        let styleFile = (tempDir as NSString).appendingPathComponent("mapstyle.json")
        do {
            try json.write(toFile: styleFile, atomically: true, encoding: .utf8)
            let styleURL = URL(fileURLWithPath: styleFile)
            mapView.styleURL = styleURL
        } catch {
            print("Failed to write map style: \(error)")
        }
    }

    func setCamera(latitude: Double, longitude: Double, zoom: Double) {
        guard let mapView = self.mapView else { return }
        mapView.setCenter(
            CLLocationCoordinate2D(latitude: latitude, longitude: longitude),
            zoomLevel: zoom,
            animated: true
        )
    }

    func setShowsUserLocation(show: Bool) {
        guard let mapView = self.mapView else { return }
        mapView.showsUserLocation = show
    }
}
