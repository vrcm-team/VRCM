package io.github.vrcmteam.vrcm.presentation.screens.notification

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.vrcmteam.vrcm.core.shared.AccountSessionToken
import io.github.vrcmteam.vrcm.presentation.screens.home.data.NotificationIdentity
import io.github.vrcmteam.vrcm.presentation.screens.home.data.NotificationInboxState
import io.github.vrcmteam.vrcm.presentation.screens.home.data.NotificationItemData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

internal data class NotificationCenterUiState(
    val sessionToken: AccountSessionToken? = null,
    val inboxState: NotificationInboxState = NotificationInboxState(),
    val pendingNotificationActions: Map<NotificationIdentity, NotificationItemData.ActionData> = emptyMap(),
    val pendingReadNotifications: Set<NotificationIdentity> = emptySet(),
    val pendingDeleteNotifications: Set<NotificationIdentity> = emptySet(),
    val isRefreshing: Boolean = false,
    val hasRefreshError: Boolean = false,
)

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
    private val reductions = Channel<PendingReduction>(Channel.UNLIMITED)

    val value: NotificationCenterUiState
        get() = mutableValue

    init {
        scope.launch(reducerDispatcher) {
            try {
                for (pending in reductions) {
                    try {
                        mutableValue = pending.reducer(mutableValue)
                        pending.completion.complete(Unit)
                    } catch (cancellation: CancellationException) {
                        pending.completion.cancel(cancellation)
                        throw cancellation
                    } catch (error: Throwable) {
                        pending.completion.completeExceptionally(error)
                        throw error
                    }
                }
            } finally {
                reductions.close()
                val cancellation = CancellationException("Notification state reducer stopped")
                while (true) {
                    val pending = reductions.tryReceive().getOrNull() ?: break
                    pending.completion.cancel(cancellation)
                }
            }
        }
    }

    fun reduce(
        reducer: (NotificationCenterUiState) -> NotificationCenterUiState,
    ): Job {
        val completion = CompletableDeferred<Unit>()
        if (reductions.trySend(PendingReduction(reducer, completion)).isFailure) {
            completion.cancel(CancellationException("Notification state reducer is closed"))
        }
        return completion
    }

    fun close() {
        reductions.close()
    }

    private data class PendingReduction(
        val reducer: (NotificationCenterUiState) -> NotificationCenterUiState,
        val completion: CompletableDeferred<Unit>,
    )
}
