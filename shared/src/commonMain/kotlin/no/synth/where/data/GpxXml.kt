package no.synth.where.data

import kotlin.time.Instant

/**
 * Minimal GPX scanning shared by the track and waypoint parsers: a linear walk over the elements of
 * one name, plus case-insensitive attribute and child lookups within a single element.
 */

/**
 * Calls [block] with each `<tagName …>` element in [content], original case, body included.
 * [lower] must be [content] lowercased; callers keep one copy across several passes.
 */
internal fun forEachGpxElement(
    content: String,
    lower: String,
    tagName: String,
    block: (String) -> Unit
) {
    val name = tagName.lowercase()
    val openMarker = "<$name"
    val closeMarker = "</$name>"
    var searchFrom = 0
    while (true) {
        val start = indexOfOpenTag(lower, openMarker, searchFrom)
        if (start < 0) break
        val openEnd = lower.indexOf('>', start)
        if (openEnd < 0) break

        val end = if (lower[openEnd - 1] == '/') {
            openEnd + 1
        } else {
            // The element ends at its own close tag, but the search for it is bounded by the next
            // element of the same name (or end of document): an unclosed tag would otherwise send
            // every scan toward end-of-document, making the parse O(n^2).
            val next = indexOfOpenTag(lower, openMarker, openEnd).takeIf { it >= 0 } ?: content.length
            val close = lower.substring(openEnd, next).indexOf(closeMarker)
            if (close >= 0) openEnd + close + closeMarker.length else next
        }

        searchFrom = end
        block(content.substring(start, end))
    }
}

/**
 * Index of the next `<name` opening tag at or after [from], or -1. The name must end there, so
 * `<wpt` doesn't match `<wptx1:WaypointExtension`, and any XML whitespace may follow it.
 */
private fun indexOfOpenTag(lower: String, openMarker: String, from: Int): Int {
    var at = from
    while (true) {
        val start = lower.indexOf(openMarker, at)
        if (start < 0) return -1
        val after = start + openMarker.length
        if (after >= lower.length) return -1
        val next = lower[after]
        if (next.isWhitespace() || next == '>' || next == '/') return start
        at = after
    }
}

/** Value of the opening tag's [name] attribute (pass it lowercase), or null. */
internal fun String.gpxAttr(name: String): String? {
    val tagEnd = indexOf('>').let { if (it < 0) length else it }
    val lower = lowercase()
    for (quote in charArrayOf('"', '\'')) {
        val marker = "$name=$quote"
        val at = lower.indexOf(marker)
        if (at < 0 || at > tagEnd) continue
        val valueStart = at + marker.length
        val valueEnd = indexOf(quote, valueStart)
        if (valueEnd < 0) continue
        return substring(valueStart, valueEnd)
    }
    return null
}

/** Text of the first `<tag>…</tag>` child (pass [tag] lowercase), still XML-escaped, or null. */
internal fun String.gpxChild(tag: String): String? {
    val lower = lowercase()
    val open = "<$tag>"
    val start = lower.indexOf(open)
    if (start < 0) return null
    val end = lower.indexOf("</$tag>", start + open.length)
    if (end < 0) return null
    return substring(start + open.length, end)
}

/** This string with its first `<tag>…</tag>` child removed. */
private fun String.withoutGpxChild(tag: String): String {
    val lower = lowercase()
    val start = lower.indexOf("<$tag>")
    if (start < 0) return this
    val end = lower.indexOf("</$tag>", start)
    if (end < 0) return this
    return substring(0, start) + substring(end + tag.length + 3)
}

/** The element's `<time>` as epoch millis, or null when absent or unparseable. */
internal fun String.gpxTimeMillis(): Long? =
    gpxChild("time")?.trim()?.let { runCatching { Instant.parse(it).toEpochMilliseconds() }.getOrNull() }

/** Document name: the first non-empty `<name>` inside `<metadata>`, `<trk>` or `<rte>`. */
internal fun gpxDocumentName(content: String, lower: String): String? {
    for (parent in listOf("<metadata>", "<trk>", "<rte>")) {
        val parentStart = lower.indexOf(parent)
        if (parentStart < 0) continue
        val parentClose = lower.indexOf(parent.replace("<", "</"), parentStart)
        val section = if (parentClose >= 0) content.substring(parentStart, parentClose) else content.substring(parentStart)
        // <author> is the one child that nests a <name> of its own; left in, an author-only
        // <metadata> would name the document after whoever wrote it.
        val name = section.withoutGpxChild("author").gpxChild("name")?.trim()?.unescapeXml()
        if (!name.isNullOrEmpty()) return name
    }
    return null
}

/** [data] decoded as GPX text, or null when it is empty or holds no `<gpx` element. */
internal fun gpxTextOrNull(data: ByteArray): String? {
    if (data.isEmpty()) return null
    val text = data.decodeToString()
    return if (text.contains("<gpx", ignoreCase = true)) text else null
}

internal fun String.escapeXml(): String = this
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")
    .replace("'", "&apos;")

internal fun String.unescapeXml(): String = this
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .replace("&quot;", "\"")
    .replace("&apos;", "'")
    .replace("&amp;", "&")
