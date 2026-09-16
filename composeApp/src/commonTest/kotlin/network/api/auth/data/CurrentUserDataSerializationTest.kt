package io.github.vrcmteam.vrcm.network.api.auth.data

import io.github.vrcmteam.vrcm.testing.currentUserData
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals

class CurrentUserDataSerializationTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun missingOptionalProfileFieldsDecodeAsEmptyValues() {
        val response = JsonObject(
            json.encodeToJsonElement(currentUserData(userId = "usr_test")).jsonObject.filterKeys {
                it !in optionalProfileFields
            },
        )

        val user = json.decodeFromJsonElement<CurrentUserData>(response)

        assertEquals(emptyList(), user.bioLinks)
        assertEquals("", user.profilePicOverride)
        assertEquals("", user.userIcon)
    }

    private companion object {
        val optionalProfileFields = setOf("bioLinks", "profilePicOverride", "userIcon")
    }
}
