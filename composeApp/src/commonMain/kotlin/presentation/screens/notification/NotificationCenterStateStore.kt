package io.github.vrcmteam.vrcm.presentation.screens.notification

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.vrcmteam.vrcm.core.shared.AccountSessionToken
import io.github.vrcmteam.vrcm.presentation.screens.home.data.NotificationIdentity
import io.github.vrcmteam.vrcm.presentation.screens.home.data.NotificationInboxState
import io.github.vrcmteam.vrcm.presentation.screens.home.data.NotificationItemData
import io.github.vrcmteam.vrcm.presentation.screens.gallery.GallerySelection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch

internal data class NotificationCenterUiState(
    val sessionToken: AccountSessionToken? = null,
    val inboxState: NotificationInboxState = NotificationInboxState(),
    val pendingMutations: Map<NotificationIdentity, PendingNotificationMutation> = emptyMap(),
    val failedPhotoResponses: Map<NotificationIdentity, GallerySelection> = emptyMap(),
    val isRefreshing: Boolean = false,
    val hasRefreshError: Boolean = false,
)

internal sealed interface PendingNotificationMutation {
    data class Action(val action: NotificationItemData.ActionData) : PendingNotificationMutation
    data class PhotoResponse(
        val selection: GallerySelection,
        val phase: InvitePhotoResponsePhase,
    ) : PendingNotificationMutation
    data object Read : PendingNotificationMutation
    data object Delete : PendingNotificationMutation
}

internal enum class InvitePhotoResponsePhase {
    PREPARING,
    RESPONDING,
}

/**
 * Applies notification UI state reductions in FIFO order on the supplied UI dispatcher.
 * Network jobs may submit concurrently; this actor remains the only Compose state writer.
 */
internal class NotificationCenterStateStore(
    scope: CoroutineScope,
    initialState: NotificationCenterUiState,
    reducerDispatcher: CoroutineDispatcher,
) {
    private var mutableValue by mutableStateOf(initialState)
    private val reductions = Channel<ReductionCommand>(Channel.UNLIMITED)

    val value: NotificationCenterUiState
        get() = mutableValue

    init {
        scope.launch(reducerDispatcher) {
            try {
                for (pending in reductions) {
                    try {
                        pending.execute()
                    } catch (cancellation: CancellationException) {
                        pending.cancel(cancellation)
                        throw cancellation
                    } catch (error: Throwable) {
                        pending.fail(error)
                        throw error
                    }
                }
            } finally {
                reductions.close()
                val cancellation = CancellationException("Notification state reducer stopped")
                while (true) {
                    val pending = reductions.tryReceive().getOrNull() ?: break
                    pending.cancel(cancellation)
                }
            }
        }
    }

    fun reduce(
        reducer: (NotificationCenterUiState) -> NotificationCenterUiState,
    ): Job {
        val completion = CompletableDeferred<Unit>()
        enqueue(
            ReductionCommandImpl(
                reducer = { reducer(it) to Unit },
                completion = completion,
                skipIfCompletionCancelled = false,
            ),
        )
        return completion
    }

    suspend fun reserveMutation(
        sessionToken: AccountSessionToken,
        identity: NotificationIdentity,
        mutation: PendingNotificationMutation,
    ): Boolean = reduceWithResult { current ->
        if (current.sessionToken != sessionToken || identity in current.pendingMutations) {
            current to false
        } else {
            current.copy(
                pendingMutations = current.pendingMutations + (identity to mutation),
            ) to true
        }
    }

    suspend fun transitionPhotoResponse(
        sessionToken: AccountSessionToken,
        identity: NotificationIdentity,
        selection: GallerySelection,
        phase: InvitePhotoResponsePhase,
    ): Boolean = reduceWithResult { current ->
        val existing = current.pendingMutations[identity]
        if (
            current.sessionToken != sessionToken ||
            existing != null && existing !is PendingNotificationMutation.PhotoResponse
        ) {
            current to false
        } else {
            current.copy(
                pendingMutations = current.pendingMutations + (
                        identity to PendingNotificationMutation.PhotoResponse(selection, phase)
                        ),
                failedPhotoResponses = current.failedPhotoResponses + (identity to selection),
            ) to true
        }
    }

    suspend fun finishMutation(
        sessionToken: AccountSessionToken,
        identity: NotificationIdentity,
    ): Boolean = reduceWithResult { current ->
        if (current.sessionToken != sessionToken) {
            current to false
        } else {
            current.copy(
                pendingMutations = current.pendingMutations - identity,
            ) to true
        }
    }

    fun close() {
        reductions.close()
    }

    private suspend fun <T> reduceWithResult(
        reducer: (NotificationCenterUiState) -> Pair<NotificationCenterUiState, T>,
    ): T {
        val completion = CompletableDeferred<T>(currentCoroutineContext()[Job])
        enqueue(
            ReductionCommandImpl(
                reducer = reducer,
                completion = completion,
                skipIfCompletionCancelled = true,
            ),
        )
        return completion.await()
    }

    private fun enqueue(command: ReductionCommand) {
        if (reductions.trySend(command).isFailure) {
            command.cancel(CancellationException("Notification state reducer is closed"))
        }
    }

    private interface ReductionCommand {
        fun execute()
        fun cancel(cancellation: CancellationException)
        fun fail(error: Throwable)
    }

    private inner class ReductionCommandImpl<T>(
        private val reducer: (NotificationCenterUiState) -> Pair<NotificationCenterUiState, T>,
        private val completion: CompletableDeferred<T>,
        private val skipIfCompletionCancelled: Boolean,
    ) : ReductionCommand {
        override fun execute() {
            if (skipIfCompletionCancelled && !completion.isActive) return
            val (updated, result) = reducer(mutableValue)
            mutableValue = updated
            completion.complete(result)
        }

        override fun cancel(cancellation: CancellationException) {
            completion.cancel(cancellation)
        }

        override fun fail(error: Throwable) {
            completion.completeExceptionally(error)
        }
    }
}
