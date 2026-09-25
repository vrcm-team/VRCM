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

    @Test
    fun representedGroupIsMarkedAndMovedToTheFront() {
        val regular = LimitedUserGroup(id = "membership-regular", groupId = "regular")
        val represented = LimitedUserGroup(
            id = "membership-represented",
            groupId = "represented",
            name = "Displayed group",
        )

        val result = prioritizeRepresentedGroup(
            groups = listOf(regular, represented.copy(isRepresenting = false)),
            representedGroup = represented,
        )

        assertEquals(listOf("represented", "regular"), result.map { it.groupId })
        assertEquals(true, result.first().isRepresenting)
    }

    @Test
    fun representedGroupIsAddedWhenTheRegularListOmitsIt() {
        val represented = LimitedUserGroup(
            id = "membership-represented",
            groupId = "represented",
            name = "Displayed group",
        )

        val result = prioritizeRepresentedGroup(
            groups = listOf(LimitedUserGroup(id = "membership-regular", groupId = "regular")),
            representedGroup = represented,
        )

        assertEquals(listOf("represented", "regular"), result.map { it.groupId })
        assertEquals(true, result.first().isRepresenting)
    }

    @Test
    fun existingRepresentationIsPrioritizedWhenTheDedicatedResultIsEmpty() {
        val represented = LimitedUserGroup(
            id = "membership-represented",
            groupId = "represented",
            isRepresenting = true,
        )
        val regular = LimitedUserGroup(id = "membership-regular", groupId = "regular")

        val result = prioritizeRepresentedGroup(
            groups = listOf(regular, represented),
            representedGroup = null,
        )

        assertEquals(listOf("represented", "regular"), result.map { it.groupId })
        assertEquals(true, result.first().isRepresenting)
    }

    @Test
    fun staleRepresentationMarkersAreClearedWhenTheDedicatedResultChanges() {
        val stale = LimitedUserGroup(
            id = "membership-stale",
            groupId = "stale",
            isRepresenting = true,
        )
        val current = LimitedUserGroup(
            id = "membership-current",
            groupId = "current",
        )

        val result = prioritizeRepresentedGroup(
            groups = listOf(stale, current),
            representedGroup = current,
        )

        assertEquals(listOf("current", "stale"), result.map { it.groupId })
        assertEquals(listOf(true, false), result.map { it.isRepresenting })
    }
}
