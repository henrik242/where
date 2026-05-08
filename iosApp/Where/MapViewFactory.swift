import UIKit
import UIKit.UIGestureRecognizerSubclass
import MapLibre
import Shared

class MapViewFactory: NSObject, MapViewProvider, MLNMapViewDelegate, MLNNetworkConfigurationDelegate, UIGestureRecognizerDelegate {
    private var mapView: MLNMapView?
    private var currentStyleJson: String?
    private var isMapConnected = true
    private var staleFixTimer: Timer?
    private static let freshTintColor: UIColor = .systemBlue
    private static let staleTintColor: UIColor = .systemGray
    private static let staleFixThreshold: TimeInterval = 30.0
    private var pendingTrackGeoJson: String?
    private var pendingTrackColor: String?
    private var pendingSavedPointsGeoJson: String?
    private var pendingRulerLineGeoJson: String?
    private var pendingRulerPointsGeoJson: String?
    private var pendingSearchResultsGeoJson: String?
    private var pendingSearchHighlightGeoJson: String?
    private var pendingFriendTrackGeoJson: String?
    private var pendingFriendTrackColor: String?
    private var pendingCoordGridGeoJson: String?

    private var longPressCallback: MapLongPressCallback?
    private var mapClickCallback: MapClickCallback?
    private var cameraMoveCallback: MapCameraMoveCallback?
    private var twoFingerTapCallback: MapTwoFingerTapCallback?
    private var styleVersion = 0

    private let trackSourceId = "track-source"
    private let trackLayerId = "track-layer"
    private let savedPointsSourceId = "saved-points-source"
    private let savedPointsLayerId = "saved-points-layer"
    private let rulerLineSourceId = "ruler-line-source"
    private let rulerLineLayerId = "ruler-line-layer"
    private let rulerPointSourceId = "ruler-point-source"
    private let rulerPointLayerId = "ruler-point-layer"
    private let searchResultsSourceId = "search-results-source"
    private let searchResultsLayerId = "search-results-layer"
    private let searchHighlightSourceId = "search-highlight-source"
    private let searchHighlightLayerId = "search-highlight-layer"
    private let friendTrackLineSourceId = "friend-track-line-source"
    private let friendTrackPointSourceId = "friend-track-point-source"
    private let friendTrackLineLayerId = "friend-track-line-layer"
    private let friendTrackPointLayerId = "friend-track-point-layer"
    private let friendTrackLabelLayerId = "friend-track-label-layer"
    private let coordGridSourceId = "coord-grid-source"
    private let coordGridLayerId = "coord-grid-line-layer"
    private let coordGridZoneLayerId = "coord-grid-zone-layer"
    private let coordGridLabelLayerId = "coord-grid-label-layer"
    private let coordGridCellLayerId = "coord-grid-cell-layer"

    func createMapView() -> UIView {
        if let existing = self.mapView {
            return existing
        }
        let map = MLNMapView(frame: .zero)
        map.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        map.logoView.isHidden = true
        map.delegate = self
        // Default camera: center of Norway
        map.setCenter(CLLocationCoordinate2D(latitude: 65.0, longitude: 14.0), zoomLevel: 4, animated: false)

        let longPress = UILongPressGestureRecognizer(target: self, action: #selector(handleLongPress(_:)))
        map.addGestureRecognizer(longPress)

        let tap = UITapGestureRecognizer(target: self, action: #selector(handleTap(_:)))
        tap.require(toFail: longPress)
        map.addGestureRecognizer(tap)

        let twoFingerTap = TwoFingerTapGestureRecognizer(target: self, action: #selector(handleTwoFingerTap(_:)))
        twoFingerTap.delegate = self
        map.addGestureRecognizer(twoFingerTap)

        // Make MLN's pinch and two-finger-tap-to-zoom recognizers wait for our
        // tap to fail, so a clean two-finger tap doesn't also nudge the zoom.
        // (Android can't do this preventively — see MapLibreMapView.kt's
        // cancelTransitions() call which suppresses the same gesture post-hoc.)
        for gr in map.gestureRecognizers ?? [] where gr !== twoFingerTap {
            if gr is UIPinchGestureRecognizer {
                gr.require(toFail: twoFingerTap)
            } else if let t = gr as? UITapGestureRecognizer, t.numberOfTouchesRequired == 2 {
                gr.require(toFail: twoFingerTap)
            }
        }

        self.mapView = map

        // If setStyle was called before the map was created, apply it now.
        if let json = currentStyleJson {
            styleVersion += 1
            let tempDir = NSTemporaryDirectory()
            let styleFile = (tempDir as NSString).appendingPathComponent("mapstyle_\(styleVersion).json")
            if let _ = try? json.write(toFile: styleFile, atomically: true, encoding: .utf8) {
                map.styleURL = URL(fileURLWithPath: styleFile)
            }
        }

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

    @objc private func handleTwoFingerTap(_ gesture: TwoFingerTapGestureRecognizer) {
        guard gesture.state == .recognized, let mapView = self.mapView else { return }
        let p1 = gesture.touch1
        let p2 = gesture.touch2
        let screenDist = hypot(p2.x - p1.x, p2.y - p1.y)
        guard screenDist >= 48 else { return }
        let c1 = mapView.convert(p1, toCoordinateFrom: mapView)
        let c2 = mapView.convert(p2, toCoordinateFrom: mapView)
        let scale = mapView.window?.screen.scale ?? UIScreen.main.scale
        // CGPoints are in UIKit logical points; the Compose overlay draws in
        // pixels, so multiply by screen scale before sending coordinates over.
        twoFingerTapCallback?.onTwoFingerTap(
            screenX1: Float(p1.x * scale), screenY1: Float(p1.y * scale),
            screenX2: Float(p2.x * scale), screenY2: Float(p2.y * scale),
            lat1: c1.latitude, lng1: c1.longitude,
            lat2: c2.latitude, lng2: c2.longitude
        )
    }

    func setOnLongPressCallback(callback: MapLongPressCallback?) {
        self.longPressCallback = callback
    }

    func setOnMapClickCallback(callback: MapClickCallback?) {
        self.mapClickCallback = callback
    }

    func setStyle(json: String) {
        guard json != currentStyleJson else { return }
        currentStyleJson = json
        guard let mapView = self.mapView else { return }

        // Use a versioned filename so each update gets a distinct URL,
        // forcing MapLibre to reload even when the path would otherwise be the same.
        styleVersion += 1
        let tempDir = NSTemporaryDirectory()
        let styleFile = (tempDir as NSString).appendingPathComponent("mapstyle_\(styleVersion).json")
        do {
            try json.write(toFile: styleFile, atomically: true, encoding: .utf8)
            mapView.styleURL = URL(fileURLWithPath: styleFile)
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

    func panTo(latitude: Double, longitude: Double) {
        guard let mapView = self.mapView else { return }
        mapView.setCenter(
            CLLocationCoordinate2D(latitude: latitude, longitude: longitude),
            animated: true
        )
    }

    func setShowsUserLocation(show: Bool) {
        guard let mapView = self.mapView else { return }
        mapView.showsUserLocation = show
        if show {
            mapView.tintColor = MapViewFactory.freshTintColor
            startStaleFixMonitor()
        } else {
            stopStaleFixMonitor()
        }
    }

    /// Tints the user-location puck grey when the latest fix is older than
    /// `staleFixThreshold`. Mirrors the Android LocationComponentOptions.staleStateTimeout
    /// behavior so users can tell the dot is no longer fresh.
    private func startStaleFixMonitor() {
        staleFixTimer?.invalidate()
        let timer = Timer.scheduledTimer(withTimeInterval: 2.0, repeats: true) { [weak self] _ in
            self?.refreshUserLocationTint()
        }
        RunLoop.main.add(timer, forMode: .common)
        staleFixTimer = timer
    }

    private func stopStaleFixMonitor() {
        staleFixTimer?.invalidate()
        staleFixTimer = nil
    }

    private func refreshUserLocationTint() {
        guard let mapView = self.mapView else { return }
        let lastFix = mapView.userLocation?.location?.timestamp
        let stale = lastFix.map { Date().timeIntervalSince($0) > MapViewFactory.staleFixThreshold } ?? true
        let target: UIColor = stale ? MapViewFactory.staleTintColor : MapViewFactory.freshTintColor
        if mapView.tintColor != target {
            mapView.tintColor = target
        }
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
        setCameraBounds(south: south, west: west, north: north, east: east, padding: padding, maxZoom: 0)
    }

    func setCameraBounds(south: Double, west: Double, north: Double, east: Double, padding: Int32, maxZoom: Int32) {
        guard let mapView = self.mapView else { return }
        let sw = CLLocationCoordinate2D(latitude: south, longitude: west)
        let ne = CLLocationCoordinate2D(latitude: north, longitude: east)
        let bounds = MLNCoordinateBounds(sw: sw, ne: ne)
        let p = CGFloat(padding)
        let edgePadding = UIEdgeInsets(top: p, left: p, bottom: p, right: p)
        mapView.setVisibleCoordinateBounds(bounds, edgePadding: edgePadding, animated: true, completionHandler: nil)
        if maxZoom > 0 && mapView.zoomLevel > Double(maxZoom) {
            let center = CLLocationCoordinate2D(
                latitude: (south + north) / 2,
                longitude: (west + east) / 2
            )
            mapView.setCenter(center, zoomLevel: Double(maxZoom), animated: true)
        }
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

    func updateSearchResults(geoJson: String) {
        guard let mapView = self.mapView, let style = mapView.style else {
            pendingSearchResultsGeoJson = geoJson
            return
        }
        applySearchResults(style: style, geoJson: geoJson)
    }

    func clearSearchResults() {
        pendingSearchResultsGeoJson = nil
        guard let mapView = self.mapView, let style = mapView.style else { return }
        removeSearchResults(style: style)
    }

    func highlightSearchResult(geoJson: String) {
        guard let mapView = self.mapView, let style = mapView.style else {
            pendingSearchHighlightGeoJson = geoJson
            return
        }
        applySearchHighlight(style: style, geoJson: geoJson)
    }

    func clearHighlightedSearchResult() {
        pendingSearchHighlightGeoJson = nil
        guard let mapView = self.mapView, let style = mapView.style else { return }
        removeSearchHighlight(style: style)
    }

    func updateFriendTrackLine(geoJson: String, color: String) {
        guard let mapView = self.mapView, let style = mapView.style else {
            pendingFriendTrackGeoJson = geoJson
            pendingFriendTrackColor = color
            return
        }
        applyFriendTrackLine(style: style, geoJson: geoJson, color: color)
    }

    func clearFriendTrackLine() {
        pendingFriendTrackGeoJson = nil
        pendingFriendTrackColor = nil
        guard let mapView = self.mapView, let style = mapView.style else { return }
        removeFriendTrackLine(style: style)
    }

    func setConnected(connected: Bool) {
        isMapConnected = connected
        MLNNetworkConfiguration.sharedManager.delegate = connected ? nil : self
    }

    @objc(willSendRequest:)
    func willSend(_ request: NSMutableURLRequest) -> NSMutableURLRequest {
        if !isMapConnected {
            request.cachePolicy = .returnCacheDataDontLoad
        }
        return request
    }

    func getCameraCenter() -> [KotlinDouble]? {
        guard let mapView = self.mapView else { return nil }
        let center = mapView.centerCoordinate
        return [KotlinDouble(value: center.latitude),
                KotlinDouble(value: center.longitude)]
    }

    func updateCoordGrid(geoJson: String) {
        guard let mapView = self.mapView, let style = mapView.style else {
            pendingCoordGridGeoJson = geoJson
            return
        }
        applyCoordGrid(style: style, geoJson: geoJson)
    }

    func clearCoordGrid() {
        pendingCoordGridGeoJson = nil
        guard let mapView = self.mapView, let style = mapView.style else { return }
        removeCoordGrid(style: style)
    }

    func setOnCameraMoveCallback(callback: MapCameraMoveCallback?) {
        self.cameraMoveCallback = callback
    }

    func setOnTwoFingerTapCallback(callback: MapTwoFingerTapCallback?) {
        self.twoFingerTapCallback = callback
    }

    func getUserLocation() -> [KotlinDouble]? {
        guard let mapView = self.mapView,
              let location = mapView.userLocation?.location else { return nil }
        return [KotlinDouble(value: location.coordinate.latitude),
                KotlinDouble(value: location.coordinate.longitude)]
    }

    func projectToScreen(latitude: Double, longitude: Double) -> ScreenPoint? {
        guard let mapView = self.mapView else { return nil }
        let coord = CLLocationCoordinate2D(latitude: latitude, longitude: longitude)
        let point = mapView.convert(coord, toPointTo: mapView)
        let scale = mapView.window?.screen.scale ?? UIScreen.main.scale
        return ScreenPoint(x: Float(point.x * scale), y: Float(point.y * scale))
    }

    // MARK: - UIGestureRecognizerDelegate

    func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer) -> Bool {
        // Allow our two-finger tap to coexist with the map's pan/pinch recognizers
        return gestureRecognizer is TwoFingerTapGestureRecognizer || otherGestureRecognizer is TwoFingerTapGestureRecognizer
    }

    // MARK: - MLNMapViewDelegate

    func mapViewRegionIsChanging(_ mapView: MLNMapView) {
        let center = mapView.centerCoordinate
        cameraMoveCallback?.onCameraMove(latitude: center.latitude, longitude: center.longitude, zoom: mapView.zoomLevel, bearing: mapView.direction)
    }

    func mapView(_ mapView: MLNMapView, regionDidChangeAnimated animated: Bool) {
        let center = mapView.centerCoordinate
        cameraMoveCallback?.onCameraMove(latitude: center.latitude, longitude: center.longitude, zoom: mapView.zoomLevel, bearing: mapView.direction)
    }

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
        if let geoJson = pendingSearchResultsGeoJson {
            applySearchResults(style: style, geoJson: geoJson)
        }
        if let geoJson = pendingSearchHighlightGeoJson {
            applySearchHighlight(style: style, geoJson: geoJson)
        }
        if let geoJson = pendingFriendTrackGeoJson, let color = pendingFriendTrackColor {
            applyFriendTrackLine(style: style, geoJson: geoJson, color: color)
        }
        if let geoJson = pendingCoordGridGeoJson {
            applyCoordGrid(style: style, geoJson: geoJson)
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

    private func applySearchResults(style: MLNStyle, geoJson: String) {
        removeSearchResults(style: style)

        guard let data = geoJson.data(using: .utf8),
              let shape = try? MLNShape(data: data, encoding: String.Encoding.utf8.rawValue) else {
            print("Failed to parse search results GeoJSON")
            return
        }

        let source = MLNShapeSource(identifier: searchResultsSourceId, shape: shape, options: nil)
        style.addSource(source)

        let layer = MLNCircleStyleLayer(identifier: searchResultsLayerId, source: source)
        layer.circleRadius = NSExpression(forConstantValue: 7)
        layer.circleColor = NSExpression(forConstantValue: UIColor(hex: "#E91E63"))
        layer.circleStrokeColor = NSExpression(forConstantValue: UIColor.white)
        layer.circleStrokeWidth = NSExpression(forConstantValue: 2)
        layer.circleOpacity = NSExpression(forConstantValue: 0.9)
        style.addLayer(layer)

        pendingSearchResultsGeoJson = geoJson
    }

    private func removeSearchResults(style: MLNStyle) {
        if let layer = style.layer(withIdentifier: searchResultsLayerId) { style.removeLayer(layer) }
        if let source = style.source(withIdentifier: searchResultsSourceId) { style.removeSource(source) }
    }

    private func applySearchHighlight(style: MLNStyle, geoJson: String) {
        removeSearchHighlight(style: style)

        guard let data = geoJson.data(using: .utf8),
              let shape = try? MLNShape(data: data, encoding: String.Encoding.utf8.rawValue) else {
            print("Failed to parse search highlight GeoJSON")
            return
        }

        let source = MLNShapeSource(identifier: searchHighlightSourceId, shape: shape, options: nil)
        style.addSource(source)

        let layer = MLNCircleStyleLayer(identifier: searchHighlightLayerId, source: source)
        layer.circleRadius = NSExpression(forConstantValue: 11)
        layer.circleColor = NSExpression(forConstantValue: UIColor(hex: "#E91E63"))
        layer.circleStrokeColor = NSExpression(forConstantValue: UIColor.white)
        layer.circleStrokeWidth = NSExpression(forConstantValue: 3)
        layer.circleOpacity = NSExpression(forConstantValue: 1.0)
        style.addLayer(layer)

        pendingSearchHighlightGeoJson = geoJson
    }

    private func removeSearchHighlight(style: MLNStyle) {
        if let layer = style.layer(withIdentifier: searchHighlightLayerId) { style.removeLayer(layer) }
        if let source = style.source(withIdentifier: searchHighlightSourceId) { style.removeSource(source) }
    }

    private func applyFriendTrackLine(style: MLNStyle, geoJson: String, color: String) {
        removeFriendTrackLine(style: style)

        guard let data = geoJson.data(using: .utf8),
              let shape = try? MLNShape(data: data, encoding: String.Encoding.utf8.rawValue) else {
            print("Failed to parse friend track GeoJSON")
            return
        }

        // Split features by geometry type into separate sources
        var lineShapes: [MLNShape] = []
        var pointShapes: [MLNShape] = []

        if let collection = shape as? MLNShapeCollectionFeature {
            for s in collection.shapes {
                if s is MLNPolylineFeature {
                    lineShapes.append(s)
                } else if s is MLNPointFeature {
                    pointShapes.append(s)
                }
            }
        } else if shape is MLNPolylineFeature {
            lineShapes.append(shape)
        } else if shape is MLNPointFeature {
            pointShapes.append(shape)
        }

        if !lineShapes.isEmpty {
            let lineSource = MLNShapeSource(identifier: friendTrackLineSourceId, shapes: lineShapes, options: nil)
            style.addSource(lineSource)

            let lineLayer = MLNLineStyleLayer(identifier: friendTrackLineLayerId, source: lineSource)
            lineLayer.lineColor = NSExpression(forConstantValue: UIColor(hex: color))
            lineLayer.lineWidth = NSExpression(forConstantValue: 4)
            lineLayer.lineOpacity = NSExpression(forConstantValue: 0.8)
            lineLayer.lineDashPattern = NSExpression(forConstantValue: [4, 2])
            lineLayer.lineCap = NSExpression(forConstantValue: "round")
            lineLayer.lineJoin = NSExpression(forConstantValue: "round")
            style.addLayer(lineLayer)
        }

        if !pointShapes.isEmpty {
            let pointSource = MLNShapeSource(identifier: friendTrackPointSourceId, shapes: pointShapes, options: nil)
            style.addSource(pointSource)

            let pointLayer = MLNCircleStyleLayer(identifier: friendTrackPointLayerId, source: pointSource)
            pointLayer.circleRadius = NSExpression(forConstantValue: 8)
            pointLayer.circleColor = NSExpression(forConstantValue: UIColor(hex: color))
            pointLayer.circleStrokeColor = NSExpression(forConstantValue: UIColor.white)
            pointLayer.circleStrokeWidth = NSExpression(forConstantValue: 2)
            style.addLayer(pointLayer)

            let labelLayer = MLNSymbolStyleLayer(identifier: friendTrackLabelLayerId, source: pointSource)
            labelLayer.text = NSExpression(forKeyPath: "clientId")
            labelLayer.textFontSize = NSExpression(forConstantValue: 12)
            labelLayer.textColor = NSExpression(forConstantValue: UIColor(hex: color))
            labelLayer.textHaloColor = NSExpression(forConstantValue: UIColor.white)
            labelLayer.textHaloWidth = NSExpression(forConstantValue: 1.5)
            labelLayer.textOffset = NSExpression(forConstantValue: NSValue(cgVector: CGVector(dx: 0, dy: 1.5)))
            labelLayer.textAnchor = NSExpression(forConstantValue: "top")
            style.addLayer(labelLayer)
        }

        pendingFriendTrackGeoJson = geoJson
        pendingFriendTrackColor = color
    }

    private func applyCoordGrid(style: MLNStyle, geoJson: String) {
        guard let data = geoJson.data(using: .utf8),
              let shape = try? MLNShape(data: data, encoding: String.Encoding.utf8.rawValue) else {
            return
        }

        if let existingSource = style.source(withIdentifier: coordGridSourceId) as? MLNShapeSource {
            existingSource.shape = shape
        } else {
            removeCoordGrid(style: style)
            let source = MLNShapeSource(identifier: coordGridSourceId, shape: shape, options: nil)
            style.addSource(source)

            let belowIds = [trackLayerId, rulerLineLayerId, friendTrackLineLayerId, savedPointsLayerId]
            let belowLayer = belowIds.lazy.compactMap({ style.layer(withIdentifier: $0) }).first

            let zoneLayer = MLNLineStyleLayer(identifier: coordGridZoneLayerId, source: source)
            zoneLayer.lineColor = NSExpression(forConstantValue: UIColor(red: 0.902, green: 0.494, blue: 0.133, alpha: 1.0))
            zoneLayer.lineWidth = NSExpression(forConstantValue: 1.5)
            zoneLayer.lineOpacity = NSExpression(forConstantValue: 0.7)
            zoneLayer.predicate = NSPredicate(format: "zone != nil")
            if let below = belowLayer { style.insertLayer(zoneLayer, below: below) } else { style.addLayer(zoneLayer) }

            let lineLayer = MLNLineStyleLayer(identifier: coordGridLayerId, source: source)
            lineLayer.lineColor = NSExpression(forConstantValue: UIColor.black)
            lineLayer.lineWidth = NSExpression(forConstantValue: 0.8)
            lineLayer.lineOpacity = NSExpression(forConstantValue: 0.3)
            lineLayer.predicate = NSPredicate(format: "zone = nil")
            if let below = belowLayer { style.insertLayer(lineLayer, below: below) } else { style.addLayer(lineLayer) }

            let labelLayer = MLNSymbolStyleLayer(identifier: coordGridLabelLayerId, source: source)
            labelLayer.text = NSExpression(forKeyPath: "label")
            labelLayer.textFontNames = NSExpression(forConstantValue: ["NotoSansRegular"])
            labelLayer.textFontSize = NSExpression(forConstantValue: 10)
            labelLayer.textColor = NSExpression(forConstantValue: UIColor.black.withAlphaComponent(0.6))
            labelLayer.textHaloColor = NSExpression(forConstantValue: UIColor.white)
            labelLayer.textHaloWidth = NSExpression(forConstantValue: 1.5)
            labelLayer.textAnchor = NSExpression(forKeyPath: "anchor")
            labelLayer.predicate = NSPredicate(format: "cell = nil")
            if let below = belowLayer { style.insertLayer(labelLayer, below: below) } else { style.addLayer(labelLayer) }

            let cellLayer = MLNSymbolStyleLayer(identifier: coordGridCellLayerId, source: source)
            cellLayer.text = NSExpression(forKeyPath: "label")
            cellLayer.textFontNames = NSExpression(forConstantValue: ["NotoSansRegular"])
            cellLayer.textFontSize = NSExpression(forConstantValue: 14)
            cellLayer.textColor = NSExpression(forConstantValue: UIColor(red: 0.776, green: 0.157, blue: 0.157, alpha: 0.85))
            cellLayer.textHaloColor = NSExpression(forConstantValue: UIColor.white)
            cellLayer.textHaloWidth = NSExpression(forConstantValue: 1.5)
            cellLayer.textAnchor = NSExpression(forKeyPath: "anchor")
            cellLayer.textAllowsOverlap = NSExpression(forConstantValue: false)
            cellLayer.textIgnoresPlacement = NSExpression(forConstantValue: false)
            cellLayer.textPadding = NSExpression(forConstantValue: 8)
            cellLayer.predicate = NSPredicate(format: "cell = YES")
            if let below = belowLayer { style.insertLayer(cellLayer, below: below) } else { style.addLayer(cellLayer) }
        }

        pendingCoordGridGeoJson = geoJson
    }

    private func removeCoordGrid(style: MLNStyle) {
        if let layer = style.layer(withIdentifier: coordGridCellLayerId) { style.removeLayer(layer) }
        if let layer = style.layer(withIdentifier: coordGridLabelLayerId) { style.removeLayer(layer) }
        if let layer = style.layer(withIdentifier: coordGridLayerId) { style.removeLayer(layer) }
        if let layer = style.layer(withIdentifier: coordGridZoneLayerId) { style.removeLayer(layer) }
        if let source = style.source(withIdentifier: coordGridSourceId) { style.removeSource(source) }
    }

    private func removeFriendTrackLine(style: MLNStyle) {
        if let layer = style.layer(withIdentifier: friendTrackLabelLayerId) { style.removeLayer(layer) }
        if let layer = style.layer(withIdentifier: friendTrackLineLayerId) { style.removeLayer(layer) }
        if let layer = style.layer(withIdentifier: friendTrackPointLayerId) { style.removeLayer(layer) }
        if let source = style.source(withIdentifier: friendTrackLineSourceId) { style.removeSource(source) }
        if let source = style.source(withIdentifier: friendTrackPointSourceId) { style.removeSource(source) }
    }
}

class TwoFingerTapGestureRecognizer: UIGestureRecognizer {
    private(set) var touch1: CGPoint = .zero
    private(set) var touch2: CGPoint = .zero
    private var initial1: CGPoint = .zero
    private var initial2: CGPoint = .zero
    private var touch1Ref: UITouch?
    private var touch2Ref: UITouch?
    private var touch1Ended: Bool = false
    private var touch2Ended: Bool = false
    private var startTime: TimeInterval = 0
    private let maxDuration: TimeInterval = 0.6
    private let maxMovement: CGFloat = 20

    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent) {
        super.touchesBegan(touches, with: event)
        let active = event.touches(for: self) ?? []
        if active.count > 2 {
            state = .failed
            return
        }
        if active.count == 2 {
            let arr = Array(active)
            touch1Ref = arr[0]
            touch2Ref = arr[1]
            initial1 = arr[0].location(in: view)
            initial2 = arr[1].location(in: view)
            touch1 = initial1
            touch2 = initial2
            startTime = event.timestamp
        }
    }

    override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent) {
        super.touchesMoved(touches, with: event)
        guard state != .failed && state != .cancelled else { return }
        guard let t1 = touch1Ref, let t2 = touch2Ref else { return }
        let p1 = t1.location(in: view)
        let p2 = t2.location(in: view)
        if hypot(p1.x - initial1.x, p1.y - initial1.y) > maxMovement ||
           hypot(p2.x - initial2.x, p2.y - initial2.y) > maxMovement {
            state = .failed
            return
        }
        touch1 = p1
        touch2 = p2
    }

    override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent) {
        super.touchesEnded(touches, with: event)
        if state == .failed || state == .cancelled { return }
        if event.timestamp - startTime > maxDuration {
            state = .failed
            return
        }
        // Fingers can lift in separate touchesEnded calls; latch a per-touch
        // ended flag and only recognize once both have lifted.
        if let t1 = touch1Ref, touches.contains(t1) {
            touch1Ended = true
        }
        if let t2 = touch2Ref, touches.contains(t2) {
            touch2Ended = true
        }
        if touch1Ended && touch2Ended {
            state = .recognized
        }
    }

    override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent) {
        super.touchesCancelled(touches, with: event)
        state = .cancelled
    }

    override func reset() {
        super.reset()
        touch1 = .zero
        touch2 = .zero
        initial1 = .zero
        initial2 = .zero
        touch1Ref = nil
        touch2Ref = nil
        touch1Ended = false
        touch2Ended = false
        startTime = 0
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
