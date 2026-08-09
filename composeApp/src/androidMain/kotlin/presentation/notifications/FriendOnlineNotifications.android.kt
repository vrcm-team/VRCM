package io.github.vrcmteam.vrcm.presentation.notifications

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import io.github.vrcmteam.vrcm.MainActivity
import io.github.vrcmteam.vrcm.R
import io.github.vrcmteam.vrcm.network.api.friends.date.FriendData

private const val SOCIAL_CHANNEL = "friend_activity_alerts_v2"
private const val MONITOR_CHANNEL = "friend_activity_monitor"

class FriendNotificationFactory(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)
    init { channels() }
    fun monitoringNotification(): Notification = builder(MONITOR_CHANNEL, 1)
        .setContentTitle(context.getString(R.string.friend_monitor_title))
        .setContentText(context.getString(R.string.friend_monitor_message))
        .setCategory(Notification.CATEGORY_SERVICE)
        .setOngoing(true).setOnlyAlertOnce(true).build()
    fun social(id: Int, title: String, message: CharSequence): Notification = builder(SOCIAL_CHANNEL, id)
        .setContentTitle(title).setContentText(message).setStyle(Notification.BigTextStyle().bigText(message))
        .setCategory(Notification.CATEGORY_SOCIAL).setAutoCancel(true).build()
    private fun builder(channel: String, code: Int): Notification.Builder {
        val intent = Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP }
        val pending = PendingIntent.getActivity(context, code, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(context, channel) else Notification.Builder(context)
        return builder.setSmallIcon(R.mipmap.logo).setContentIntent(pending).setPriority(Notification.PRIORITY_HIGH)
    }
    private fun channels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        manager.createNotificationChannel(NotificationChannel(SOCIAL_CHANNEL, context.getString(R.string.friend_social_channel), NotificationManager.IMPORTANCE_HIGH))
        manager.createNotificationChannel(NotificationChannel(MONITOR_CHANNEL, context.getString(R.string.friend_monitor_channel), NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) })
    }
}

class AndroidFriendOnlineNotifier(private val context: Context) : FriendOnlineNotifier {
    private val factory = FriendNotificationFactory(context)
    override fun notifyOnline(friend: FriendData) {
        if (!canNotify()) return
        val message = friend.statusDescription.trim().ifBlank { context.getString(R.string.friend_online_default) }
        context.getSystemService(NotificationManager::class.java).notify(
            friend.id.hashCode(),
            factory.social(
                friend.id.hashCode(),
                context.getString(R.string.friend_online_title, friend.displayName),
                message,
            ),
        )
    }
    override fun notifyOffline(friendId: String, displayName: String) {
        if (!canNotify()) return
        context.getSystemService(NotificationManager::class.java).notify(friendId.hashCode(), factory.social(friendId.hashCode(), context.getString(R.string.friend_offline_title, displayName), ""))
    }
    override fun notifyBoop(notificationId: String, displayName: String, emojiId: String?) {
        if (!canNotify()) return
        val glyph = when (emojiId) {
            "default_heart" -> "❤"; "default_hand_wave" -> "👋"; "default_laugh" -> "😂"
            "default_thumbs_up" -> "👍"; "default_thinking" -> "🤔"; "default_wow" -> "😮"
            "default_angry" -> "😠"; else -> "✦"
        }
        val id = notificationId.hashCode()
        context.getSystemService(NotificationManager::class.java).notify(
            id, factory.social(id, context.getString(R.string.friend_boop_title, displayName), glyph)
        )
    }
    override fun notifyFriendRequest(notificationId: String, displayName: String) {
        if (!canNotify()) return
        val id = notificationId.hashCode()
        context.getSystemService(NotificationManager::class.java).notify(
            id,
            factory.social(id, context.getString(R.string.friend_request_title, displayName), ""),
        )
    }

    override fun notifyGroupAnnouncement(notificationId: String, groupName: String, title: String) {
        if (!canNotify()) return
        val id = notificationId.hashCode()
        context.getSystemService(NotificationManager::class.java).notify(
            id,
            factory.social(
                id,
                context.getString(R.string.group_announcement_title, groupName),
                title,
            ),
        )
    }

    private fun canNotify() = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}
