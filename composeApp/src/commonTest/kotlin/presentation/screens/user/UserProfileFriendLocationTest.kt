package io.github.vrcmteam.vrcm.presentation.screens.user

import androidx.compose.runtime.mutableStateMapOf
import io.github.vrcmteam.vrcm.network.api.attributes.LocationType
import io.github.vrcmteam.vrcm.presentation.screens.home.data.FriendLocation
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class UserProfileFriendLocationTest {
    @Test
    fun locationAppearsWhenFriendStateArrivesBeforeLocationIndex() = runTest {
        val locations = MutableStateFlow<Map<String, FriendLocation>>(emptyMap())
        val friendLocation = location("wrld_room:1", "usr_friend")
        val friendLocationValue = MutableStateFlow<FriendLocation?>(null)
        val friendLocationId = MutableStateFlow<String?>(null)
        val collection = launch {
            combine(friendLocationId, locations) { locationId, locationsByUser ->
                resolveFriendLocation("usr_friend", locationId, locationsByUser)
            }.collect { friendLocationValue.value = it }
        }

        friendLocationId.value = friendLocation.location
        runCurrent()
        assertNull(friendLocationValue.value)

        locations.value = mapOf("usr_friend" to friendLocation)
        runCurrent()
        assertEquals(friendLocation, friendLocationValue.value)
        collection.cancel()
    }

    @Test
    fun locationAppearsWhenLocationIndexArrivesBeforeFriendState() = runTest {
        val friendLocation = location("wrld_room:1", "usr_friend")
        val locations = MutableStateFlow(mapOf("usr_friend" to friendLocation))
        val friendLocationId = MutableStateFlow<String?>(null)
        val friendLocationValue = MutableStateFlow<FriendLocation?>(null)
        val collection = launch {
            combine(friendLocationId, locations) { locationId, locationsByUser ->
                resolveFriendLocation("usr_friend", locationId, locationsByUser)
            }.collect { friendLocationValue.value = it }
        }

        runCurrent()
        assertNull(friendLocationValue.value)

        friendLocationId.value = friendLocation.location
        runCurrent()
        assertEquals(friendLocation, friendLocationValue.value)
        collection.cancel()
    }

    private fun location(location: String, userId: String) = FriendLocation(
        location = location,
        friends = mutableStateMapOf(userId to mutableStateOfFriend()),
    )

    private fun mutableStateOfFriend() = androidx.compose.runtime.mutableStateOf(
        io.github.vrcmteam.vrcm.network.api.friends.date.FriendData(
            bio = null,
            currentAvatarImageUrl = "",
            currentAvatarThumbnailImageUrl = "",
            developerType = "none",
            displayName = "Friend",
            friendKey = "",
            id = "usr_friend",
            imageUrl = "",
            isFriend = true,
            lastLogin = "",
            lastPlatform = "standalonewindows",
            location = LocationType.Instance.value,
            profilePicOverride = "",
            status = io.github.vrcmteam.vrcm.network.api.attributes.UserStatus.Active,
            statusDescription = "",
            userIcon = "",
            pronouns = null,
        )
    )
}
