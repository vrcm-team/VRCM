package io.github.vrcmteam.vrcm.presentation.screens.user

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UserProfileLoadGateTest {
    @Test
    fun failedUserLoadCanBeRetried() = runTest {
        val gate = UserProfileLoadGate()

        assertTrue(gate.runLoad { false })
        assertTrue(gate.runLoad { true })
    }

    @Test
    fun loadedUserIsCachedUntilAForcedRefresh() = runTest {
        val gate = UserProfileLoadGate()

        assertTrue(gate.runLoad { true })
        assertFalse(gate.runLoad { true })
        assertTrue(gate.runLoad(forceRefresh = true) { true })
    }

    @Test
    fun concurrentUserLoadsOnlyExecuteOnce() = runTest {
        val gate = UserProfileLoadGate()
        val start = CompletableDeferred<Unit>()
        var attempts = 0

        val results = List(20) {
            async(Dispatchers.Default) {
                start.await()
                gate.runLoad {
                    attempts++
                    true
                }
            }
        }

        start.complete(Unit)

        assertEquals(1, results.awaitAll().count { it })
        assertEquals(1, attempts)
    }

    @Test
    fun forcedRefreshDuringLoadRunsAfterTheCurrentRequest() = runTest {
        val gate = UserProfileLoadGate()
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        var attempts = 0

        val first = async(Dispatchers.Default) {
            gate.runLoad {
                attempts++
                firstStarted.complete(Unit)
                releaseFirst.await()
                true
            }
        }
        firstStarted.await()
        val forced = async(Dispatchers.Default) {
            gate.runLoad(forceRefresh = true) {
                attempts++
                true
            }
        }
        yield()

        assertEquals(1, attempts)
        releaseFirst.complete(Unit)

        assertTrue(first.await())
        assertTrue(forced.await())
        assertEquals(2, attempts)
    }
}
