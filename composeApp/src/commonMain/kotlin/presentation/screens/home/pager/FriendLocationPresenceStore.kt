package io.github.vrcmteam.vrcm.presentation.screens.home.pager

import io.github.vrcmteam.vrcm.network.api.attributes.LocationType
import io.github.vrcmteam.vrcm.network.api.attributes.UserStatus
import io.github.vrcmteam.vrcm.network.api.friends.date.FriendData
import io.github.vrcmteam.vrcm.service.FriendUpdateEvent

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

internal class FriendLocationPresenceStore {
    private val friendsById = mutableMapOf<String, FriendData>()
    private val activeFriendIds = mutableSetOf<String>()
    private val refreshOverrides = mutableSetOf<String>()
    private val activeRefreshOverrides = mutableMapOf<String, Boolean>()
    private var refreshInProgress = false

    fun clear() {
        friendsById.clear()
        activeFriendIds.clear()
        refreshOverrides.clear()
        activeRefreshOverrides.clear()
        refreshInProgress = false
    }

    fun setActiveFriends(friendIds: Collection<String>) {
        activeFriendIds.clear()
        activeFriendIds.addAll(friendIds)
        activeRefreshOverrides.forEach { (userId, isActive) ->
            if (isActive) activeFriendIds += userId else activeFriendIds -= userId
        }
    }

    fun addPage(friends: Collection<FriendData>) {
        friends.filterNot { refreshInProgress && it.id in refreshOverrides }
            .associateByTo(friendsById, FriendData::id)
    }

    fun replaceFriends(friends: Collection<FriendData>) {
        friendsById.clear()
        friendsById.putAll(friends.associateBy(FriendData::id))
        activeFriendIds.retainAll(friendsById.keys)
    }

    fun beginRefresh() {
        refreshOverrides.clear()
        activeRefreshOverrides.clear()
        refreshInProgress = true
    }

    fun finishRefresh(includedIds: Set<String>, reconcile: Boolean) {
        if (reconcile) {
            val preservedEventIds = refreshOverrides.filterTo(mutableSetOf()) { it in friendsById }
            val retainedIds = includedIds + preservedEventIds
            friendsById.keys.retainAll(retainedIds)
            activeFriendIds.retainAll(retainedIds)
        }
        cancelRefresh()
    }

    fun cancelRefresh() {
        refreshOverrides.clear()
        activeRefreshOverrides.clear()
        refreshInProgress = false
    }

    fun reconcile(includedIds: Set<String>) {
        friendsById.keys.retainAll(includedIds)
        activeFriendIds.retainAll(includedIds)
    }

    fun apply(event: FriendUpdateEvent): Boolean {
        when (event) {
            is FriendUpdateEvent.Active -> {
                markRefreshOverride(event.friend.id, isActive = true)
                activeFriendIds += event.friend.id
                friendsById[event.friend.id] = event.friend
            }

            is FriendUpdateEvent.Online -> {
                markRefreshOverride(event.friend.id, isActive = false)
                activeFriendIds -= event.friend.id
                friendsById[event.friend.id] = event.friend
            }

            is FriendUpdateEvent.LocationChanged -> {
                markRefreshOverride(event.friend.id, isActive = false)
                activeFriendIds -= event.friend.id
                friendsById[event.friend.id] = event.friend
            }

            is FriendUpdateEvent.Offline -> {
                markRefreshOverride(event.userId, isActive = false)
                activeFriendIds -= event.userId
                friendsById.remove(event.userId)
            }

            is FriendUpdateEvent.Updated -> {
                if (event.friend.id in friendsById) friendsById[event.friend.id] = event.friend
            }
            is FriendUpdateEvent.Delete -> {
                markRefreshOverride(event.userId, isActive = false)
                activeFriendIds -= event.userId
                friendsById.remove(event.userId)
            }

            FriendUpdateEvent.RefreshRequired -> return true
        }
        return false
    }

    private fun markRefreshOverride(userId: String, isActive: Boolean) {
        if (refreshInProgress) {
            refreshOverrides += userId
            activeRefreshOverrides[userId] = isActive
        }
    }

    fun snapshot(): FriendLocationSnapshot {
        val effectiveFriends = friendsById.values.map { friend ->
            if (friend.id in activeFriendIds && friend.location == LocationType.Private.value) {
                friend.copy(location = LocationType.Offline.value)
            } else {
                friend
            }
        }
        val byType = effectiveFriends.groupBy { LocationType.fromValue(it.location) }
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
}
