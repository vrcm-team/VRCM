package io.github.vrcmteam.vrcm.presentation.notifications

import io.github.vrcmteam.vrcm.network.api.friends.date.FriendData

/** Platform bridge for opted-in friend activity and inbox alerts. */
interface FriendOnlineNotifier {
    fun notifyOnline(friend: FriendData)
    fun notifyOffline(friendId: String, displayName: String)
    fun notifyBoop(notificationId: String, displayName: String, emojiId: String?)
    fun notifyFriendRequest(notificationId: String, displayName: String)

    /** [groupName] and [title] come straight from the announcement payload. */
    fun notifyGroupAnnouncement(notificationId: String, groupName: String, title: String)
}

internal class NoOpFriendOnlineNotifier : FriendOnlineNotifier {
    override fun notifyOnline(friend: FriendData) = Unit
    override fun notifyOffline(friendId: String, displayName: String) = Unit
    override fun notifyBoop(notificationId: String, displayName: String, emojiId: String?) = Unit
    override fun notifyFriendRequest(notificationId: String, displayName: String) = Unit
    override fun notifyGroupAnnouncement(notificationId: String, groupName: String, title: String) = Unit
}
