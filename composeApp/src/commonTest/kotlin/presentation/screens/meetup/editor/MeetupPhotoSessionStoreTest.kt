package io.github.vrcmteam.vrcm.presentation.screens.meetup.editor

import androidx.compose.ui.graphics.ImageBitmap
import io.github.vrcmteam.vrcm.service.meetup.MeetupPhotoCandidate
import io.github.vrcmteam.vrcm.storage.meetup.MeetupCrop
import io.github.vrcmteam.vrcm.storage.meetup.MeetupOrientation
import io.github.vrcmteam.vrcm.storage.meetup.MeetupPhotoSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class MeetupPhotoSessionStoreTest {
    @Test
    fun editingPortraitDoesNotMutateLandscape() {
        val store = MeetupPhotoSessionStore(releasePreview = {})
        val session = store.create(preparedPhoto())
        val landscapeBefore = session.landscapeCrop.value

        session.updateCrop(MeetupOrientation.Portrait, MeetupCrop(.1f, .2f, 2f))

        assertEquals(landscapeBefore, session.landscapeCrop.value)
        assertEquals(MeetupCrop(.1f, .2f, 2f), session.portraitCrop.value)
    }

    @Test
    fun completeReturnsLatestCropsExactlyOnceAndReleasesPreview() {
        val released = mutableListOf<ImageBitmap>()
        val store = MeetupPhotoSessionStore(releasePreview = released::add)
        val prepared = preparedPhoto()
        val session = store.create(prepared)
        session.updateCrop(MeetupOrientation.Landscape, MeetupCrop(-.05f, 0f, 3f))

        val result = store.complete(session.id)

        assertEquals(MeetupCrop(-.05f, 0f, 3f), result?.landscapeCrop)
        assertEquals(prepared.candidate.portraitCrop, result?.portraitCrop)
        assertEquals(listOf(prepared.preview), released)
        assertNull(store.get(session.id))
        assertNull(store.complete(session.id))
        assertEquals(1, released.size)
    }

    @Test
    fun discardReleasesPreviewAndDropsSession() {
        val released = mutableListOf<ImageBitmap>()
        val store = MeetupPhotoSessionStore(releasePreview = released::add)
        val prepared = preparedPhoto()
        val session = store.create(prepared)

        store.discard(session.id)
        store.discard(session.id)

        assertSame(prepared.preview, released.single())
        assertNull(store.get(session.id))
    }

    private fun preparedPhoto(): MeetupPreparedPhoto = MeetupPreparedPhoto(
        candidate = MeetupPhotoCandidate(
            source = MeetupPhotoSource.LocalAlbum,
            sourceId = null,
            sourceUrl = null,
            fileName = "photo.png",
            bytes = byteArrayOf(1, 2, 3),
            width = 3000,
            height = 4000,
            portraitCrop = MeetupCrop(zoom = 1.4f),
            landscapeCrop = MeetupCrop(zoom = 2.4f),
        ),
        preview = ImageBitmap(4, 4),
    )
}
