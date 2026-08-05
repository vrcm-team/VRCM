package io.github.vrcmteam.vrcm.presentation.screens.user

import kotlin.test.Test
import kotlin.test.assertEquals

class MutualFriendsContentStateTest {
    @Test
    fun firstRequestShowsLoading() {
        assertEquals(
            MutualFriendsContentState.Loading,
            resolveMutualFriendsContentState(
                hasLoadedSuccessfully = false,
                isLoading = true,
                hasError = false,
                totalCount = 0,
            ),
        )
    }

    @Test
    fun backgroundRefreshKeepsLoadedFriendsVisible() {
        assertEquals(
            MutualFriendsContentState.Content,
            resolveMutualFriendsContentState(
                hasLoadedSuccessfully = true,
                isLoading = true,
                hasError = false,
                totalCount = 3,
            ),
        )
    }

    @Test
    fun backgroundRefreshKeepsLoadedEmptyStateVisible() {
        assertEquals(
            MutualFriendsContentState.Empty,
            resolveMutualFriendsContentState(
                hasLoadedSuccessfully = true,
                isLoading = true,
                hasError = false,
                totalCount = 0,
            ),
        )
    }

    @Test
    fun backgroundRefreshFailureKeepsCachedFriendsVisible() {
        assertEquals(
            MutualFriendsContentState.Content,
            resolveMutualFriendsContentState(
                hasLoadedSuccessfully = true,
                isLoading = false,
                hasError = true,
                totalCount = 3,
            ),
        )
    }
}
