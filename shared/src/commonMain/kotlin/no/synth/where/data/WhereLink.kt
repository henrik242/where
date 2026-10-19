package no.synth.where.data

private val WHERE_GROUP_LINK = Regex("^https?://where\\.synth\\.no/([a-z0-9]{6}(?:,[a-z0-9]{6})*)/?(?:[?#].*)?$")

/**
 * Client IDs in a where.synth.no/<id> or /<id>,<id>,... link (the same group link the web viewer
 * takes), or null if [url] is not one.
 */
fun parseWhereGroupLink(url: String): List<String>? =
    WHERE_GROUP_LINK.find(url)?.groupValues?.get(1)?.split(",")
