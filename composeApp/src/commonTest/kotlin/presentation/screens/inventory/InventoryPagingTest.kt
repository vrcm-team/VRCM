package io.github.vrcmteam.vrcm.presentation.screens.inventory

import io.github.vrcmteam.vrcm.network.api.inventory.data.InventoryData
import io.github.vrcmteam.vrcm.network.api.inventory.data.InventoryItemData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InventoryPagingTest {
    @Test
    fun overlappingPageAdvancesByServerRowsAndDeduplicatesByItemId() {
        val current = InventoryPagingSnapshot(
            items = listOf(item("inv_1"), item("inv_2")),
            nextOffset = 2,
            totalCount = 5,
        )

        val result = appendInventoryPage(
            current = current,
            page = InventoryData(
                data = listOf(item("inv_2"), item("inv_3")),
                totalCount = 5,
            ),
            pageSize = 2,
        )

        assertEquals(listOf("inv_1", "inv_2", "inv_3"), result.items.map { it.id })
        assertEquals(4, result.nextOffset)
        assertTrue(result.hasMore)
    }

    @Test
    fun emptyPageStopsPaginationEvenWhenServerTotalIsStale() {
        val result = appendInventoryPage(
            current = InventoryPagingSnapshot(nextOffset = 40, totalCount = 100),
            page = InventoryData(data = emptyList(), totalCount = 100),
            pageSize = 20,
        )

        assertEquals(40, result.nextOffset)
        assertFalse(result.hasMore)
    }

    @Test
    fun shortPageWithoutTotalCountMarksTheEnd() {
        val result = appendInventoryPage(
            current = InventoryPagingSnapshot(),
            page = InventoryData(data = listOf(item("inv_1"))),
            pageSize = 20,
        )

        assertEquals(1, result.nextOffset)
        assertFalse(result.hasMore)
    }

    private fun item(id: String) = InventoryItemData(id = id, name = id)
}
