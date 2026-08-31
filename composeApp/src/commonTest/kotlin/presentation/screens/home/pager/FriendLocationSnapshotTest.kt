package io.github.vrcmteam.vrcm.presentation.screens.home.pager

import io.github.vrcmteam.vrcm.network.api.attributes.LocationType
import io.github.vrcmteam.vrcm.network.api.attributes.UserStatus
import io.github.vrcmteam.vrcm.network.api.friends.date.FriendData
import io.github.vrcmteam.vrcm.service.FriendPresence
import io.github.vrcmteam.vrcm.service.FriendStateStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class FriendLocationSnapshotTest {
    @Test
    fun activePrivateFriendUsesGlobalEffectivePresenceAndAppearsOnWeb() {
        val store = FriendStateStore()
        val activeFriend = friend("usr_active", LocationType.Private.value)
        store.putFromEvent(activeFriend)
        val activeFriendsRefresh = store.beginRefresh()

        store.mergeActiveFriends(activeFriendsRefresh, listOf(activeFriend.id))

        val globalFriend = store.snapshot.getValue(activeFriend.id)
        val locations = store.snapshot.values.toFriendLocationSnapshot()
        assertEquals(LocationType.Offline.value, globalFriend.location)
        assertEquals(listOf(activeFriend.id), locations.web.map(FriendData::id))
        assertTrue(locations.private.isEmpty())
    }

    @Test
    fun simultaneousTravelersShareDestinationWithoutLosingLoadingState() {
        val destination = "wrld_destination:3"
        val snapshot = listOf(
            friend("usr_a", LocationType.Traveling.value, destination),
            friend("usr_b", LocationType.Traveling.value, destination),
        ).toFriendLocationSnapshot()

        val group = snapshot.instances.getValue(destination)
        assertEquals(setOf("usr_a", "usr_b"), group.friends.map(FriendData::id).toSet())
        assertEquals(setOf("usr_a", "usr_b"), group.travelingIds)
    }

    @Test
    fun privateOwnPresenceRemainsEligibleForPrivateCategory() {
        assertEquals(
            LocationType.Private.value,
            ownEffectiveLocation(FriendPresence(LocationType.Private.value)),
        )
        assertEquals(null, ownEffectiveLocation(FriendPresence("", "")))
    }

    @Test
    fun statusUpdateReusesPrivateCategoryCardAndUserState() {
        val publishedState = FriendLocationPublishedState()
        val active = friend("usr_self", LocationType.Private.value)
        publishedState.syncSimpleLocation(LocationType.Private, listOf(active))
        val card = publishedState.locationMap.getValue(LocationType.Private).first()
        val userState = card.friends.getValue(active.id)

        publishedState.syncSimpleLocation(
            LocationType.Private,
            listOf(active.copy(status = UserStatus.Busy)),
        )

        assertSame(card, publishedState.locationMap.getValue(LocationType.Private).first())
        assertSame(userState, card.friends.getValue(active.id))
        assertEquals(UserStatus.Busy, userState.value.status)
    }

    private fun friend(
        id: String,
        location: String,
        travelingToLocation: String = "",
    ) = FriendData(
        bio = null,
        currentAvatarImageUrl = "",
        currentAvatarThumbnailImageUrl = "",
        developerType = "none",
        displayName = id,
        friendKey = "",
        id = id,
        imageUrl = "",
        isFriend = true,
        lastLogin = "",
        lastPlatform = "standalonewindows",
        location = location,
        travelingToLocation = travelingToLocation,
        profilePicOverride = "",
        status = UserStatus.Active,
        statusDescription = "",
        userIcon = "",
        pronouns = null,
    )
}
