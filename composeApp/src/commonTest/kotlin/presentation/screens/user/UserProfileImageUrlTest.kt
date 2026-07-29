package io.github.vrcmteam.vrcm.presentation.screens.user

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UserProfileImageUrlTest {
    @Test
    fun nullImageUsesAvailableThumbnail() {
        assertEquals(
            "https://example.com/thumbnail.png",
            originalImageUrl(
                imageUrl = null,
                thumbnailImageUrl = "https://example.com/thumbnail.png",
            ),
        )
    }

    @Test
    fun blankImageUsesThumbnailAndRestoresItsOriginalFileUrl() {
        assertEquals(
            "https://api.vrchat.cloud/api/1/file/file_thumbnail-only/7/file",
            originalImageUrl(
                imageUrl = "",
                thumbnailImageUrl =
                    "https://api.vrchat.cloud/api/1/image/file_thumbnail-only/7/256",
            ),
        )
    }

    @Test
    fun availableImageTakesPriorityOverThumbnail() {
        assertEquals(
            "https://example.com/image.png",
            originalImageUrl(
                imageUrl = "https://example.com/image.png",
                thumbnailImageUrl = "https://example.com/thumbnail.png",
            ),
        )
    }

    @Test
    fun availableFileImageIsRestoredBeforeThumbnailFallback() {
        assertEquals(
            "https://api.vrchat.cloud/api/1/file/file_primary/3/file",
            originalImageUrl(
                imageUrl = "https://api.vrchat.cloud/api/1/image/file_primary/3/256",
                thumbnailImageUrl = "https://example.com/thumbnail.png",
            ),
        )
    }

    @Test
    fun missingImageAndThumbnailReturnNull() {
        assertNull(originalImageUrl(imageUrl = "", thumbnailImageUrl = null))
    }
}
