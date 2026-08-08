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
        val failures = mutableListOf<Pair<Int, String?>>()
        val job = launch {
            retryWebSocketConnection(
                retryDelayMillis = 100,
                onFailure = { error, consecutiveFailures ->
                    failures += consecutiveFailures to error.message
                },
                connect = {
                    attempts++
                    if (attempts == 1) error("network changed")
                    awaitCancellation()
                },
            )
        }

        runCurrent()
        assertEquals(1, attempts)
        assertEquals(listOf<Pair<Int, String?>>(1 to "network changed"), failures)
        advanceTimeBy(100)
        runCurrent()
        assertEquals(2, attempts)

        job.cancelAndJoin()
        advanceTimeBy(1_000)
        assertEquals(2, attempts)
    }

    @Test
    fun reconnectAfterASuccessfulConnectionStillWaitsForTheBaseDelay() = runTest {
        var attempts = 0
        val job = launch {
            retryWebSocketConnection(
                retryDelayMillis = 100,
                onFailure = { _, _ -> },
                connect = {
                    attempts++
                    // The first call returns normally, which resets the failure counter.
                    if (attempts >= 2) awaitCancellation()
                },
            )
        }

        runCurrent()
        assertEquals(1, attempts, "a reset failure counter must not collapse the backoff to zero")
        advanceTimeBy(100)
        runCurrent()
        assertEquals(2, attempts)

        job.cancelAndJoin()
    }
}
