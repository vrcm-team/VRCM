package io.github.vrcmteam.vrcm.presentation.compoments

internal fun shouldLoadNextPage(
    lastVisibleIndex: Int,
    totalItemsCount: Int,
    preloadDistance: Int = 5,
): Boolean = totalItemsCount > 0 &&
    lastVisibleIndex >= (totalItemsCount - preloadDistance).coerceAtLeast(0)

internal fun shouldLoadNextSearchPage(
    lastVisibleIndex: Int,
    dataItemsCount: Int,
    preloadDistance: Int = 5,
): Boolean = dataItemsCount > 0 && shouldLoadNextPage(
    lastVisibleIndex = lastVisibleIndex,
    totalItemsCount = dataItemsCount + 1,
    preloadDistance = preloadDistance,
)
