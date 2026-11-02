package no.synth.where.ui.map

/**
 * Map layer options.
 */
/** Base map layers; [downloadId] is the layer's id in [no.synth.where.data.DownloadLayers]. */
enum class MapLayer(val downloadId: String) {
    OSM("osm"),
    OPENTOPOMAP("opentopomap"),
    KARTVERKET("kartverket"),
    TOPORASTER("toporaster"),
    SJOKARTRASTER("sjokartraster"),
    MAPANT("mapant"),
    SATELLITE("satellite"),
}

/**
 * NVE slope-steepness overlays. Both paint the same steepness colours and [STEEPNESS_RUNOUT] adds
 * avalanche runout zones on top, so at most one is shown at a time; null means neither.
 */
enum class NveOverlay(val sourceId: String, service: String) {
    STEEPNESS("steepness", "Bratthet_2024"),

    // Source id stays the legacy "avalanchezones" so offline downloads keep resolving.
    STEEPNESS_RUNOUT("avalanchezones", "Bratthet_med_utlop_2024");

    // NVE deletes old vintages rather than keeping them (Bratthet_2023 is already gone), so bump
    // the year above when a new one is published.
    val tileUrl = "https://gis3.nve.no/arcgis/rest/services/wmts/$service/MapServer/tile/{z}/{y}/{x}"

    companion object {
        const val MIN_ZOOM = 6

        /** NVE's tile cache stops here; MapLibre overzooms past it instead of fetching 404s. */
        const val MAX_ZOOM = 16
    }
}
