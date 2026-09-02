package io.github.vrcmteam.vrcm.presentation.screens.notification

import io.github.vrcmteam.vrcm.core.shared.AccountSessionToken
import io.github.vrcmteam.vrcm.presentation.screens.home.data.NotificationInboxState
import io.github.vrcmteam.vrcm.presentation.screens.home.data.NotificationItemData
import io.github.vrcmteam.vrcm.presentation.screens.home.data.NotificationSource
import io.github.vrcmteam.vrcm.presentation.screens.home.data.identity
import io.github.vrcmteam.vrcm.presentation.screens.gallery.GallerySelection
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class NotificationCenterStateStoreTest {
    @Test
    fun queuedReservationsCannotStartMultipleMutationTypesForSameNotification() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val token = AccountSessionToken(userId = "usr_test", generation = 1)
        val store = NotificationCenterStateStore(
            scope = scope,
            initialState = NotificationCenterUiState(sessionToken = token),
            reducerDispatcher = dispatcher,
        )
        val target = notification("not_target")
        val action = NotificationItemData.ActionData(data = "", type = "Accept")

        try {
            val accepted = listOf(
                async {
                    store.reserveMutation(
                        token,
                        target.identity,
                        PendingNotificationMutation.Action(action),
                    )
                },
                async {
                    store.reserveMutation(
                        token,
                        target.identity,
                        PendingNotificationMutation.Read,
                    )
                },
                async {
                    store.reserveMutation(
                        token,
                        target.identity,
                        PendingNotificationMutation.Delete,
                    )
                },
            ).awaitAll()

            assertEquals(1, accepted.count { it })
        } finally {
            store.close()
            scope.cancel()
        }
    }

    @Test
    fun cancelledReservationCannotLeavePendingMutation() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val token = AccountSessionToken(userId = "usr_test", generation = 1)
        val store = NotificationCenterStateStore(
            scope = scope,
            initialState = NotificationCenterUiState(sessionToken = token),
            reducerDispatcher = dispatcher,
        )
        val target = notification("not_target")

        try {
            val reservation = launch(start = CoroutineStart.UNDISPATCHED) {
                store.reserveMutation(
                    token,
                    target.identity,
                    PendingNotificationMutation.Delete,
                )
            }
            reservation.cancelAndJoin()
            store.reduce { it }.join()

            assertEquals(false, target.identity in store.value.pendingMutations)
        } finally {
            store.close()
            scope.cancel()
        }
    }

    @Test
    fun staleRefreshCannotRevertNotificationMarkedReadByEarlierMutation() = runTest {
        val target = notification("not_target")
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val store = NotificationCenterStateStore(
            scope = scope,
            initialState = NotificationCenterUiState(
                inboxState = NotificationInboxState().replace(
                    NotificationSource.PIPELINE,
                    listOf(target),
                ),
            ),
            reducerDispatcher = dispatcher,
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
    fun staleRefreshCannotRestoreNotificationConsumedByConcurrentMutation() = runTest {
        val target = notification("not_target")
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val store = NotificationCenterStateStore(
            scope = scope,
            initialState = NotificationCenterUiState(
                inboxState = NotificationInboxState().replace(
                    NotificationSource.PIPELINE,
                    listOf(target),
                ),
            ),
            reducerDispatcher = dispatcher,
        )

        try {
            val mutation = store.reduce { state ->
                state.copy(inboxState = state.inboxState.consume(target))
            }
            val refresh = store.reduce { state ->
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
    fun concurrentMutationsConsumeBothNotificationsWithoutLostUpdate() = runTest {
        val first = notification("not_first")
        val second = notification("not_second")
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val store = NotificationCenterStateStore(
            scope = scope,
            initialState = NotificationCenterUiState(
                inboxState = NotificationInboxState().replace(
                    NotificationSource.PIPELINE,
                    listOf(first, second),
                ),
            ),
            reducerDispatcher = dispatcher,
        )

        try {
            val firstMutation = store.reduce { state ->
                state.copy(inboxState = state.inboxState.consume(first))
            }
            val secondMutation = store.reduce { state ->
                state.copy(inboxState = state.inboxState.consume(second))
            }
            joinAll(firstMutation, secondMutation)

            assertEquals(emptyList(), store.value.inboxState.pipeline)
        } finally {
            store.close()
            scope.cancel()
        }
    }

    @Test
    fun renewedSessionCleanupAllowsPhotoResponseRetry() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val initialToken = AccountSessionToken(userId = "usr_test", generation = 1)
        val renewedToken = AccountSessionToken(userId = "usr_test", generation = 2)
        val store = NotificationCenterStateStore(
            scope = scope,
            initialState = NotificationCenterUiState(sessionToken = initialToken),
            reducerDispatcher = dispatcher,
        )
        val target = notification("not_photo")
        val selection = GallerySelection(
            fileId = "file_gallery",
            fileName = "photo.png",
            extension = ".png",
            imageUrl = "https://api.vrchat.cloud/api/1/image/file_gallery/1/2048",
        )
        val mutation = PendingNotificationMutation.PhotoResponse(
            selection = selection,
            phase = InvitePhotoResponsePhase.RESPONDING,
        )

        try {
            store.reduce {
                NotificationCenterUiState(sessionToken = renewedToken)
            }.join()
            assertEquals(true, store.reserveMutation(renewedToken, target.identity, mutation))

            assertEquals(false, store.finishMutation(initialToken, target.identity))
            assertEquals(true, target.identity in store.value.pendingMutations)
            assertEquals(true, store.finishMutation(renewedToken, target.identity))
            assertEquals(false, target.identity in store.value.pendingMutations)
            assertEquals(true, store.reserveMutation(renewedToken, target.identity, mutation))
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
