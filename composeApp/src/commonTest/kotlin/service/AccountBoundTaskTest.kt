package io.github.vrcmteam.vrcm.service

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AccountBoundTaskTest {
    @Test
    fun switchingAccountCancelsOldTaskAndStartsLatestAccount() = runTest {
        val tracker = AccountGenerationTracker("usr_a")
        val accountA = tracker.currentToken()!!
        val accountAStarted = CompletableDeferred<Unit>()
        val accountACancelled = CompletableDeferred<Unit>()
        val accountBStarted = CompletableDeferred<Unit>()
        val task = AccountBoundTask(
            scope = this,
            isCurrent = tracker::isCurrent,
            runTask = { token ->
                if (token.userId == "usr_a") {
                    accountAStarted.complete(Unit)
                    try {
                        awaitCancellation()
                    } finally {
                        accountACancelled.complete(Unit)
                    }
                } else {
                    accountBStarted.complete(Unit)
                }
            },
        )
        task.start(accountA)
        accountAStarted.await()

        val accountB = tracker.activate("usr_b").token
        task.start(accountB)

        accountACancelled.await()
        accountBStarted.await()
        assertFalse(tracker.isCurrent(accountA))
        assertTrue(tracker.isCurrent(accountB))
        task.cancelAndJoin()
    }

    @Test
    fun cancelledTaskCanRestartForTheSameAccountOnForeground() = runTest {
        val tracker = AccountGenerationTracker("usr_a")
        val token = tracker.currentToken()!!
        var starts = 0
        val task = AccountBoundTask(
            scope = this,
            isCurrent = tracker::isCurrent,
            runTask = { starts++ },
        )

        task.start(token)
        runCurrent()
        task.cancel()
        task.start(token)
        runCurrent()

        assertEquals(2, starts)
        task.cancelAndJoin()
    }
}
