import Foundation
import MapLibre
import Shared

class OfflineMapFactory: NSObject, OfflineMapManager {
    private var activeObservers: [String: OfflineMapDownloadObserver] = [:]
    private var activePacks: [String: MLNOfflinePack] = [:]
    private var invalidatedPacks = Set<ObjectIdentifier>()
    // Cache progress before suspension (pack.progress may reset after suspend)
    private var cachedStatus: [String: String] = [:]
    // Reload flag: set to true whenever the stored pack list changes
    private var packsNeedReload = true

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
        // Restore completion statuses persisted in a previous session so the UI
        // shows the correct state immediately, without waiting for requestProgress()
        // notifications which are unreliable for already-complete packs on restart.
        if let saved = UserDefaults.standard.dictionary(forKey: "offlineMapStatus") as? [String: String] {
            cachedStatus = saved
        }
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
        removePersistedStatus(forRegion: regionName)

        // Register style with HTTP server
        StyleServer.shared.setStyle(name: regionName, json: styleJson)

        let styleURL = StyleServer.shared.styleURL(for: regionName)

        // Delete any existing pack — stale packs from previous sessions may have
        // broken internal state (wrong tile URLs, exhausted retries). Creating a
        // fresh pack with the current style JSON is more reliable than resuming.
        if let existing = findPackSync(name: regionName) {
            NSLog("[OfflineMap] Removing stale pack for \(regionName) (state=\(existing.state.rawValue)) before creating fresh")
            invalidatedPacks.insert(ObjectIdentifier(existing))
            if existing.state == .active {
                existing.suspend()
            }
            MLNOfflineStorage.shared.removePack(existing) { [weak self] error in
                if let error = error {
                    NSLog("[OfflineMap] removePack error during refresh for \(regionName): \(error)")
                }
                // Create new pack after old one is removed
                self?.startPack(regionName: regionName, layerName: layerName, styleURL: styleURL,
                               south: south, west: west, north: north, east: east,
                               minZoom: minZoom, maxZoom: maxZoom, observer: observer)
            }
            return
        }

        NSLog("[OfflineMap] No existing pack for \(regionName), creating new")
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
        NSLog("[OfflineMap] Database path: \(MLNOfflineStorage.shared.databasePath)")
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
        }
    }

    func stopDownload(regionName: String) {
        if let pack = activePacks[regionName],
           !invalidatedPacks.contains(ObjectIdentifier(pack)) {
            if pack.state != .invalid {
                // Cache progress BEFORE suspending (suspend may reset progress)
                cachedStatus[regionName] = encodePackStatus(pack, name: regionName)
                pack.suspend()
                NSLog("[OfflineMap] Suspended pack for \(regionName), cached status: \(cachedStatus[regionName] ?? "")")
            }
        }
        activeObservers.removeValue(forKey: regionName)
    }

    func resumeDownload(regionName: String) {
        guard let pack = activePacks[regionName] else {
            NSLog("[OfflineMap] Auto-resume: no pack in activePacks for \(regionName)")
            return
        }

        let packId = ObjectIdentifier(pack)
        NSLog("[OfflineMap] Auto-resume for \(regionName): state=\(pack.state.rawValue), invalidated=\(invalidatedPacks.contains(packId))")

        guard !invalidatedPacks.contains(packId), pack.state != .invalid else {
            NSLog("[OfflineMap] Auto-resume: pack is invalid, cleaning up \(regionName)")
            activePacks.removeValue(forKey: regionName)
            return
        }

        // Suspend then resume after a pause to kick MapLibre's download engine
        pack.suspend()

        DispatchQueue.main.asyncAfter(deadline: .now() + 2.0) {
            guard pack.state != .invalid else {
                NSLog("[OfflineMap] Auto-resume: pack became invalid before resume for \(regionName)")
                return
            }
            pack.resume()
            NSLog("[OfflineMap] Auto-resume: resumed pack for \(regionName) (state=\(pack.state.rawValue))")
        }
    }

    func getRegionStatusEncoded(regionName: String) -> String {
        // Active download: trust the pack reference directly — never fall through
        // to findPackSync, because reloadPacks() invalidates live pack objects.
        if let pack = activePacks[regionName],
           !invalidatedPacks.contains(ObjectIdentifier(pack)),
           pack.state != .invalid {
            let status = encodePackStatus(pack, name: regionName)
            if !status.hasPrefix("0,0,") {
                cachedStatus[regionName] = status
            }
            return cachedStatus[regionName] ?? status
        }

        // No active download — safe to check storage for completed/historical packs
        let storedPack = findPackSync(name: regionName)
        if let pack = storedPack {
            let status = encodePackStatus(pack, name: regionName)
            if !status.hasPrefix("0,0,") {
                cachedStatus[regionName] = status
                return status
            }
            // Progress is 0 (stale); use cached data if available.
            // ensurePacksLoaded() (called via findPackSync) already called
            // requestProgress() on this pack, so the notification will fire shortly.
            if let cached = cachedStatus[regionName] {
                return cached
            }
            // pack.state == .unknown means reloadPacks() ran but requestProgress() hasn't
            // fired its notification yet. Return the retry sentinel so the Kotlin caller
            // waits and retries — the notification will populate cachedStatus shortly.
            if pack.state == .unknown {
                return "-1,-1,-1,-1"
            }
            // State is now known from the DB (inactive, complete, etc.).
            return "0,0,0,\(pack.state == .complete)"
        }
        if let cached = cachedStatus[regionName] {
            return cached
        }
        return ""
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
        // Remove from active tracking and suspend to stop notifications
        if let pack = activePacks.removeValue(forKey: regionName) {
            invalidatedPacks.insert(ObjectIdentifier(pack))
            if pack.state == .active {
                pack.suspend()
            }
        }
        activeObservers.removeValue(forKey: regionName)
        removePersistedStatus(forRegion: regionName)

        guard let pack = findPackSync(name: regionName) else { return false }
        invalidatedPacks.insert(ObjectIdentifier(pack))
        if pack.state == .active {
            pack.suspend()
        }
        MLNOfflineStorage.shared.removePack(pack) { [weak self] error in
            if let error = error {
                NSLog("[OfflineMap] removePack error for \(regionName): \(error)")
            }
            self?.packsNeedReload = true
        }
        return true
    }

    func getLayerStatsEncoded(layerName: String) -> String {
        var totalSize: Int64 = 0
        var totalTiles: Int32 = 0
        var counted = Set<String>()
        var requestedProgress = false

        // Include active in-memory packs
        for (name, pack) in activePacks where !invalidatedPacks.contains(ObjectIdentifier(pack)) && pack.state != .invalid {
            if let metadata = decodeMetadata(data: pack.context),
               metadata["layer"] == layerName {
                let progress = pack.progress
                totalSize += Int64(progress.countOfBytesCompleted)
                totalTiles += Int32(progress.countOfResourcesCompleted)
                counted.insert(name)
            }
        }

        // ensurePacksLoaded() reloads once (if dirty) and calls requestProgress()
        // on every pack so cachedStatus gets populated via notifications.
        ensurePacksLoaded()
        if let packs = MLNOfflineStorage.shared.packs {
            for pack in packs {
                guard !invalidatedPacks.contains(ObjectIdentifier(pack)) else { continue }
                guard let metadata = decodeMetadata(data: pack.context),
                      metadata["layer"] == layerName,
                      let name = metadata["name"],
                      !counted.contains(name) else { continue }

                let resources = Int32(pack.progress.countOfResourcesCompleted)
                if resources > 0 {
                    totalSize += Int64(pack.progress.countOfBytesCompleted)
                    totalTiles += resources
                } else if let cached = cachedStatus[name] {
                    // Use values populated by the requestProgress() notification
                    let parts = cached.split(separator: ",")
                    if parts.count >= 3 {
                        totalTiles += Int32(String(parts[0])) ?? 0
                        totalSize += Int64(String(parts[2])) ?? 0
                    }
                } else {
                    // Notification hasn't fired yet; tell the caller to retry
                    requestedProgress = true
                }
            }
        }

        // Tell the caller to retry after the requestProgress notifications fire
        if requestedProgress { return "-1,-1" }
        return "\(totalSize),\(totalTiles)"
    }

    func getRegionNamesForLayer(layerName: String) -> String {
        var names: [String] = []
        var counted = Set<String>()

        // Include active in-memory packs
        for (name, pack) in activePacks where !invalidatedPacks.contains(ObjectIdentifier(pack)) && pack.state != .invalid {
            if let metadata = decodeMetadata(data: pack.context),
               metadata["layer"] == layerName {
                names.append(name)
                counted.insert(name)
            }
        }

        ensurePacksLoaded()
        if let packs = MLNOfflineStorage.shared.packs {
            for pack in packs {
                guard !invalidatedPacks.contains(ObjectIdentifier(pack)) else { continue }
                guard let metadata = decodeMetadata(data: pack.context),
                      metadata["layer"] == layerName,
                      let name = metadata["name"],
                      !counted.contains(name) else { continue }
                names.append(name)
            }
        }

        let json = try? JSONSerialization.data(withJSONObject: names)
        return json.flatMap { String(data: $0, encoding: .utf8) } ?? "[]"
    }

    func getDatabaseSize() -> Int64 {
        let path = MLNOfflineStorage.shared.databasePath
        guard let attrs = try? FileManager.default.attributesOfItem(atPath: path),
              let size = attrs[.size] as? Int64 else { return 0 }
        return size
    }

    func clearAmbientCache(callback: any ClearCacheCallback) {
        MLNOfflineStorage.shared.clearAmbientCache { [weak self] error in
            if let error = error {
                NSLog("[OfflineMap] clearAmbientCache error: \(error)")
            }
            self?.packsNeedReload = true
            callback.onComplete()
        }
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
            persistCompletionStatus(encodePackStatus(pack, name: name), forRegion: name)
            observer.onProgress(percent: 100)
            observer.onComplete(success: true)
            activePacks.removeValue(forKey: name)
            activeObservers.removeValue(forKey: name)
            packsNeedReload = true
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
            packsNeedReload = true
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
        packsNeedReload = true
    }

    // MARK: - Helpers

    /// Reloads packs from the database at most once per dirty window, then eagerly
    /// calls requestProgress() on every pack so the MLNOfflinePackProgressChanged
    /// notification fires and populates cachedStatus for all regions in one shot.
    /// Repeated calls within the same window are no-ops, which prevents the
    /// "15 reloadPacks() calls invalidating each other's pack objects" race.
    private func ensurePacksLoaded() {
        guard packsNeedReload, activePacks.isEmpty else { return }
        MLNOfflineStorage.shared.reloadPacks()
        packsNeedReload = false
        MLNOfflineStorage.shared.packs?.forEach { pack in
            guard !invalidatedPacks.contains(ObjectIdentifier(pack)) else { return }
            pack.requestProgress()
        }
    }

    private func findPackSync(name: String) -> MLNOfflinePack? {
        // reloadPacks() invalidates ALL existing MLNOfflinePack Swift objects,
        // killing active downloads. Only reload when no downloads are running
        // (ensurePacksLoaded handles the guard).
        ensurePacksLoaded()
        guard let packs = MLNOfflineStorage.shared.packs else { return nil }
        return packs.first { pack in
            guard !self.invalidatedPacks.contains(ObjectIdentifier(pack)) else { return false }
            if let metadata = self.decodeMetadata(data: pack.context) {
                return metadata["name"] == name
            }
            return false
        }
    }

    private func persistCompletionStatus(_ status: String, forRegion name: String) {
        cachedStatus[name] = status
        var saved = (UserDefaults.standard.dictionary(forKey: "offlineMapStatus") as? [String: String]) ?? [:]
        saved[name] = status
        UserDefaults.standard.set(saved, forKey: "offlineMapStatus")
    }

    private func removePersistedStatus(forRegion name: String) {
        cachedStatus.removeValue(forKey: name)
        var saved = (UserDefaults.standard.dictionary(forKey: "offlineMapStatus") as? [String: String]) ?? [:]
        saved.removeValue(forKey: name)
        UserDefaults.standard.set(saved, forKey: "offlineMapStatus")
    }

    private func encodeMetadata(name: String, layer: String) -> Data {
        let dict: [String: String] = ["name": name, "layer": layer]
        return (try? JSONSerialization.data(withJSONObject: dict)) ?? Data()
    }

    private func decodeMetadata(data: Data) -> [String: String]? {
        return try? JSONSerialization.jsonObject(with: data) as? [String: String]
    }
}
