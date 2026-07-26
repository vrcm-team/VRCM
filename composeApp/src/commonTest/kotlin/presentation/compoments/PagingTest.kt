package io.github.vrcmteam.vrcm.presentation.compoments

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PagingTest {
    @Test
    fun loadMoreOnlyTriggersNearTheEndOfANonEmptyList() {
        assertFalse(shouldLoadNextPage(lastVisibleIndex = -1, totalItemsCount = 0))
        assertFalse(shouldLoadNextPage(lastVisibleIndex = 3, totalItemsCount = 10))
        assertTrue(shouldLoadNextPage(lastVisibleIndex = 5, totalItemsCount = 10))
    }

    @Test
    fun searchPagingAccountsForItsHeaderItem() {
        assertFalse(shouldLoadNextSearchPage(lastVisibleIndex = 0, dataItemsCount = 0))
        assertFalse(shouldLoadNextSearchPage(lastVisibleIndex = 15, dataItemsCount = 20))
        assertTrue(shouldLoadNextSearchPage(lastVisibleIndex = 16, dataItemsCount = 20))
    }
}
