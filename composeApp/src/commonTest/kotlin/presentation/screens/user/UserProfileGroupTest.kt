package io.github.vrcmteam.vrcm.presentation.screens.user

import io.github.vrcmteam.vrcm.network.api.users.data.LimitedUserGroup
import kotlin.test.Test
import kotlin.test.assertEquals

class UserProfileGroupTest {
    @Test
    fun mutualGroupsAreExcludedFromRegularGroupsForOtherUsers() {
        val regular = LimitedUserGroup(id = "regular")
        val mutual = LimitedUserGroup(id = "mutual", mutualGroup = true)

        assertEquals(listOf(regular), visibleUserGroups(listOf(regular, mutual), isSelf = false))
    }

    @Test
    fun allGroupsRemainVisibleOnTheCurrentUsersProfile() {
        val groups = listOf(
            LimitedUserGroup(id = "regular"),
            LimitedUserGroup(id = "mutual", mutualGroup = true),
        )

        assertEquals(groups, visibleUserGroups(groups, isSelf = true))
    }
}
