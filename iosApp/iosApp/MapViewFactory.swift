import UIKit
import MapLibre
import Shared

class MapViewFactory: NSObject, MapViewProvider, MLNMapViewDelegate {
    private var mapView: MLNMapView?
    private var currentStyleJson: String?
    private var pendingTrackGeoJson: String?
    private var pendingTrackColor: String?
    private var pendingSavedPointsGeoJson: String?
    private var pendingRulerLineGeoJson: String?
    private var pendingRulerPointsGeoJson: String?

    private var longPressCallback: MapLongPressCallback?
    private var mapClickCallback: MapClickCallback?

    private let trackSourceId = "track-source"
    private let trackLayerId = "track-layer"
    private let savedPointsSourceId = "saved-points-source"
    private let savedPointsLayerId = "saved-points-layer"
    private let rulerLineSourceId = "ruler-line-source"
    private let rulerLineLayerId = "ruler-line-layer"
    private let rulerPointSourceId = "ruler-point-source"
    private let rulerPointLayerId = "ruler-point-layer"

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

        let longPress = UILongPressGestureRecognizer(target: self, action: #selector(handleLongPress(_:)))
        map.addGestureRecognizer(longPress)

        let tap = UITapGestureRecognizer(target: self, action: #selector(handleTap(_:)))
        tap.require(toFail: longPress)
        map.addGestureRecognizer(tap)

        self.mapView = map
        return map
    }

    @objc private func handleLongPress(_ gesture: UILongPressGestureRecognizer) {
        guard gesture.state == .began, let mapView = self.mapView else { return }
        let point = gesture.location(in: mapView)
        let coordinate = mapView.convert(point, toCoordinateFrom: mapView)
        longPressCallback?.onLongPress(latitude: coordinate.latitude, longitude: coordinate.longitude)
    }

    @objc private func handleTap(_ gesture: UITapGestureRecognizer) {
        guard let mapView = self.mapView else { return }
        let point = gesture.location(in: mapView)
        let coordinate = mapView.convert(point, toCoordinateFrom: mapView)
        mapClickCallback?.onMapClick(latitude: coordinate.latitude, longitude: coordinate.longitude)
    }

    func setOnLongPressCallback(callback: MapLongPressCallback?) {
        self.longPressCallback = callback
    }

    func setOnMapClickCallback(callback: MapClickCallback?) {
        self.mapClickCallback = callback
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

    func setCameraBounds(south: Double, west: Double, north: Double, east: Double, padding: Int32) {
        guard let mapView = self.mapView else { return }
        let sw = CLLocationCoordinate2D(latitude: south, longitude: west)
        let ne = CLLocationCoordinate2D(latitude: north, longitude: east)
        let bounds = MLNCoordinateBounds(sw: sw, ne: ne)
        let p = CGFloat(padding)
        let edgePadding = UIEdgeInsets(top: p, left: p, bottom: p, right: p)
        mapView.setVisibleCoordinateBounds(bounds, edgePadding: edgePadding, animated: true, completionHandler: nil)
    }

    func updateSavedPoints(geoJson: String) {
        guard let mapView = self.mapView, let style = mapView.style else {
            pendingSavedPointsGeoJson = geoJson
            return
        }
        applySavedPoints(style: style, geoJson: geoJson)
    }

    func clearSavedPoints() {
        pendingSavedPointsGeoJson = nil
        guard let mapView = self.mapView, let style = mapView.style else { return }
        removeSavedPoints(style: style)
    }

    func updateRuler(lineGeoJson: String, pointsGeoJson: String) {
        guard let mapView = self.mapView, let style = mapView.style else {
            pendingRulerLineGeoJson = lineGeoJson
            pendingRulerPointsGeoJson = pointsGeoJson
            return
        }
        applyRuler(style: style, lineGeoJson: lineGeoJson, pointsGeoJson: pointsGeoJson)
    }

    func clearRuler() {
        pendingRulerLineGeoJson = nil
        pendingRulerPointsGeoJson = nil
        guard let mapView = self.mapView, let style = mapView.style else { return }
        removeRuler(style: style)
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
        if let geoJson = pendingSavedPointsGeoJson {
            applySavedPoints(style: style, geoJson: geoJson)
        }
        if let lineGeoJson = pendingRulerLineGeoJson, let pointsGeoJson = pendingRulerPointsGeoJson {
            applyRuler(style: style, lineGeoJson: lineGeoJson, pointsGeoJson: pointsGeoJson)
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

    private func applySavedPoints(style: MLNStyle, geoJson: String) {
        removeSavedPoints(style: style)

        guard let data = geoJson.data(using: .utf8),
              let shape = try? MLNShape(data: data, encoding: String.Encoding.utf8.rawValue) else {
            print("Failed to parse saved points GeoJSON")
            return
        }

        let source = MLNShapeSource(identifier: savedPointsSourceId, shape: shape, options: nil)
        style.addSource(source)

        let layer = MLNCircleStyleLayer(identifier: savedPointsLayerId, source: source)
        layer.circleRadius = NSExpression(forConstantValue: 6)
        layer.circleColor = NSExpression(forKeyPath: "color")
        layer.circleStrokeColor = NSExpression(forConstantValue: UIColor.white)
        layer.circleStrokeWidth = NSExpression(forConstantValue: 2)
        style.addLayer(layer)

        pendingSavedPointsGeoJson = geoJson
    }

    private func removeSavedPoints(style: MLNStyle) {
        if let existingLayer = style.layer(withIdentifier: savedPointsLayerId) {
            style.removeLayer(existingLayer)
        }
        if let existingSource = style.source(withIdentifier: savedPointsSourceId) {
            style.removeSource(existingSource)
        }
    }

    private func applyRuler(style: MLNStyle, lineGeoJson: String, pointsGeoJson: String) {
        removeRuler(style: style)

        // Ruler line (orange dashed)
        if let lineData = lineGeoJson.data(using: .utf8),
           let lineShape = try? MLNShape(data: lineData, encoding: String.Encoding.utf8.rawValue) {
            let lineSource = MLNShapeSource(identifier: rulerLineSourceId, shape: lineShape, options: nil)
            style.addSource(lineSource)

            let lineLayer = MLNLineStyleLayer(identifier: rulerLineLayerId, source: lineSource)
            lineLayer.lineColor = NSExpression(forConstantValue: UIColor(hex: "#FFA500"))
            lineLayer.lineWidth = NSExpression(forConstantValue: 3)
            lineLayer.lineOpacity = NSExpression(forConstantValue: 0.9)
            lineLayer.lineDashPattern = NSExpression(forConstantValue: [2, 2])
            style.addLayer(lineLayer)
        }

        // Ruler points (orange circles with white stroke)
        if let pointsData = pointsGeoJson.data(using: .utf8),
           let pointsShape = try? MLNShape(data: pointsData, encoding: String.Encoding.utf8.rawValue) {
            let pointSource = MLNShapeSource(identifier: rulerPointSourceId, shape: pointsShape, options: nil)
            style.addSource(pointSource)

            let pointLayer = MLNCircleStyleLayer(identifier: rulerPointLayerId, source: pointSource)
            pointLayer.circleRadius = NSExpression(forConstantValue: 6)
            pointLayer.circleColor = NSExpression(forConstantValue: UIColor(hex: "#FFA500"))
            pointLayer.circleStrokeWidth = NSExpression(forConstantValue: 2)
            pointLayer.circleStrokeColor = NSExpression(forConstantValue: UIColor.white)
            style.addLayer(pointLayer)
        }

        pendingRulerLineGeoJson = lineGeoJson
        pendingRulerPointsGeoJson = pointsGeoJson
    }

    private func removeRuler(style: MLNStyle) {
        if let layer = style.layer(withIdentifier: rulerLineLayerId) { style.removeLayer(layer) }
        if let source = style.source(withIdentifier: rulerLineSourceId) { style.removeSource(source) }
        if let layer = style.layer(withIdentifier: rulerPointLayerId) { style.removeLayer(layer) }
        if let source = style.source(withIdentifier: rulerPointSourceId) { style.removeSource(source) }
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
