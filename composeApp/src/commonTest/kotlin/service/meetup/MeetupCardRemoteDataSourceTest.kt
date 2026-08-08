package io.github.vrcmteam.vrcm.service.meetup

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MeetupCardRemoteDataSourceTest {
    @Test
    fun supportedImageHeadersAreAccepted() {
        assertTrue(looksLikeSupportedImage(withHeader(0xFF, 0xD8, 0xFF, 0xE0)))
        assertTrue(looksLikeSupportedImage(withHeader(0x89, 'P'.code, 'N'.code, 'G'.code)))
        assertTrue(
            looksLikeSupportedImage(
                "RIFF....WEBPVP8 ".encodeToByteArray().also {
                    it[4] = 0; it[5] = 0; it[6] = 0; it[7] = 0
                },
            ),
        )
        assertTrue(looksLikeSupportedImage("GIF89a-and-more-bytes".encodeToByteArray()))
        assertTrue(looksLikeSupportedImage("....ftypheic....".encodeToByteArray()))
    }

    @Test
    fun nonImageResponsesAreRejected() {
        assertFalse(looksLikeSupportedImage(ByteArray(0)))
        assertFalse(looksLikeSupportedImage("short".encodeToByteArray()))
        assertFalse(looksLikeSupportedImage("<!DOCTYPE html><html></html>".encodeToByteArray()))
        assertFalse(looksLikeSupportedImage("{\"error\":\"not found\"}".encodeToByteArray()))
    }

    private fun withHeader(vararg header: Int): ByteArray = ByteArray(16) { index ->
        if (index < header.size) header[index].toByte() else 0
    }
}
