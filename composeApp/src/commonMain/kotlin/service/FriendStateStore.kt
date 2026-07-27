package io.github.vrcmteam.vrcm.service

import io.github.vrcmteam.vrcm.network.api.friends.date.FriendData

internal data class FriendRefreshToken(
    val generation: Long,
    val eventVersion: Long,
    val refreshVersion: Long,
)

internal class FriendStateStore {
    private val friendsById = mutableMapOf<String, FriendData>()
    private val lastEventVersionById = mutableMapOf<String, Long>()
    private var generation = 0L
    private var eventVersion = 0L
    private var latestRefreshVersion = 0L

    val snapshot: Map<String, FriendData>
        get() = friendsById.toMap()

    fun beginRefresh(): FriendRefreshToken {
        latestRefreshVersion++
        return FriendRefreshToken(generation, eventVersion, latestRefreshVersion)
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

    fun removeFromEvent(userId: String) {
        friendsById.remove(userId)
        recordEvent(userId)
    }

    fun mergeRefresh(
        token: FriendRefreshToken,
        friends: Collection<FriendData>,
        replaceUntouched: Boolean,
    ): Boolean {
        if (token.generation != generation || token.refreshVersion != latestRefreshVersion) return false
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
        lastEventVersionById.clear()
    }

    private fun recordEvent(userId: String) {
        eventVersion++
        lastEventVersionById[userId] = eventVersion
    }

    private fun wasTouchedAfter(token: FriendRefreshToken, userId: String): Boolean =
        lastEventVersionById[userId]?.let { it > token.eventVersion } == true
}
