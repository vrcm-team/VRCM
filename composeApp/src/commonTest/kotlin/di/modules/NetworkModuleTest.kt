package io.github.vrcmteam.vrcm.di.modules

import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertFailsWith

class NetworkModuleTest {
    @Test
    fun invalidNullDoesNotCoerceToDefaultValue() {
        assertFailsWith<SerializationException> {
            createNetworkJson().decodeFromString<StrictNetworkPayload>("""{"value":null}""")
        }
    }
}

@Serializable
private data class StrictNetworkPayload(
    val value: String = "fallback",
)
