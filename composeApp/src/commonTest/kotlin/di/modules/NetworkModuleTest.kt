package io.github.vrcmteam.vrcm.di.modules

import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals

class NetworkModuleTest {
    @Test
    fun invalidNullCoercesToDefaultValue() {
        val payload = createNetworkJson()
            .decodeFromString<LenientNetworkPayload>("""{"value":null}""")

        assertEquals("fallback", payload.value)
    }
}

@Serializable
private data class LenientNetworkPayload(
    val value: String = "fallback",
)
