package io.github.vrcmteam.vrcm.presentation.screens.gallery

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GallerySelectionSessionStoreTest {
    @Test
    fun pickerResultIsConsumedOnceAndContainsNoBytes() {
        val store = GallerySelectionSessionStore()
        val id = store.create()

        assertTrue(store.complete(id, selection("file_1")))
        assertEquals("file_1", store.consume(id)?.fileId)
        assertNull(store.consume(id))
    }

    @Test
    fun onlyFirstCompletionOfASessionIsAccepted() {
        val store = GallerySelectionSessionStore()
        val id = store.create()

        assertTrue(store.complete(id, selection("file_first")))
        assertFalse(store.complete(id, selection("file_second")))
        assertEquals("file_first", store.consume(id)?.fileId)
    }

    @Test
    fun pendingSessionSurvivesConsumeUntilCompletedOrCancelled() {
        val store = GallerySelectionSessionStore()
        val id = store.create()

        assertNull(store.consume(id))
        assertTrue(store.isPending(id))
        assertTrue(store.complete(id, selection("file_late")))
        assertEquals("file_late", store.consume(id)?.fileId)
        assertFalse(store.isPending(id))
    }

    @Test
    fun cancelledAndUnknownSessionsRejectResults() {
        val store = GallerySelectionSessionStore()
        val id = store.create()
        store.cancel(id)

        assertFalse(store.complete(id, selection("file_1")))
        assertNull(store.consume(id))
        assertFalse(store.complete("gallery-selection-unknown", selection("file_1")))
    }

    private fun selection(fileId: String) = GallerySelection(
        fileId = fileId,
        fileName = "name",
        extension = ".png",
        imageUrl = "https://image",
    )
}
