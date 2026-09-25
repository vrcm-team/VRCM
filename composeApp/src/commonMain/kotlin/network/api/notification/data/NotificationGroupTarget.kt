package io.github.vrcmteam.vrcm.network.api.notification.data

/** Resolves the first valid VRChat group ID from structured fields or a notification link. */
internal fun resolveNotificationGroupId(
    link: String?,
    vararg candidates: String?,
): String? = (candidates.asSequence() + sequenceOf(link))
    .mapNotNull(::extractGroupId)
    .firstOrNull()

private fun extractGroupId(value: String?): String? {
    val text = value?.trim().orEmpty()
    val markerIndex = text.indexOf("grp_")
    if (markerIndex < 0) return null
    return text.substring(markerIndex)
        .takeWhile { char -> char.isLetterOrDigit() || char == '-' || char == '_' }
        .takeIf { it.length > "grp_".length }
}
