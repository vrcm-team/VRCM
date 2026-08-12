package io.github.vrcmteam.vrcm.presentation.notifications

import io.github.vrcmteam.vrcm.network.api.friends.date.FriendData

/** Platform bridge for opted-in friend activity and inbox alerts. */
interface FriendOnlineNotifier {
    fun notifyOnline(friend: FriendData)
    fun notifyOffline(friendId: String, displayName: String)
    fun notifyBoop(notificationId: String, displayName: String, emojiId: String?)
    fun notifyFriendRequest(notificationId: String, displayName: String)

    /** [groupName] and [message] come from the VRChat group notification payload. */
    fun notifyGroupEvent(notificationId: String, type: String, groupName: String, message: String)
    fun notifyVrchatServiceIncident(indicator: String, description: String)
    fun notifyVrchatServiceRestored()
}

internal class NoOpFriendOnlineNotifier : FriendOnlineNotifier {
    override fun notifyOnline(friend: FriendData) = Unit
    override fun notifyOffline(friendId: String, displayName: String) = Unit
    override fun notifyBoop(notificationId: String, displayName: String, emojiId: String?) = Unit
    override fun notifyFriendRequest(notificationId: String, displayName: String) = Unit
    override fun notifyGroupEvent(notificationId: String, type: String, groupName: String, message: String) = Unit
    override fun notifyVrchatServiceIncident(indicator: String, description: String) = Unit
    override fun notifyVrchatServiceRestored() = Unit
}
