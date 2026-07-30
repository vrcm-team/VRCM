package io.github.vrcmteam.vrcm.presentation.screens.gallery.editor

import androidx.compose.ui.graphics.ImageBitmap

internal class PreviewBitmapReleaseController(
    private val bitmap: ImageBitmap,
    private val releaseBitmap: (ImageBitmap) -> Unit,
) {
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
        releaseBitmap(bitmap)
    }
}
