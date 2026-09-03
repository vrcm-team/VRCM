package io.github.vrcmteam.vrcm.presentation.screens.gallery.editor

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.colorspace.ColorSpace
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import io.github.vrcmteam.vrcm.network.api.avatars.data.AvatarData
import io.github.vrcmteam.vrcm.network.api.files.data.FileTagType
import io.github.vrcmteam.vrcm.network.api.worlds.data.WorldData
import io.github.vrcmteam.vrcm.presentation.screens.world.WorldImageUpdate
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

    @Test
    fun worldCoverCompletionIsConsumedOnlyByTheMatchingWorld() {
        val store = PrintImageEditorSessionStore()
        val token = io.github.vrcmteam.vrcm.core.shared.AccountSessionToken("usr_owner", 1)
        val target = ImageEditorTarget.WorldCover("wrld_test", token)
        val id = store.create(
            source = SelectedImage("source.jpg", byteArrayOf(1)),
            prepared = PreparedImage(SessionTestImageBitmap, ImageSize(16, 9)),
            target = target,
        )
        val world = WorldData(
            authorId = "usr_owner",
            authorName = "Owner",
            capacity = 16,
            createdAt = null,
            description = null,
            favorites = null,
            featured = null,
            heat = 0,
            id = "wrld_test",
            imageUrl = "https://example.test/original.png",
            labsPublicationDate = "",
            name = "World",
            namespace = null,
            organization = "vrchat",
            popularity = 0,
            publicationDate = "",
            recommendedCapacity = 8,
            releaseStatus = "private",
            tags = emptyList(),
            thumbnailImageUrl = "https://example.test/thumbnail.png",
            udonProducts = emptyList(),
            unityPackages = emptyList(),
            updatedAt = null,
            version = 1,
            visits = 0,
        )
        val update = WorldImageUpdate(world, token)

        store.complete(id, ImageEditorSubmission.WorldCover(update))

        assertEquals(null, store.consumeWorldCoverUpdate("wrld_other"))
        assertEquals(update, store.consumeWorldCoverUpdate("wrld_test"))
        assertEquals(null, store.consumeWorldCoverUpdate("wrld_test"))
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
