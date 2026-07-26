package io.github.vrcmteam.vrcm.presentation.screens.gallery.editor

object DecodeSizePlanner {
    fun plan(source: ImageSize, request: DecodeRequest): ImageSize {
        require(source.width > 0) { "Source width must be positive" }
        require(source.height > 0) { "Source height must be positive" }
        require(request.maxDimension > 0) { "Maximum dimension must be positive" }
        require(request.maxPixels > 0) { "Maximum pixel count must be positive" }

        val sourceLongest = maxOf(source.width, source.height)
        var low = 1L
        var high = minOf(sourceLongest, request.maxDimension).toLong()
        var best = 1L

        while (low <= high) {
            val candidate = low + (high - low) / 2
            val size = sizeForLongestEdge(source, candidate)
            if (size.width.toLong() * size.height <= request.maxPixels) {
                best = candidate
                low = candidate + 1
            } else {
                high = candidate - 1
            }
        }

        return sizeForLongestEdge(source, best)
    }

    private fun sizeForLongestEdge(source: ImageSize, longestEdge: Long): ImageSize =
        if (source.width >= source.height) {
            ImageSize(
                width = longestEdge.toInt(),
                height = (longestEdge * source.height / source.width).toInt().coerceAtLeast(1),
            )
        } else {
            ImageSize(
                width = (longestEdge * source.width / source.height).toInt().coerceAtLeast(1),
                height = longestEdge.toInt(),
            )
        }
}
