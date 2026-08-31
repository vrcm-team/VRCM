package io.github.vrcmteam.vrcm.service

import io.github.vrcmteam.vrcm.network.api.attributes.LocationType
import io.github.vrcmteam.vrcm.network.api.friends.date.FriendData
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal data class FriendRefreshToken(
    val generation: Long,
    val eventVersion: Long,
)

internal class FriendRefreshCoordinator {
    private val mutex = Mutex()

    suspend fun <T> runRefresh(block: suspend () -> T): T = mutex.withLock { block() }
}

internal class FriendAccountTracker {
    private var userId: String? = null

    fun onAuthenticated(newUserId: String): Boolean {
        val accountChanged = userId != null && userId != newUserId
        userId = newUserId
        return accountChanged
    }

    fun onLogout() {
        userId = null
    }
}

internal class FriendStateStore {
    private val friendsById = mutableMapOf<String, FriendData>()
    private val activeFriendIds = mutableSetOf<String>()
    private val lastEventVersionById = mutableMapOf<String, Long>()
    private var generation = 0L
    private var eventVersion = 0L

    val snapshot: Map<String, FriendData>
        get() = friendsById.mapValues { (userId, friend) ->
            friend.asEffectivePresence(userId in activeFriendIds)
        }

    fun friend(userId: String): FriendData? = friendsById[userId]

    fun beginRefresh(): FriendRefreshToken {
        return FriendRefreshToken(generation, eventVersion)
    }

    fun updateFromEvent(
        userId: String,
        update: (FriendData?) -> FriendData?,
    ): FriendData? {
        val updated = update(friendsById[userId]) ?: return null
        friendsById[userId] = updated
        recordEvent(userId)
        return updated
    }

    fun updateOrRemoveFromEvent(
        userId: String,
        update: (FriendData?) -> FriendData?,
    ): FriendData? {
        val updated = update(friendsById[userId])
        if (updated == null) friendsById.remove(userId) else friendsById[userId] = updated
        recordEvent(userId)
        return updated
    }

    fun putFromEvent(friend: FriendData) {
        friendsById[friend.id] = friend
        recordEvent(friend.id)
    }

    fun setActiveFromEvent(userId: String, isActive: Boolean) {
        if (isActive) activeFriendIds += userId else activeFriendIds -= userId
        recordEvent(userId)
    }

    fun removeFromEvent(userId: String) {
        friendsById.remove(userId)
        activeFriendIds.remove(userId)
        recordEvent(userId)
    }

    /** Applies the signed-in user's active-friend snapshot without overwriting newer socket events. */
    fun mergeActiveFriends(token: FriendRefreshToken, activeIds: Collection<String>): Boolean {
        if (token.generation != generation) return false
        val incoming = activeIds.toSet()
        val previous = activeFriendIds.toSet()
        (friendsById.keys + activeFriendIds + incoming).forEach { userId ->
            if (!wasTouchedAfter(token, userId)) {
                if (userId in incoming) activeFriendIds += userId else activeFriendIds -= userId
            }
        }
        return previous != activeFriendIds
    }

    fun mergeRefresh(
        token: FriendRefreshToken,
        friends: Collection<FriendData>,
        replaceUntouched: Boolean,
    ): Boolean {
        if (token.generation != generation) return false
        val incoming = friends.associateBy(FriendData::id)
        if (replaceUntouched) {
            friendsById.keys
                .filter { it !in incoming && !wasTouchedAfter(token, it) }
                .forEach(friendsById::remove)
        }
        incoming.forEach { (userId, friend) ->
            if (!wasTouchedAfter(token, userId)) friendsById[userId] = friend
        }
        return true
    }

    fun clear() {
        generation++
        friendsById.clear()
        activeFriendIds.clear()
        lastEventVersionById.clear()
    }

    fun restore(friends: Collection<FriendData>) {
        generation++
        friendsById.clear()
        friendsById.putAll(friends.associateBy(FriendData::id))
        activeFriendIds.clear()
        lastEventVersionById.clear()
    }

    private fun recordEvent(userId: String) {
        eventVersion++
        lastEventVersionById[userId] = eventVersion
    }

    private fun wasTouchedAfter(token: FriendRefreshToken, userId: String): Boolean =
        lastEventVersionById[userId]?.let { it > token.eventVersion } == true
}

private fun FriendData.asEffectivePresence(isActive: Boolean): FriendData =
    if (isActive && location == LocationType.Private.value) {
        copy(location = LocationType.Offline.value)
    } else {
        this
    }
