import Foundation
import MapLibre
import Shared

class OfflineMapFactory: NSObject, OfflineMapManager {
    private var activeObservers: [String: OfflineMapDownloadObserver] = [:]
    private var activePacks: [String: MLNOfflinePack] = [:]
    private var invalidatedPacks = Set<ObjectIdentifier>()
    // Cache progress before suspension (pack.progress may reset after suspend)
    private var cachedStatus: [String: String] = [:]

    override init() {
        super.init()
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(offlinePackProgressChanged(_:)),
            name: NSNotification.Name.MLNOfflinePackProgressChanged,
            object: nil
        )
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(offlinePackDidReceiveError(_:)),
            name: NSNotification.Name.MLNOfflinePackError,
            object: nil
        )
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(offlinePackDidReceiveMaxTiles(_:)),
            name: NSNotification.Name.MLNOfflinePackMaximumMapboxTilesReached,
            object: nil
        )
    }

    deinit {
        NotificationCenter.default.removeObserver(self)
    }

    func downloadRegion(
        regionName: String, layerName: String, styleJson: String,
        south: Double, west: Double, north: Double, east: Double,
        minZoom: Int32, maxZoom: Int32, observer: any OfflineMapDownloadObserver
    ) {
        // Clean up stale in-memory references
        activePacks.removeValue(forKey: regionName)
        activeObservers.removeValue(forKey: regionName)
        cachedStatus.removeValue(forKey: regionName)

        // Register style with HTTP server (needed for both new and resumed packs)
        StyleServer.shared.setStyle(name: regionName, json: styleJson)

        // Check if pack already exists in storage — resume instead of remove+add
        if let existing = findPackSync(name: regionName) {
            NSLog("[OfflineMap] Resuming existing pack for \(regionName) (state=\(existing.state.rawValue))")
            activeObservers[regionName] = observer
            activePacks[regionName] = existing
            existing.resume()
            return
        }

        // Create new pack with HTTP style URL
        let styleURL = StyleServer.shared.styleURL(for: regionName)
        NSLog("[OfflineMap] Style URL: \(styleURL)")
        NSLog("[OfflineMap] Style JSON (\(styleJson.count) chars): \(styleJson.prefix(500))")

        startPack(regionName: regionName, layerName: layerName, styleURL: styleURL,
                  south: south, west: west, north: north, east: east,
                  minZoom: minZoom, maxZoom: maxZoom, observer: observer)
    }

    private func startPack(regionName: String, layerName: String, styleURL: URL,
                           south: Double, west: Double, north: Double, east: Double,
                           minZoom: Int32, maxZoom: Int32, observer: any OfflineMapDownloadObserver) {
        let sw = CLLocationCoordinate2D(latitude: south, longitude: west)
        let ne = CLLocationCoordinate2D(latitude: north, longitude: east)
        let bounds = MLNCoordinateBounds(sw: sw, ne: ne)

        let region = MLNTilePyramidOfflineRegion(
            styleURL: styleURL,
            bounds: bounds,
            fromZoomLevel: Double(minZoom),
            toZoomLevel: Double(maxZoom)
        )

        let metadata = encodeMetadata(name: regionName, layer: layerName)

        NSLog("[OfflineMap] Creating new pack for \(regionName), bounds=(\(south),\(west))-(\(north),\(east)), zoom=\(minZoom)-\(maxZoom)")
        NSLog("[OfflineMap] styleURL=\(styleURL)")
        NSLog("[OfflineMap] Database path: \(MLNOfflineStorage.shared.databasePath ?? "nil")")
        NSLog("[OfflineMap] Existing packs count: \(MLNOfflineStorage.shared.packs?.count ?? -1)")

        MLNOfflineStorage.shared.addPack(for: region, withContext: metadata) { [weak self] pack, error in
            guard let self = self else { return }
            if let error = error {
                NSLog("[OfflineMap] addPack error for \(regionName): \(error)")
                observer.onError(message: error.localizedDescription)
                return
            }
            guard let pack = pack else {
                NSLog("[OfflineMap] addPack returned nil for \(regionName)")
                observer.onError(message: "Pack creation returned nil")
                return
            }
            NSLog("[OfflineMap] Pack created for \(regionName), state=\(pack.state.rawValue), resuming")
            self.activeObservers[regionName] = observer
            self.activePacks[regionName] = pack
            pack.resume()
            NSLog("[OfflineMap] After resume: state=\(pack.state.rawValue)")

            // Delayed state check for debugging
            DispatchQueue.main.asyncAfter(deadline: .now() + 3.0) {
                let p = pack.progress
                NSLog("[OfflineMap] 3s check for \(regionName): state=\(pack.state.rawValue), completed=\(p.countOfResourcesCompleted), expected=\(p.countOfResourcesExpected), bytes=\(p.countOfBytesCompleted)")
            }
        }
    }

    func stopDownload(regionName: String) {
        if let pack = activePacks[regionName] {
            if pack.state != .invalid {
                // Cache progress BEFORE suspending (suspend may reset progress)
                cachedStatus[regionName] = encodePackStatus(pack, name: regionName)
                pack.suspend()
                NSLog("[OfflineMap] Suspended pack for \(regionName), cached status: \(cachedStatus[regionName] ?? "")")
            }
        }
        activeObservers.removeValue(forKey: regionName)
    }

    func getRegionStatusEncoded(regionName: String) -> String {
        // Check active in-memory pack first (has current download progress)
        if let pack = activePacks[regionName], pack.state != .invalid {
            let status = encodePackStatus(pack, name: regionName)
            if !status.hasPrefix("0,0,") {
                cachedStatus[regionName] = status
                return status
            }
        }
        // Fall back to storage for completed/historical packs
        let storedPack = findPackSync(name: regionName)
        if let pack = storedPack {
            let status = encodePackStatus(pack, name: regionName)
            if !status.hasPrefix("0,0,") {
                cachedStatus[regionName] = status
                return status
            }
        }
        // Use cached status (best progress seen so far)
        if let cached = cachedStatus[regionName] {
            return cached
        }
        return storedPack != nil ? "0,0,0,false" : ""
    }

    private func encodePackStatus(_ pack: MLNOfflinePack, name: String) -> String {
        let progress = pack.progress
        let downloaded = Int(progress.countOfResourcesCompleted)
        let expected = progress.countOfResourcesExpected
        let total = (expected == UInt64.max) ? downloaded : Int(expected)
        let size = Int64(progress.countOfBytesCompleted)
        let isComplete = pack.state == .complete
        let result = "\(downloaded),\(total),\(size),\(isComplete)"
        NSLog("[OfflineMap] packStatus(\(name)): \(result) (state=\(pack.state.rawValue))")
        return result
    }

    func deleteRegionSync(regionName: String) -> Bool {
        // Remove from active tracking
        if let pack = activePacks.removeValue(forKey: regionName) {
            invalidatedPacks.insert(ObjectIdentifier(pack))
        }
        activeObservers.removeValue(forKey: regionName)
        cachedStatus.removeValue(forKey: regionName)

        guard let pack = findPackSync(name: regionName) else { return false }
        invalidatedPacks.insert(ObjectIdentifier(pack))
        MLNOfflineStorage.shared.removePack(pack) { error in
            if let error = error {
                NSLog("[OfflineMap] removePack error for \(regionName): \(error)")
            }
        }
        return true
    }

    func getLayerStatsEncoded(layerName: String) -> String {
        var totalSize: Int64 = 0
        var totalTiles: Int32 = 0
        var counted = Set<String>()

        // Include active in-memory packs
        for (name, pack) in activePacks where pack.state != .invalid {
            if let metadata = decodeMetadata(data: pack.context),
               metadata["layer"] == layerName {
                let progress = pack.progress
                totalSize += Int64(progress.countOfBytesCompleted)
                totalTiles += Int32(progress.countOfResourcesCompleted)
                counted.insert(name)
            }
        }

        // Include stored packs not already counted
        MLNOfflineStorage.shared.reloadPacks()
        if let packs = MLNOfflineStorage.shared.packs {
            for pack in packs {
                guard !invalidatedPacks.contains(ObjectIdentifier(pack)) else { continue }
                if let metadata = decodeMetadata(data: pack.context),
                   metadata["layer"] == layerName,
                   let name = metadata["name"],
                   !counted.contains(name) {
                    let progress = pack.progress
                    totalSize += Int64(progress.countOfBytesCompleted)
                    totalTiles += Int32(progress.countOfResourcesCompleted)
                }
            }
        }

        return "\(totalSize),\(totalTiles)"
    }

    // MARK: - Notification handlers

    @objc private func offlinePackProgressChanged(_ notification: Notification) {
        guard let pack = notification.object as? MLNOfflinePack else {
            NSLog("[OfflineMap] Progress notification: object is not MLNOfflinePack")
            return
        }
        guard !invalidatedPacks.contains(ObjectIdentifier(pack)) else { return }

        guard let metadata = decodeMetadata(data: pack.context),
              let name = metadata["name"] else {
            NSLog("[OfflineMap] Progress notification: could not decode metadata")
            return
        }

        let expected = pack.progress.countOfResourcesExpected
        let completed = pack.progress.countOfResourcesCompleted
        NSLog("[OfflineMap] Progress for \(name): \(completed)/\(expected) state=\(pack.state.rawValue)")

        // Cache non-zero progress so it survives suspension/state changes
        if completed > 0 {
            cachedStatus[name] = encodePackStatus(pack, name: name)
        }

        guard let observer = activeObservers[name] else { return }

        if pack.state == .complete {
            NSLog("[OfflineMap] Pack complete: \(name)")
            cachedStatus[name] = encodePackStatus(pack, name: name)
            observer.onProgress(percent: 100)
            observer.onComplete(success: true)
            activePacks.removeValue(forKey: name)
            activeObservers.removeValue(forKey: name)
            return
        }

        if expected > 0 && expected != UInt64.max {
            let percent = Int32(completed * 100 / expected)
            observer.onProgress(percent: percent)
        }
    }

    @objc private func offlinePackDidReceiveError(_ notification: Notification) {
        guard let pack = notification.object as? MLNOfflinePack else {
            NSLog("[OfflineMap] Error notification: object is not MLNOfflinePack")
            return
        }

        let errorInfo = notification.userInfo?[MLNOfflinePackUserInfoKey.error] as? NSError
        let message = errorInfo?.localizedDescription ?? "Unknown error"

        guard !invalidatedPacks.contains(ObjectIdentifier(pack)) else {
            NSLog("[OfflineMap] Error for invalidated pack: \(message)")
            return
        }

        guard let metadata = decodeMetadata(data: pack.context),
              let name = metadata["name"] else {
            NSLog("[OfflineMap] Error notification (no metadata): \(message)")
            return
        }

        NSLog("[OfflineMap] Pack error for \(name): \(message) (domain=\(errorInfo?.domain ?? "nil"), code=\(errorInfo?.code ?? 0))")

        let isTransient = errorInfo?.domain == NSURLErrorDomain
        if !isTransient {
            activeObservers[name]?.onError(message: message)
            activePacks.removeValue(forKey: name)
            activeObservers.removeValue(forKey: name)
        }
    }

    @objc private func offlinePackDidReceiveMaxTiles(_ notification: Notification) {
        guard let pack = notification.object as? MLNOfflinePack else { return }
        guard !invalidatedPacks.contains(ObjectIdentifier(pack)) else { return }
        guard let metadata = decodeMetadata(data: pack.context),
              let name = metadata["name"] else { return }
        NSLog("[OfflineMap] Max tiles reached for \(name)")

        activeObservers[name]?.onComplete(success: true)
        activePacks.removeValue(forKey: name)
        activeObservers.removeValue(forKey: name)
    }

    // MARK: - Helpers

    private func findPackSync(name: String) -> MLNOfflinePack? {
        MLNOfflineStorage.shared.reloadPacks()
        guard let packs = MLNOfflineStorage.shared.packs else { return nil }
        return packs.first { pack in
            guard !self.invalidatedPacks.contains(ObjectIdentifier(pack)) else { return false }
            if let metadata = self.decodeMetadata(data: pack.context) {
                return metadata["name"] == name
            }
            return false
        }
    }

    private func encodeMetadata(name: String, layer: String) -> Data {
        let dict: [String: String] = ["name": name, "layer": layer]
        return (try? JSONSerialization.data(withJSONObject: dict)) ?? Data()
    }

    private func decodeMetadata(data: Data) -> [String: String]? {
        return try? JSONSerialization.jsonObject(with: data) as? [String: String]
    }
}
