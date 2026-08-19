package io.github.vrcmteam.vrcm.presentation.screens.gallery.editor

import androidx.compose.ui.graphics.ImageBitmap

internal class PreviewBitmapReleaseController(
    bitmaps: List<ImageBitmap>,
    private val releaseBitmap: (ImageBitmap) -> Unit,
) {
    private val bitmaps = buildList {
        bitmaps.forEach { bitmap ->
            if (none { it === bitmap }) add(bitmap)
        }
    }
    private var displayLeaseCount = 0
    private var disposalRequested = false
    private var released = false

    fun acquireDisplayLease() {
        check(!released) { "Preview bitmap is already released" }
        displayLeaseCount++
    }

    fun releaseDisplayLease() {
        check(displayLeaseCount > 0) { "Preview display lease is not held" }
        displayLeaseCount--
        releaseIfUnused()
    }

    fun dispose() {
        if (disposalRequested) return
        disposalRequested = true
        releaseIfUnused()
    }

    private fun releaseIfUnused() {
        if (!disposalRequested || displayLeaseCount != 0 || released) return
        released = true
        var releaseFailure: Throwable? = null
        bitmaps.forEach { bitmap ->
            try {
                releaseBitmap(bitmap)
            } catch (cause: Throwable) {
                val primaryFailure = releaseFailure
                if (primaryFailure == null) {
                    releaseFailure = cause
                } else if (cause !== primaryFailure) {
                    primaryFailure.addSuppressed(cause)
                }
            }
        }
        releaseFailure?.let { throw it }
    }
}
