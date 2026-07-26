package io.github.vrcmteam.vrcm.presentation.screens.gallery.editor

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.colorspace.ColorSpace
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import kotlinx.coroutines.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PrintImageEditorNavigationTest {
    @Test
    fun pushFailureDiscardsSessionAndReleasesPreviewWithoutReplacingFailure() {
        val store = PrintImageEditorSessionStore()
        val failure = IllegalStateException("push")
        val releaseFailure = IllegalArgumentException("release")
        val released = mutableListOf<ImageBitmap>()

        val thrown = assertFailsWith<IllegalStateException> {
            handoffPreparedImageToEditor(
                source = SelectedImage("photo.png", byteArrayOf(1)),
                prepared = PreparedImage(NavigationTestBitmap, ImageSize(16, 9)),
                sessionStore = store,
                releasePreview = {
                    released += it
                    throw releaseFailure
                },
                push = { throw failure },
            )
        }

        assertSame(failure, thrown)
        assertTrue(releaseFailure in thrown.suppressedExceptions)
        assertEquals(listOf<ImageBitmap>(NavigationTestBitmap), released)
        assertNull(store.get("print-editor-0"))
    }

    @Test
    fun pushCancellationDiscardsSessionAndReleasesPreviewKeepingIdentity() {
        val store = PrintImageEditorSessionStore()
        val cancellation = CancellationException("cancelled")
        val released = mutableListOf<ImageBitmap>()

        val thrown = assertFailsWith<CancellationException> {
            handoffPreparedImageToEditor(
                source = SelectedImage("photo.png", byteArrayOf(1)),
                prepared = PreparedImage(NavigationTestBitmap, ImageSize(16, 9)),
                sessionStore = store,
                releasePreview = released::add,
                push = { throw cancellation },
            )
        }

        assertSame(cancellation, thrown)
        assertEquals(listOf<ImageBitmap>(NavigationTestBitmap), released)
        assertNull(store.get("print-editor-0"))
    }

    @Test
    fun successfulPushTransfersPreviewWithoutReleasingIt() {
        val store = PrintImageEditorSessionStore()
        val released = mutableListOf<ImageBitmap>()
        var pushedSessionId: String? = null

        handoffPreparedImageToEditor(
            source = SelectedImage("photo.png", byteArrayOf(1)),
            prepared = PreparedImage(NavigationTestBitmap, ImageSize(16, 9)),
            sessionStore = store,
            releasePreview = released::add,
            push = { pushedSessionId = it },
        )

        assertEquals(emptyList(), released)
        assertSame(NavigationTestBitmap, store.get(requireNotNull(pushedSessionId))?.prepared?.preview)
    }
}

private data object NavigationTestBitmap : ImageBitmap {
    override val width: Int = 16
    override val height: Int = 9
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
