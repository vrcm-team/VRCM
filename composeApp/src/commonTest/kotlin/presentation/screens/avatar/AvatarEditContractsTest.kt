package io.github.vrcmteam.vrcm.presentation.screens.avatar

import io.github.vrcmteam.vrcm.network.api.avatars.data.AvatarUpdateData
import io.github.vrcmteam.vrcm.network.api.avatars.data.AvatarStyle
import io.github.vrcmteam.vrcm.presentation.screens.avatar.data.AvatarProfileVo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AvatarEditContractsTest {
    @Test
    fun blankNameIsRejected() {
        assertEquals(
            AvatarMetadataChange.InvalidName,
            avatarMetadataChange(
                current = avatar(),
                draft = draft(name = "   "),
                allowedStyles = emptyList(),
            ),
        )
    }

    @Test
    fun unchangedMetadataDoesNotCreateRequest() {
        assertEquals(
            AvatarMetadataChange.NoChanges,
            avatarMetadataChange(
                current = avatar(),
                draft = draft(name = " Current "),
                allowedStyles = emptyList(),
            ),
        )
    }

    @Test
    fun descriptionCanBeClearedWithoutResendingName() {
        assertEquals(
            AvatarMetadataChange.Update(AvatarUpdateData(description = "")),
            avatarMetadataChange(
                current = avatar(),
                draft = draft(description = ""),
                allowedStyles = emptyList(),
            ),
        )
    }

    @Test
    fun managedTagsAndStylesUseAllowedValuesWhileRetainingSystemTags() {
        val current = avatar().copy(
            tags = listOf(
                "system_approved",
                "content_horror",
                "content_future",
                "author_tag_old",
            ),
            primaryStyle = "Anime",
            secondaryStyle = "Robot",
        )
        val styles = listOf(
            AvatarStyle("avst_anime", "Anime"),
            AvatarStyle("avst_robot", "Robot"),
            AvatarStyle("avst_cute", "Cute"),
        )

        val change = avatarMetadataChange(
            current = current,
            draft = draft(
                contentTags = setOf("content_gore", "content_sex"),
                authorTags = "dance, social\ndance",
                primaryStyle = AvatarStyleChoice.Selected("avst_cute"),
                secondaryStyle = AvatarStyleChoice.Clear,
            ),
            allowedStyles = styles,
        )

        assertEquals(
            AvatarMetadataChange.Update(
                AvatarUpdateData(
                    tags = listOf(
                        "system_approved",
                        "content_future",
                        "content_gore",
                        "content_sex",
                        "author_tag_dance",
                        "author_tag_social",
                    ),
                    primaryStyle = "avst_cute",
                    secondaryStyle = "",
                )
            ),
            change,
        )
    }

    @Test
    fun styleIdsNotReturnedByTheServerAreRejected() {
        val change = avatarMetadataChange(
            current = avatar(),
            draft = draft(
                primaryStyle = AvatarStyleChoice.Selected("avst_stale"),
            ),
            allowedStyles = listOf(AvatarStyle("avst_allowed", "Allowed")),
        )

        assertEquals(AvatarMetadataChange.InvalidPrimaryStyle, change)
    }

    @Test
    fun unknownContentTagsAreRejectedBeforeTheRequest() {
        val change = avatarMetadataChange(
            current = avatar(),
            draft = draft(contentTags = setOf("content_unverified")),
            allowedStyles = emptyList(),
        )

        assertEquals(AvatarMetadataChange.InvalidContentTags, change)
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

    private fun avatar() = AvatarProfileVo(
        avatarId = "avtr_owned",
        avatarName = "Current",
        avatarDescription = "Description",
        authorId = "usr_owner",
    )

    private fun draft(
        name: String = "Current",
        description: String = "Description",
        contentTags: Set<String> = emptySet(),
        authorTags: String = "",
        primaryStyle: AvatarStyleChoice = AvatarStyleChoice.Unchanged,
        secondaryStyle: AvatarStyleChoice = AvatarStyleChoice.Unchanged,
    ) = AvatarMetadataDraft(
        name = name,
        description = description,
        contentTags = contentTags,
        authorTags = authorTags,
        primaryStyle = primaryStyle,
        secondaryStyle = secondaryStyle,
    )
}
