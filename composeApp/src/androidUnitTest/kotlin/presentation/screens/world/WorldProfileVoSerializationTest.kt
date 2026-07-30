package io.github.vrcmteam.vrcm.presentation.screens.world

import io.github.vrcmteam.vrcm.network.api.worlds.data.WorldData
import io.github.vrcmteam.vrcm.presentation.screens.world.data.WorldProfileVo
import java.io.ByteArrayOutputStream
import java.io.ObjectOutputStream
import kotlin.test.Test
import kotlin.test.assertTrue

class WorldProfileVoSerializationTest {
    @Test
    fun minimalWorldProfileCanBeSavedInTheNavigationStack() {
        assertJavaSerializable(WorldProfileVo(worldId = "wrld_example"))
    }

    @Test
    fun worldDataProfileCanBeSavedInTheNavigationStack() {
        val world = WorldData(
            authorId = "usr_author",
            authorName = "Author",
            capacity = 16,
            createdAt = null,
            description = "Description",
            favorites = 1,
            featured = false,
            heat = 0,
            id = "wrld_example",
            imageUrl = "https://example.com/world.png",
            labsPublicationDate = "",
            name = "World",
            namespace = null,
            organization = "vrchat",
            popularity = 0,
            publicationDate = "",
            recommendedCapacity = 8,
            releaseStatus = "public",
            tags = emptyList(),
            thumbnailImageUrl = null,
            udonProducts = emptyList(),
            unityPackages = emptyList(),
            updatedAt = null,
            version = 1,
            visits = 1,
        )

        assertJavaSerializable(WorldProfileVo(world))
    }

    private fun assertJavaSerializable(value: Any) {
        val bytes = ByteArrayOutputStream().use { buffer ->
            ObjectOutputStream(buffer).use { it.writeObject(value) }
            buffer.toByteArray()
        }
        assertTrue(bytes.isNotEmpty())
    }
}
