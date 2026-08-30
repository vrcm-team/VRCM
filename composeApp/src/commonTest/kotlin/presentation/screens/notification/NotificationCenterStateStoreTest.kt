package io.github.vrcmteam.vrcm.presentation.screens.notification

import io.github.vrcmteam.vrcm.presentation.screens.home.data.NotificationInboxState
import io.github.vrcmteam.vrcm.presentation.screens.home.data.NotificationItemData
import io.github.vrcmteam.vrcm.presentation.screens.home.data.NotificationSource
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.Test
import kotlin.test.assertEquals

class NotificationCenterStateStoreTest {
    @Test
    fun staleRefreshCannotRevertNotificationMarkedReadByEarlierMutation() = runBlocking {
        val target = notification("not_target")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val store = NotificationCenterStateStore(
            scope = scope,
            initialState = NotificationCenterUiState(
                inboxState = NotificationInboxState().replace(
                    NotificationSource.PIPELINE,
                    listOf(target),
                ),
            ),
            reducerDispatcher = Dispatchers.Default,
        )

        try {
            store.reduce { state ->
                state.copy(inboxState = state.inboxState.markSeen(target))
            }.join()
            store.reduce { state ->
                state.copy(
                    inboxState = state.inboxState.replace(
                        NotificationSource.PIPELINE,
                        listOf(target),
                    ),
                )
            }.join()

            assertEquals(true, store.value.inboxState.pipeline.single().seen)
        } finally {
            store.close()
            scope.cancel()
        }
    }

    @Test
    fun staleRefreshCannotRestoreNotificationConsumedByConcurrentMutation() = runBlocking {
        val target = notification("not_target")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val store = NotificationCenterStateStore(
            scope = scope,
            initialState = NotificationCenterUiState(
                inboxState = NotificationInboxState().replace(
                    NotificationSource.PIPELINE,
                    listOf(target),
                ),
            ),
            reducerDispatcher = Dispatchers.Default,
        )
        val gate = ConcurrentReductionGate(participants = 2)
        val mutationJob = CompletableDeferred<Job>()

        try {
            val mutation = store.reduce { state ->
                gate.awaitPeers()
                state.copy(inboxState = state.inboxState.consume(target))
            }
            mutationJob.complete(mutation)
            val refresh = store.reduce { state ->
                gate.awaitPeers()
                runBlocking { mutationJob.await().join() }
                state.copy(
                    inboxState = state.inboxState.replace(
                        NotificationSource.PIPELINE,
                        listOf(target),
                    ),
                )
            }
            joinAll(mutation, refresh)

            assertEquals(emptyList(), store.value.inboxState.pipeline)
        } finally {
            store.close()
            scope.cancel()
        }
    }

    @Test
    fun concurrentMutationsConsumeBothNotificationsWithoutLostUpdate() = runBlocking {
        val first = notification("not_first")
        val second = notification("not_second")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val store = NotificationCenterStateStore(
            scope = scope,
            initialState = NotificationCenterUiState(
                inboxState = NotificationInboxState().replace(
                    NotificationSource.PIPELINE,
                    listOf(first, second),
                ),
            ),
            reducerDispatcher = Dispatchers.Default,
        )
        val gate = ConcurrentReductionGate(participants = 2)
        val firstMutationJob = CompletableDeferred<Job>()

        try {
            val firstMutation = store.reduce { state ->
                gate.awaitPeers()
                state.copy(inboxState = state.inboxState.consume(first))
            }
            firstMutationJob.complete(firstMutation)
            val secondMutation = store.reduce { state ->
                gate.awaitPeers()
                runBlocking { firstMutationJob.await().join() }
                state.copy(inboxState = state.inboxState.consume(second))
            }
            joinAll(firstMutation, secondMutation)

            assertEquals(emptyList(), store.value.inboxState.pipeline)
        } finally {
            store.close()
            scope.cancel()
        }
    }

    private fun notification(id: String) = NotificationItemData(
        id = id,
        source = NotificationSource.PIPELINE,
        imageUrl = "",
        title = null,
        message = "",
        createdAt = "2026-08-30T00:00:00Z",
        senderUserId = "usr_sender",
        link = null,
        type = "boop",
        actions = emptyList(),
    )
}

private class ConcurrentReductionGate(private val participants: Int) {
    private val entered = atomic(0)
    private val release = CompletableDeferred<Unit>()

    fun awaitPeers() = runBlocking {
        if (entered.incrementAndGet() == participants) release.complete(Unit)
        withTimeoutOrNull(1_000) { release.await() }
    }
}
