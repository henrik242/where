import UIKit
import MapLibre
import Shared

class MapViewFactory: NSObject, MapViewProvider, MLNMapViewDelegate {
    private var mapView: MLNMapView?
    private var currentStyleJson: String?
    private var pendingTrackGeoJson: String?
    private var pendingTrackColor: String?

    private let trackSourceId = "track-source"
    private let trackLayerId = "track-layer"

    func createMapView() -> UIView {
        if let existing = self.mapView {
            return existing
        }
        let map = MLNMapView(frame: .zero)
        map.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        map.logoView.isHidden = true
        map.attributionButton.isHidden = true
        map.delegate = self
        // Default camera: center of Norway
        map.setCenter(CLLocationCoordinate2D(latitude: 65.0, longitude: 14.0), zoomLevel: 4, animated: false)
        self.mapView = map
        return map
    }

    func setStyle(json: String) {
        guard let mapView = self.mapView else { return }
        guard json != currentStyleJson else { return }
        currentStyleJson = json

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

    func zoomIn() {
        guard let mapView = self.mapView else { return }
        mapView.setZoomLevel(mapView.zoomLevel + 1, animated: true)
    }

    func zoomOut() {
        guard let mapView = self.mapView else { return }
        mapView.setZoomLevel(mapView.zoomLevel - 1, animated: true)
    }

    func updateTrackLine(geoJson: String, color: String) {
        guard let mapView = self.mapView, let style = mapView.style else {
            pendingTrackGeoJson = geoJson
            pendingTrackColor = color
            return
        }
        applyTrackLine(style: style, geoJson: geoJson, color: color)
    }

    func clearTrackLine() {
        pendingTrackGeoJson = nil
        pendingTrackColor = nil
        guard let mapView = self.mapView, let style = mapView.style else { return }
        removeTrackLine(style: style)
    }

    func getUserLocation() -> [KotlinDouble]? {
        guard let mapView = self.mapView,
              let location = mapView.userLocation?.location else { return nil }
        return [KotlinDouble(value: location.coordinate.latitude),
                KotlinDouble(value: location.coordinate.longitude)]
    }

    // MARK: - MLNMapViewDelegate

    func mapView(_ mapView: MLNMapView, didFinishLoading style: MLNStyle) {
        if let geoJson = pendingTrackGeoJson, let color = pendingTrackColor {
            applyTrackLine(style: style, geoJson: geoJson, color: color)
        }
    }

    // MARK: - Private

    private func applyTrackLine(style: MLNStyle, geoJson: String, color: String) {
        removeTrackLine(style: style)

        guard let data = geoJson.data(using: .utf8),
              let shape = try? MLNShape(data: data, encoding: String.Encoding.utf8.rawValue) else {
            print("Failed to parse track GeoJSON")
            return
        }

        let source = MLNShapeSource(identifier: trackSourceId, shape: shape, options: nil)
        style.addSource(source)

        let layer = MLNLineStyleLayer(identifier: trackLayerId, source: source)
        layer.lineColor = NSExpression(forConstantValue: UIColor(hex: color))
        layer.lineWidth = NSExpression(forConstantValue: 4)
        layer.lineOpacity = NSExpression(forConstantValue: 0.8)
        layer.lineCap = NSExpression(forConstantValue: "round")
        layer.lineJoin = NSExpression(forConstantValue: "round")
        style.addLayer(layer)

        pendingTrackGeoJson = geoJson
        pendingTrackColor = color
    }

    private func removeTrackLine(style: MLNStyle) {
        if let existingLayer = style.layer(withIdentifier: trackLayerId) {
            style.removeLayer(existingLayer)
        }
        if let existingSource = style.source(withIdentifier: trackSourceId) {
            style.removeSource(existingSource)
        }
    }
}

extension UIColor {
    convenience init(hex: String) {
        var hexSanitized = hex.trimmingCharacters(in: .whitespacesAndNewlines)
        hexSanitized = hexSanitized.replacingOccurrences(of: "#", with: "")

        var rgb: UInt64 = 0
        Scanner(string: hexSanitized).scanHexInt64(&rgb)

        let r, g, b, a: CGFloat
        if hexSanitized.count == 8 {
            a = CGFloat((rgb & 0xFF000000) >> 24) / 255.0
            r = CGFloat((rgb & 0x00FF0000) >> 16) / 255.0
            g = CGFloat((rgb & 0x0000FF00) >> 8) / 255.0
            b = CGFloat(rgb & 0x000000FF) / 255.0
        } else {
            r = CGFloat((rgb & 0xFF0000) >> 16) / 255.0
            g = CGFloat((rgb & 0x00FF00) >> 8) / 255.0
            b = CGFloat(rgb & 0x0000FF) / 255.0
            a = 1.0
        }
        self.init(red: r, green: g, blue: b, alpha: a)
    }
}
