package io.github.vrcmteam.vrcm.presentation.screens.gallery.editor

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.colorspace.ColorSpace
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import io.github.vrcmteam.vrcm.network.api.avatars.data.AvatarData
import io.github.vrcmteam.vrcm.network.api.files.data.FileTagType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PrintImageEditorSessionStoreTest {
    @Test
    fun sessionDataIsKeptOutsideTheNavigationScreen() {
        val store = PrintImageEditorSessionStore()
        val source = SelectedImage("source.jpg", byteArrayOf(1, 2, 3))
        val prepared = PreparedImage(SessionTestImageBitmap, ImageSize(16, 9))
        val croppedSource = PreparedImageSource(
            source.copy(bytes = byteArrayOf(4, 5, 6)),
            PreparedImage(CroppedSessionTestImageBitmap, ImageSize(12, 9)),
        )

        val id = store.create(source, prepared, croppedSource = croppedSource)

        assertEquals(source, store.get(id)?.source)
        assertEquals(prepared, store.get(id)?.prepared)
        assertEquals(croppedSource, store.get(id)?.croppedSource)
        store.discard(id)
        assertNull(store.get(id))
    }

    @Test
    fun uploadCompletionWaitsForGalleryCollector() = runBlocking {
        val store = PrintImageEditorSessionStore()
        val id = store.create(
            SelectedImage("source.jpg", byteArrayOf(1)),
            PreparedImage(SessionTestImageBitmap, ImageSize(16, 9)),
        )

        store.complete(id)

        assertEquals(Unit, store.uploadCompletions.first())
        assertNull(store.get(id))
    }

    @Test
    fun avatarCoverCompletionWaitsUntilTheMatchingProfileConsumesIt() {
        val store = PrintImageEditorSessionStore()
        val target = ImageEditorTarget.AvatarCover("avtr_test")
        val id = store.create(
            source = SelectedImage("source.jpg", byteArrayOf(1)),
            prepared = PreparedImage(SessionTestImageBitmap, ImageSize(16, 9)),
            target = target,
        )
        val updated = AvatarData(
            id = "avtr_test",
            name = "Test",
            imageUrl = "https://example.test/cover.png",
        )

        assertEquals(target, store.get(id)?.target)
        store.complete(id, ImageEditorSubmission.AvatarCover(updated))

        assertEquals(updated, store.avatarCoverUpdates.value[updated.id])
        assertEquals(updated, store.consumeAvatarCoverUpdate(updated.id))
        assertTrue(store.avatarCoverUpdates.value.isEmpty())
        assertNull(store.get(id))
    }

    @Test
    fun galleryCompletionKeepsTheUploadedTagForRefresh() = runBlocking {
        val store = PrintImageEditorSessionStore()
        val target = ImageEditorTarget.Gallery(FileTagType.Emoji)
        val id = store.create(
            source = SelectedImage("source.png", byteArrayOf(1)),
            prepared = PreparedImage(SessionTestImageBitmap, ImageSize(16, 16)),
            target = target,
        )

        store.complete(id, ImageEditorSubmission.Gallery(FileTagType.Emoji))

        assertEquals(FileTagType.Emoji, store.galleryUploadCompletions.first())
        assertNull(store.get(id))
    }
}

private data object SessionTestImageBitmap : ImageBitmap {
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
    ) = error("Session store tests do not read preview pixels")

    override fun prepareToDraw() = Unit
}

private data object CroppedSessionTestImageBitmap : ImageBitmap {
    override val width: Int = 12
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
    ) = error("Session store tests do not read preview pixels")

    override fun prepareToDraw() = Unit
}
