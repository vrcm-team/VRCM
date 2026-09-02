package io.github.vrcmteam.vrcm.service

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class InvitePhotoResponseValidationTest {
    @Test
    fun galleryPhotoPayloadRequiresPngSignature() {
        val png = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
        )

        assertEquals(png.toList(), validateInvitePhotoPayload(png).toList())
        assertFailsWith<IllegalArgumentException> {
            validateInvitePhotoPayload(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()))
        }
    }

    @Test
    fun galleryPhotoPayloadRejectsEmptyAndOversizedInput() {
        assertFailsWith<IllegalArgumentException> {
            validateInvitePhotoPayload(byteArrayOf())
        }
        val oversized = ByteArray((InvitePhotoResponseService.MAX_INVITE_PHOTO_BYTES + 1).toInt())
        oversized[0] = 0x89.toByte()
        oversized[1] = 0x50
        oversized[2] = 0x4E
        oversized[3] = 0x47
        oversized[4] = 0x0D
        oversized[5] = 0x0A
        oversized[6] = 0x1A
        oversized[7] = 0x0A
        assertFailsWith<IllegalArgumentException> {
            validateInvitePhotoPayload(oversized)
        }
    }
}
