package io.github.vrcmteam.vrcm.presentation.compoments

import kotlin.test.Test
import kotlin.test.assertEquals

class SearchTabTypeTest {
    @Test
    fun avatarIsTheThirdStandardSearchTab() {
        assertEquals(2, SearchTabType.AVATAR.index)
        assertEquals(SearchTabType.AVATAR, SearchTabType.fromIndex(2))
    }
}
