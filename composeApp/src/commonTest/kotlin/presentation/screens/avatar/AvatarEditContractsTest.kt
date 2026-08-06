package io.github.vrcmteam.vrcm.presentation.screens.avatar

import io.github.vrcmteam.vrcm.network.api.avatars.data.AvatarUpdateData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AvatarEditContractsTest {
    @Test
    fun blankNameIsRejected() {
        assertEquals(
            AvatarMetadataChange.InvalidName,
            avatarMetadataChange("Current", "Description", "   ", "Description"),
        )
    }

    @Test
    fun unchangedMetadataDoesNotCreateRequest() {
        assertEquals(
            AvatarMetadataChange.NoChanges,
            avatarMetadataChange("Current", "Description", " Current ", "Description"),
        )
    }

    @Test
    fun descriptionCanBeClearedWithoutResendingName() {
        assertEquals(
            AvatarMetadataChange.Update(AvatarUpdateData(description = "")),
            avatarMetadataChange("Current", "Description", "Current", ""),
        )
    }

    @Test
    fun fileExtensionCannotDisguiseAnotherImageFormat() {
        val jpegBytes = byteArrayOf(
            0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(),
        )

        assertEquals(
            AvatarCoverValidation.UnsupportedFormat,
            validateAvatarCover("cover.png", jpegBytes),
        )
    }

    @Test
    fun webPUsesMimeTypeDetectedFromItsSignature() {
        val webPBytes = "RIFF1234WEBPVP8 ".encodeToByteArray()

        val valid = assertIs<AvatarCoverValidation.Valid>(
            validateAvatarCover("cover.webp", webPBytes)
        )

        assertEquals("image/webp", valid.cover.mimeType)
        assertEquals("cover.webp", valid.cover.fileName)
    }
}
