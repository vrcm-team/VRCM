package io.github.vrcmteam.vrcm.presentation.screens.home.pager

import io.github.vrcmteam.vrcm.network.api.attributes.LocationType
import io.github.vrcmteam.vrcm.network.api.attributes.UserStatus
import io.github.vrcmteam.vrcm.network.api.friends.date.FriendData

internal data class FriendLocationGroup(
    val friends: List<FriendData>,
    val travelingIds: Set<String> = emptySet(),
)

internal data class FriendLocationSnapshot(
    val offline: List<FriendData>,
    val web: List<FriendData>,
    val private: List<FriendData>,
    val instances: Map<String, FriendLocationGroup>,
)

/** Builds location presentation groups from the global friend presence snapshot. */
internal fun Collection<FriendData>.toFriendLocationSnapshot(): FriendLocationSnapshot {
    val byType = groupBy { LocationType.fromValue(it.location) }
    val travelingFriends = byType[LocationType.Traveling]
        .orEmpty()
        .filter { it.travelingToLocation.isNotBlank() }
    val instanceFriends = byType[LocationType.Instance].orEmpty() + travelingFriends.map {
        it.copy(location = it.travelingToLocation)
    }
    val travelingIds = travelingFriends.mapTo(mutableSetOf(), FriendData::id)
    val instances = instanceFriends.groupBy(FriendData::location).mapValues { (_, friends) ->
        FriendLocationGroup(
            friends = friends,
            travelingIds = friends.mapTo(mutableSetOf(), FriendData::id).intersect(travelingIds),
        )
    }
    val offlineOrWebFriends = byType[LocationType.Offline].orEmpty()
    return FriendLocationSnapshot(
        offline = offlineOrWebFriends.filter { it.status == UserStatus.Offline },
        web = offlineOrWebFriends.filter { it.status != UserStatus.Offline },
        private = byType[LocationType.Private].orEmpty(),
        instances = instances,
    )
}
