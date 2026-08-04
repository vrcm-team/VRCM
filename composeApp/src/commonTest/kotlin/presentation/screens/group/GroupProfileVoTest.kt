package io.github.vrcmteam.vrcm.presentation.screens.group

import io.github.vrcmteam.vrcm.network.api.groups.data.LimitedGroup
import io.github.vrcmteam.vrcm.network.api.users.data.LimitedUserGroup
import io.github.vrcmteam.vrcm.presentation.screens.group.data.GroupProfileVo
import kotlin.test.Test
import kotlin.test.assertEquals

class GroupProfileVoTest {
    @Test
    fun limitedGroupPreservesAvailableProfileFields() {
        val group = LimitedGroup(
            id = "grp_search",
            name = "Search Group",
            shortCode = "SRCH",
            description = "Search result data",
            iconUrl = "https://example.test/search-icon.png",
            bannerUrl = "https://example.test/search-banner.png",
            memberCount = 84,
        )

        val profile = GroupProfileVo(group)

        assertEquals("grp_search", profile.groupId)
        assertEquals("Search Group", profile.name)
        assertEquals("SRCH", profile.shortCode)
        assertEquals("Search result data", profile.description)
        assertEquals("https://example.test/search-icon.png", profile.iconUrl)
        assertEquals("https://example.test/search-banner.png", profile.bannerUrl)
        assertEquals(84, profile.memberCount)
    }

    @Test
    fun limitedUserGroupPreservesAvailableProfileFields() {
        val group = LimitedUserGroup(
            id = "membership-id",
            groupId = "grp_navigation",
            name = "Navigation Group",
            shortCode = "NAV",
            description = "Already loaded",
            iconUrl = "https://example.test/icon.png",
            bannerUrl = "https://example.test/banner.png",
            memberCount = 42,
        )

        val profile = GroupProfileVo(group)

        assertEquals("grp_navigation", profile.groupId)
        assertEquals("Navigation Group", profile.name)
        assertEquals("NAV", profile.shortCode)
        assertEquals("Already loaded", profile.description)
        assertEquals("https://example.test/icon.png", profile.iconUrl)
        assertEquals("https://example.test/banner.png", profile.bannerUrl)
        assertEquals(42, profile.memberCount)
    }
}
