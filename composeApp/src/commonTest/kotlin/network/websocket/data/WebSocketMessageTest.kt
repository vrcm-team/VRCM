package io.github.vrcmteam.vrcm.network.websocket.data

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class WebSocketMessageTest {
    @Test
    fun sessionRejectionFrameIsClassifiedWithoutExposingCredentials() {
        val credential = "authcookie_sensitive"
        val message = Json { ignoreUnknownKeys = true }.decodeFromString<WebSocketMessage>(
            """{"err":"authToken doesn't correspond with an active session","authToken":"$credential","ip":"127.0.0.1"}"""
        )

        val error = assertFailsWith<WebSocketSessionRejectedException> {
            message.requireEvent()
        }

        assertEquals("Pipeline rejected the authenticated session", error.message)
        assertFalse(error.message.orEmpty().contains(credential))
    }

    @Test
    fun otherPipelineErrorsDoNotTriggerSessionRecovery() {
        val errors = listOf(
            "Error finding user usr_example",
            "Pipeline: authToken doesn't correspond with an active session",
        )

        errors.forEach { errorText ->
            val message = Json.decodeFromString<WebSocketMessage>(
                """{"err":"$errorText"}"""
            )

            val error = assertFailsWith<WebSocketPipelineException> {
                message.requireEvent()
            }

            assertEquals("Pipeline error: $errorText", error.message)
        }
    }
}
