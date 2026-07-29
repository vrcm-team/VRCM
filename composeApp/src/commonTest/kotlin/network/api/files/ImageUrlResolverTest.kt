package io.github.vrcmteam.vrcm.network.api.files

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ImageUrlResolverTest {
    @Test
    fun nullImageUsesAvailableThumbnail() {
        assertEquals(
            "https://example.com/thumbnail.png",
            resolveOriginalImageUrl(
                imageUrl = null,
                thumbnailImageUrl = "https://example.com/thumbnail.png",
            ),
        )
    }

    @Test
    fun blankImageUsesThumbnailAndRestoresItsOriginalFileUrl() {
        assertEquals(
            "https://api.vrchat.cloud/api/1/file/file_thumbnail-only/7/file",
            resolveOriginalImageUrl(
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
            resolveOriginalImageUrl(
                imageUrl = "https://example.com/image.png",
                thumbnailImageUrl = "https://example.com/thumbnail.png",
            ),
        )
    }

    @Test
    fun availableFileImageIsRestoredBeforeThumbnailFallback() {
        assertEquals(
            "https://api.vrchat.cloud/api/1/file/file_primary/3/file",
            resolveOriginalImageUrl(
                imageUrl = "https://api.vrchat.cloud/api/1/image/file_primary/3/256",
                thumbnailImageUrl = "https://example.com/thumbnail.png",
            ),
        )
    }

    @Test
    fun missingImageAndThumbnailReturnNull() {
        assertNull(resolveOriginalImageUrl(imageUrl = "", thumbnailImageUrl = null))
    }
}
