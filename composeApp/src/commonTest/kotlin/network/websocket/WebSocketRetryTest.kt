package io.github.vrcmteam.vrcm.network.websocket

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class WebSocketRetryTest {
    @Test
    fun failedConnectionRetriesAndCancellationStopsTheLoop() = runTest {
        var attempts = 0
        val job = launch {
            retryWebSocketConnection(retryDelayMillis = 100) {
                attempts++
                if (attempts == 1) error("network changed")
                awaitCancellation()
            }
        }

        runCurrent()
        assertEquals(1, attempts)
        advanceTimeBy(100)
        runCurrent()
        assertEquals(2, attempts)

        job.cancelAndJoin()
        advanceTimeBy(1_000)
        assertEquals(2, attempts)
    }
}
