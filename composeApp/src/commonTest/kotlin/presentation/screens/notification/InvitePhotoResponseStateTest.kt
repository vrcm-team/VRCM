package io.github.vrcmteam.vrcm.presentation.screens.notification

import io.github.vrcmteam.vrcm.core.shared.AccountSessionToken
import io.github.vrcmteam.vrcm.presentation.screens.gallery.GallerySelection
import io.github.vrcmteam.vrcm.presentation.screens.home.data.NotificationItemData
import io.github.vrcmteam.vrcm.presentation.screens.home.data.NotificationSource
import io.github.vrcmteam.vrcm.presentation.screens.home.data.identity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class InvitePhotoResponseStateTest {
    @Test
    fun oneNotificationCannotReserveASecondPhotoResponseWhileFirstIsPending() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val token = AccountSessionToken("usr_test", 1)
        val store = NotificationCenterStateStore(
            scope = scope,
            initialState = NotificationCenterUiState(sessionToken = token),
            reducerDispatcher = dispatcher,
        )
        val item = notification("not_invite")
        val selection = GallerySelection("file_gallery", "photo.png", ".png", "https://api.vrchat.cloud/api/1/image/file_gallery/1/2048")

        try {
            assertEquals(
                true,
                store.reserveMutation(
                    token,
                    item.identity,
                    PendingNotificationMutation.PhotoResponse(
                        selection,
                        InvitePhotoResponsePhase.PREPARING,
                    ),
                ),
            )
            assertEquals(
                false,
                store.reserveMutation(
                    token,
                    item.identity,
                    PendingNotificationMutation.PhotoResponse(
                        selection,
                        InvitePhotoResponsePhase.PREPARING,
                    ),
                ),
            )
        } finally {
            store.close()
            scope.cancel()
        }
    }

    @Test
    fun photoResponseTransitionCannotWriteIntoAnotherSession() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val token = AccountSessionToken("usr_test", 1)
        val otherToken = AccountSessionToken("usr_other", 2)
        val store = NotificationCenterStateStore(
            scope = scope,
            initialState = NotificationCenterUiState(sessionToken = token),
            reducerDispatcher = dispatcher,
        )
        val item = notification("not_invite")
        val selection = GallerySelection("file_gallery", "photo.png", ".png", "https://api.vrchat.cloud/api/1/image/file_gallery/1/2048")

        try {
            assertEquals(
                false,
                store.transitionPhotoResponse(
                    otherToken,
                    item.identity,
                    selection,
                    InvitePhotoResponsePhase.PREPARING,
                ),
            )
            assertEquals(emptyMap(), store.value.pendingMutations)
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
        type = "invite",
        actions = emptyList(),
    )
}
