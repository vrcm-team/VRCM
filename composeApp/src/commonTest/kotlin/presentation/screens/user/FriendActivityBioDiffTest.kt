package io.github.vrcmteam.vrcm.presentation.screens.user

import kotlin.test.Test
import kotlin.test.assertEquals

class FriendActivityBioDiffTest {
    @Test
    fun keepsOnlyAddedAndRemovedLinesAroundSharedLines() {
        assertEquals(
            listOf(
                FriendActivityBioDiffLine(added = false, text = "Old first line"),
                FriendActivityBioDiffLine(added = true, text = "New first line"),
                FriendActivityBioDiffLine(added = true, text = "New final line"),
            ),
            friendActivityBioDiff(
                previous = "Old first line\nShared line",
                current = "New first line\nShared line\nNew final line",
            ),
        )
    }
}
