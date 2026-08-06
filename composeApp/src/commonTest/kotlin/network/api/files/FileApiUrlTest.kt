package io.github.vrcmteam.vrcm.network.api.files

import kotlin.test.Test
import kotlin.test.assertEquals

class FileApiUrlTest {
    @Test
    fun imageUrlUsesUploadedFileVersion() {
        assertEquals(
            "https://api.vrchat.cloud/api/1/image/file_avatar/4/1024",
            FileApi.imageUrl(fileId = "file_avatar", fileVersion = 4),
        )
    }
}
