package io.github.vrcmteam.vrcm.presentation.notifications

import io.github.vrcmteam.vrcm.network.api.friends.date.FriendData

/** Platform bridge for opted-in favorite-friend and Boop alerts. */
interface FriendOnlineNotifier {
    fun notifyOnline(friend: FriendData)
    fun notifyOffline(friendId: String, displayName: String)
    fun notifyBoop(notificationId: String, displayName: String, emojiId: String?)
}

internal class NoOpFriendOnlineNotifier : FriendOnlineNotifier {
    override fun notifyOnline(friend: FriendData) = Unit
    override fun notifyOffline(friendId: String, displayName: String) = Unit
    override fun notifyBoop(notificationId: String, displayName: String, emojiId: String?) = Unit
}
