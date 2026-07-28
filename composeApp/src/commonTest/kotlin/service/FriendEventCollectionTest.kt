package io.github.vrcmteam.vrcm.service

import io.github.vrcmteam.vrcm.network.websocket.data.WebSocketEvent
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class FriendEventCollectionTest {
    @Test
    fun malformedEventDoesNotStopFollowingPresenceUpdates() = runTest {
        val handledTypes = mutableListOf<String>()
        val failedTypes = mutableListOf<String>()
        val events = flowOf(
            WebSocketEvent(type = "friend-online", content = "invalid"),
            WebSocketEvent(type = "friend-location", content = "valid"),
        )

        collectFriendWebSocketEvents(
            events = events,
            handle = { event ->
                if (event.content == "invalid") error("malformed payload")
                handledTypes += event.type
            },
            onFailure = { event, _ -> failedTypes += event.type },
        )

        assertEquals(listOf("friend-online"), failedTypes)
        assertEquals(listOf("friend-location"), handledTypes)
    }
}
