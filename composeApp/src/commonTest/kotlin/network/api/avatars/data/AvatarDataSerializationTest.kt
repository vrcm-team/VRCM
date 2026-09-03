package io.github.vrcmteam.vrcm.network.api.avatars.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.SerializationException
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AvatarDataSerializationTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun emptyStringCollectionsDecodeAsEmptyLists() {
        val avatar = json.decodeFromString<AvatarData>(
            """{"id":"avtr_test","name":"Test","authorId":"usr_test","authorName":"Author","imageUrl":"","releaseStatus":"public","tags":"","unityPackages":""}"""
        )

        assertTrue(avatar.tags.isEmpty())
        assertTrue(avatar.unityPackages.isEmpty())
    }

    @Test
    fun nonEmptyStringCollectionsAreRejected() {
        assertFailsWith<SerializationException> {
            json.decodeFromString<AvatarData>(
                """{"id":"avtr_test","name":"Test","tags":"unexpected"}"""
            )
        }
        assertFailsWith<SerializationException> {
            json.decodeFromString<AvatarData>(
                """{"id":"avtr_test","name":"Test","unityPackages":"unexpected"}"""
            )
        }
    }

    @Test
    fun impostorPresenceSupportsUrlAndVariantPackageShapes() {
        val urlShape = json.decodeFromString<AvatarData>(
            """{"id":"avtr_url","name":"URL","unityPackages":[{"impostorUrl":"https://example.test/impostor","impostorizerVersion":"0.17.0"}]}"""
        )
        val variantShape = json.decodeFromString<AvatarData>(
            """{"id":"avtr_variant","name":"Variant","unityPackages":[{"variant":"impostor"}]}"""
        )
        val absent = json.decodeFromString<AvatarData>(
            """{"id":"avtr_absent","name":"Absent","unityPackages":[{"variant":"standard","impostorUrl":""}]}"""
        )

        assertTrue(urlShape.hasImpostor)
        assertEquals("0.17.0", urlShape.unityPackages.single().impostorizerVersion)
        assertTrue(variantShape.hasImpostor)
        assertFalse(absent.hasImpostor)
    }
}
