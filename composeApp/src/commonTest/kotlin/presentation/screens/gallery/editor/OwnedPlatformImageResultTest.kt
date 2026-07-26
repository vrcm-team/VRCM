package io.github.vrcmteam.vrcm.presentation.screens.gallery.editor

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.colorspace.ColorSpace
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import kotlinx.coroutines.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class OwnedPlatformImageResultTest {
    @Test
    fun promptCancellationReleasesImageBitmapResult() {
        val workerDispatcher = QueuedTestDispatcher()
        val cancellation = CancellationException("cancelled")
        val released = mutableListOf<ImageBitmap>()

        val result = cancelAfterWorkerCompletion(workerDispatcher, cancellation) {
            withOwnedPlatformImageResult(
                dispatcher = workerDispatcher,
                ownedBitmap = { it },
                releaseBitmap = released::add,
            ) {
                OwnedResultTestBitmap
            }
        }

        assertIs<CancellationException>(result.exceptionOrNull())
        assertEquals(listOf<ImageBitmap>(OwnedResultTestBitmap), released)
    }

    @Test
    fun promptCancellationReleasesDecodedImageBitmap() {
        val workerDispatcher = QueuedTestDispatcher()
        val cancellation = CancellationException("cancelled")
        val released = mutableListOf<ImageBitmap>()

        val result = cancelAfterWorkerCompletion(workerDispatcher, cancellation) {
            withOwnedPlatformImageResult(
                dispatcher = workerDispatcher,
                ownedBitmap = DecodedImage::bitmap,
                releaseBitmap = released::add,
            ) {
                DecodedImage(OwnedResultTestBitmap, ImageSize(2, 2))
            }
        }

        assertIs<CancellationException>(result.exceptionOrNull())
        assertEquals(listOf<ImageBitmap>(OwnedResultTestBitmap), released)
    }

    @Test
    fun releaseFailureIsSuppressedOnPromptCancellation() {
        val workerDispatcher = QueuedTestDispatcher()
        val cancellation = CancellationException("cancelled")
        val releaseFailure = IllegalStateException("release")

        val result = cancelAfterWorkerCompletion(workerDispatcher, cancellation) {
            withOwnedPlatformImageResult(
                dispatcher = workerDispatcher,
                ownedBitmap = { it },
                releaseBitmap = { throw releaseFailure },
            ) {
                OwnedResultTestBitmap
            }
        }

        val thrown = assertIs<CancellationException>(result.exceptionOrNull())
        assertTrue(releaseFailure in thrown.suppressedExceptions)
    }

    @Test
    fun workerCancellationAndFatalFailureKeepIdentity() {
        val workerDispatcher = QueuedTestDispatcher()
        val cancellation = CancellationException("cancelled")
        val fatal = AssertionError("fatal")

        val cancellationResult = runWorkerFailure(workerDispatcher) {
            withOwnedPlatformImageResult(
                dispatcher = workerDispatcher,
                ownedBitmap = { it },
            ) {
                throw cancellation
            }
        }
        val fatalResult = runWorkerFailure(workerDispatcher) {
            withOwnedPlatformImageResult(
                dispatcher = workerDispatcher,
                ownedBitmap = { it },
            ) {
                throw fatal
            }
        }

        assertSame(cancellation, cancellationResult.exceptionOrNull())
        assertSame(fatal, fatalResult.exceptionOrNull())
    }
}

private data object OwnedResultTestBitmap : ImageBitmap {
    override val width: Int = 2
    override val height: Int = 2
    override val colorSpace: ColorSpace = ColorSpaces.Srgb
    override val hasAlpha: Boolean = true
    override val config: ImageBitmapConfig = ImageBitmapConfig.Argb8888

    override fun readPixels(
        buffer: IntArray,
        startX: Int,
        startY: Int,
        width: Int,
        height: Int,
        bufferOffset: Int,
        stride: Int,
    ) = Unit

    override fun prepareToDraw() = Unit
}
