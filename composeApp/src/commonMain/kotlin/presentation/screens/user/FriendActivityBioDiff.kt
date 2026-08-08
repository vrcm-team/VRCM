package io.github.vrcmteam.vrcm.presentation.screens.user

internal data class FriendActivityBioDiffLine(
    val added: Boolean,
    val text: String,
    val unchanged: Boolean = false,
)

/**
 * Produces a compact, line-oriented diff for profile bios. Unchanged lines
 * are omitted so that an activity row shows only what actually changed.
 */
internal fun friendActivityBioDiff(
    previous: String?,
    current: String?,
    includeUnchanged: Boolean = false,
): List<FriendActivityBioDiffLine> {
    val oldLines = previous.orEmpty().lines()
    val newLines = current.orEmpty().lines()
    if (oldLines == newLines) return emptyList()

    val commonSuffixLengths = Array(oldLines.size + 1) { IntArray(newLines.size + 1) }
    for (oldIndex in oldLines.indices.reversed()) {
        for (newIndex in newLines.indices.reversed()) {
            commonSuffixLengths[oldIndex][newIndex] =
                if (oldLines[oldIndex] == newLines[newIndex]) {
                    commonSuffixLengths[oldIndex + 1][newIndex + 1] + 1
                } else {
                    maxOf(
                        commonSuffixLengths[oldIndex + 1][newIndex],
                        commonSuffixLengths[oldIndex][newIndex + 1],
                    )
                }
        }
    }

    val result = mutableListOf<FriendActivityBioDiffLine>()
    var oldIndex = 0
    var newIndex = 0
    while (oldIndex < oldLines.size || newIndex < newLines.size) {
        when {
            oldIndex < oldLines.size &&
                newIndex < newLines.size &&
                oldLines[oldIndex] == newLines[newIndex] -> {
                if (includeUnchanged) {
                    result += FriendActivityBioDiffLine(
                        added = false,
                        text = oldLines[oldIndex],
                        unchanged = true,
                    )
                }
                oldIndex++
                newIndex++
            }
            newIndex < newLines.size &&
                (oldIndex == oldLines.size ||
                    commonSuffixLengths[oldIndex][newIndex + 1] >
                    commonSuffixLengths[oldIndex + 1][newIndex]) -> {
                result += FriendActivityBioDiffLine(added = true, text = newLines[newIndex++])
            }
            else -> result += FriendActivityBioDiffLine(added = false, text = oldLines[oldIndex++])
        }
    }
    return result.take(if (includeUnchanged) MAX_BIO_PREVIEW_LINES else MAX_BIO_DIFF_LINES)
}

private const val MAX_BIO_DIFF_LINES = 200
private const val MAX_BIO_PREVIEW_LINES = 300
