package io.github.vrcmteam.vrcm.presentation.screens.gallery

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GalleryUploadImageFormatTest {
    @Test
    fun supportedExtensionsResolveToTheirImageMimeTypes() {
        val expected = mapOf(
            "avatar.jpg" to "image/jpeg",
            "avatar.jpeg" to "image/jpeg",
            "avatar.png" to "image/png",
            "avatar.gif" to "image/gif",
            "avatar.webp" to "image/webp",
            "AVATAR.WEBP" to "image/webp",
        )

        assertEquals(
            expected,
            expected.keys.associateWith { GalleryUploadImageFormat.fromFileName(it)?.mimeType },
        )
    }

    @Test
    fun pickerExtensionsComeFromTheSameSupportedFormatDefinition() {
        assertEquals(
            listOf("jpg", "jpeg", "png", "gif", "webp"),
            GalleryUploadImageFormat.allowedExtensions,
        )
    }

    @Test
    fun unknownOrMissingExtensionsAreRejected() {
        assertNull(GalleryUploadImageFormat.fromFileName("avatar.bmp"))
        assertNull(GalleryUploadImageFormat.fromFileName("avatar"))
    }
}
