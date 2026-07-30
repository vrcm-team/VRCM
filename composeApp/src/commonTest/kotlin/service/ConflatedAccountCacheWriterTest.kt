package io.github.vrcmteam.vrcm.service

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class ConflatedAccountCacheWriterTest {
    @Test
    fun burstWritesKeepOnlyLatestPendingValuePerAccount() = runTest {
        val firstSaveStarted = CompletableDeferred<Unit>()
        val releaseFirstSave = CompletableDeferred<Unit>()
        val writes = mutableListOf<Pair<String, String>>()
        val writer = ConflatedAccountCacheWriter<String>(this) { accountUserId, value ->
            writes += accountUserId to value
            if (value == "first") {
                firstSaveStarted.complete(Unit)
                releaseFirstSave.await()
            }
        }

        writer.submit("usr_a", "first")
        runCurrent()
        firstSaveStarted.await()
        writer.submit("usr_a", "intermediate")
        writer.submit("usr_a", "latest")
        writer.submit("usr_b", "empty")
        releaseFirstSave.complete(Unit)
        writer.close()
        advanceUntilIdle()

        assertEquals(
            listOf("usr_a" to "first", "usr_a" to "latest", "usr_b" to "empty"),
            writes,
        )
    }
}
