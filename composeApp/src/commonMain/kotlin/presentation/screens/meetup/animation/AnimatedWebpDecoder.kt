package io.github.vrcmteam.vrcm.presentation.screens.meetup.animation

import androidx.compose.ui.graphics.ImageBitmap

interface AnimatedWebpDecoder {
    fun decode(bytes: ByteArray): DecodedAnimation
}

/** A lifecycle-aware animated WebP stream whose emitted frames are caller-owned. */
interface DecodedAnimation : AutoCloseable {
    val frameCount: Int

    fun durationMillis(index: Int): Int

    fun start(onFrame: (OwnedAnimationFrame) -> Unit) = start(onFrame) {}

    fun start(
        onFrame: (OwnedAnimationFrame) -> Unit,
        onError: (Throwable) -> Unit,
    )

    fun pause()

    fun resume()
}

interface OwnedAnimationFrame : AutoCloseable {
    val bitmap: ImageBitmap
}
