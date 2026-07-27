package io.github.vrcmteam.vrcm.presentation.screens.user

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
    fun concurrentUserLoadsAreMergedWhenTheFirstLoadFails() = runTest {
        val gate = UserProfileLoadGate()
        val releaseFirst = CompletableDeferred<Unit>()
        var attempts = 0

        val first = async(start = CoroutineStart.UNDISPATCHED) {
            gate.runLoad {
                attempts++
                releaseFirst.await()
                false
            }
        }
        val followers = List(19) {
            async {
                gate.runLoad {
                    attempts++
                    false
                }
            }
        }

        assertTrue(followers.awaitAll().none { it })
        assertEquals(1, attempts)
        releaseFirst.complete(Unit)

        assertTrue(first.await())
        assertEquals(1, attempts)
    }

    @Test
    fun forcedRefreshDuringLoadRunsAfterTheCurrentRequest() = runTest {
        val gate = UserProfileLoadGate()
        val releaseFirst = CompletableDeferred<Unit>()
        var attempts = 0

        val first = async(start = CoroutineStart.UNDISPATCHED) {
            gate.runLoad {
                attempts++
                releaseFirst.await()
                true
            }
        }
        val forced = async {
            gate.runLoad(forceRefresh = true) {
                attempts++
                true
            }
        }

        assertFalse(forced.await())
        assertEquals(1, attempts)
        releaseFirst.complete(Unit)

        assertTrue(first.await())
        assertEquals(2, attempts)
    }

    @Test
    fun groupFailureOnlyRetriesGroupsOnTheNextRegularRefresh() = runTest {
        val coordinator = UserProfileLoadCoordinator()
        var userAttempts = 0
        var groupAttempts = 0

        coordinator.runLoads(
            loadUser = {
                userAttempts++
                true
            },
            loadGroups = {
                groupAttempts++
                false
            },
        )
        coordinator.runLoads(
            loadUser = {
                userAttempts++
                true
            },
            loadGroups = {
                groupAttempts++
                true
            },
        )

        assertEquals(1, userAttempts)
        assertEquals(2, groupAttempts)
    }

    @Test
    fun thrownLoadFailureLeavesTheGateRetryable() = runTest {
        val gate = UserProfileLoadGate()
        var attempts = 0

        assertFailsWith<IllegalStateException> {
            gate.runLoad {
                attempts++
                error("load failed")
            }
        }

        assertTrue(
            gate.runLoad {
                attempts++
                true
            }
        )
        assertEquals(2, attempts)
    }
}
