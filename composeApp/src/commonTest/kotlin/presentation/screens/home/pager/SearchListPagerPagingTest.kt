package io.github.vrcmteam.vrcm.presentation.screens.home.pager

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SearchListPagerPagingTest {
    @Test
    fun groupLoadMoreIsDisabledForInitialLoadingAndOtherTabs() {
        assertFalse(shouldEnableGroupLoadMore(3, hasMore = true, isLoading = false, itemCount = 0))
        assertFalse(shouldEnableGroupLoadMore(3, hasMore = true, isLoading = true, itemCount = 20))
        assertFalse(shouldEnableGroupLoadMore(1, hasMore = true, isLoading = false, itemCount = 20))
        assertTrue(shouldEnableGroupLoadMore(3, hasMore = true, isLoading = false, itemCount = 20))
    }

    @Test
    fun groupRetryIsOnlyShownForAFailedNonEmptyGroupPage() {
        assertFalse(shouldShowGroupLoadMoreRetry(3, loadMoreFailed = true, itemCount = 0))
        assertFalse(shouldShowGroupLoadMoreRetry(1, loadMoreFailed = true, itemCount = 20))
        assertFalse(shouldShowGroupLoadMoreRetry(3, loadMoreFailed = false, itemCount = 20))
        assertTrue(shouldShowGroupLoadMoreRetry(3, loadMoreFailed = true, itemCount = 20))
    }

    @Test
    fun staleGroupLoadingOwnerCannotReleaseANewerRequest() {
        val gate = GroupLoadingGate()
        val oldRequest = GroupLoadToken(generation = 1, offset = 20, append = true)
        val newRequest = GroupLoadToken(generation = 2, offset = 0, append = false)

        assertTrue(gate.tryAcquire(oldRequest))
        gate.invalidate()
        assertTrue(gate.tryAcquire(newRequest))

        gate.release(oldRequest)

        assertEquals(newRequest, gate.owner.value)
        gate.release(newRequest)
        assertEquals(null, gate.owner.value)
    }
}
