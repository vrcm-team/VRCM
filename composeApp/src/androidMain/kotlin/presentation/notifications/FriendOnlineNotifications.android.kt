package io.github.vrcmteam.vrcm.presentation.notifications

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.text.format.DateFormat
import android.util.Log
import androidx.core.content.ContextCompat
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.size.Scale
import coil3.toBitmap
import io.github.vrcmteam.vrcm.MainActivity
import io.github.vrcmteam.vrcm.R
import io.github.vrcmteam.vrcm.ACTION_OPEN_NOTIFICATION_TARGET
import io.github.vrcmteam.vrcm.EXTRA_NOTIFICATION_DESTINATION
import io.github.vrcmteam.vrcm.EXTRA_NOTIFICATION_TARGET_ID
import io.github.vrcmteam.vrcm.network.api.friends.date.FriendData
import io.github.vrcmteam.vrcm.service.NotificationLaunchDestination
import java.util.Date
import java.util.concurrent.ConcurrentHashMap

private const val SOCIAL_CHANNEL = "friend_activity_alerts_v2"
private const val MONITOR_CHANNEL = "friend_activity_monitor"
private const val SERVICE_STATUS_CHANNEL = "vrchat_service_status"
private const val SERVICE_STATUS_NOTIFICATION_ID = 0x56525354
private const val LONG_MESSAGE_THRESHOLD = 80

class FriendNotificationFactory(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)
    init { channels() }
    fun monitoringNotification(
        startedAtMillis: Long,
        restoreIntent: PendingIntent,
    ): Notification {
        val builder = builder(MONITOR_CHANNEL, 1)
            .setContentTitle(context.getString(R.string.friend_monitor_title))
            .setContentText(context.getString(R.string.friend_monitor_message))
            .setCategory(Notification.CATEGORY_SERVICE)
            .setWhen(startedAtMillis)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setDeleteIntent(restoreIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        }
        return builder.build()
    }
    fun social(
        id: Int,
        title: String,
        message: CharSequence,
        destination: NotificationLaunchDestination,
        targetId: String,
        largeIcon: Bitmap? = null,
        receivedAtMillis: Long = System.currentTimeMillis(),
        showAvatarTimestamp: Boolean = false,
    ): Notification {
        val builder = builder(SOCIAL_CHANNEL, id, destination, targetId)
            .setContentTitle(title)
            .setContentText(message)
            .setWhen(receivedAtMillis)
            .setCategory(Notification.CATEGORY_SOCIAL)
            .setAutoCancel(true)
        if (message.length > LONG_MESSAGE_THRESHOLD) {
            builder.setStyle(Notification.BigTextStyle().bigText(message))
        }
        if (largeIcon == null || !showAvatarTimestamp) {
            builder.setShowWhen(true)
            largeIcon?.let(builder::setLargeIcon)
        } else {
            // Some OEM templates replace the system timestamp with a large icon.
            builder
                .setShowWhen(false)
                .setSubText(DateFormat.getTimeFormat(context).format(Date(receivedAtMillis)))
                .setLargeIcon(largeIcon)
        }
        return builder.build()
    }
    fun serviceStatus(title: String, message: CharSequence): Notification =
        builder(SERVICE_STATUS_CHANNEL, SERVICE_STATUS_NOTIFICATION_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(Notification.BigTextStyle().bigText(message))
            .setWhen(System.currentTimeMillis())
            .setShowWhen(true)
            .setCategory(Notification.CATEGORY_STATUS)
            .setAutoCancel(true)
            .build()
    private fun builder(
        channel: String,
        code: Int,
        destination: NotificationLaunchDestination? = null,
        targetId: String? = null,
    ): Notification.Builder {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (destination != null && targetId != null) {
                action = ACTION_OPEN_NOTIFICATION_TARGET
                putExtra(EXTRA_NOTIFICATION_DESTINATION, destination.name)
                putExtra(EXTRA_NOTIFICATION_TARGET_ID, targetId)
            }
        }
        val pending = PendingIntent.getActivity(context, code, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(context, channel) else Notification.Builder(context)
        return builder.setSmallIcon(R.mipmap.logo).setContentIntent(pending).setPriority(Notification.PRIORITY_HIGH)
    }
    private fun channels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        manager.createNotificationChannel(NotificationChannel(SOCIAL_CHANNEL, context.getString(R.string.friend_social_channel), NotificationManager.IMPORTANCE_HIGH))
        manager.createNotificationChannel(NotificationChannel(MONITOR_CHANNEL, context.getString(R.string.friend_monitor_channel), NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) })
        manager.createNotificationChannel(NotificationChannel(SERVICE_STATUS_CHANNEL, context.getString(R.string.vrchat_status_channel), NotificationManager.IMPORTANCE_HIGH))
    }
}

class AndroidFriendOnlineNotifier(
    private val context: Context,
    private val imageLoader: ImageLoader,
) : FriendOnlineNotifier {
    private val factory = FriendNotificationFactory(context)
    private val notificationManager = context.getSystemService(NotificationManager::class.java)
    private val latestAvatarRequests = ConcurrentHashMap<Int, Any>()

    override fun notifyOnline(friend: FriendData) {
        if (!canNotify()) return
        val message = friend.statusDescription.trim().ifBlank { context.getString(R.string.friend_online_default) }
        notifySocial(
            id = friend.id.hashCode(),
            title = context.getString(R.string.friend_online_title, friend.displayName),
            message = message,
            destination = NotificationLaunchDestination.UserProfile,
            targetId = friend.id,
            iconUrl = friend.iconUrl,
            showAvatarTimestamp = true,
        )
    }
    override fun notifyOffline(friendId: String, displayName: String, iconUrl: String?) {
        if (!canNotify()) return
        notifySocial(
            id = friendId.hashCode(),
            title = context.getString(R.string.friend_offline_title, displayName),
            message = "",
            destination = NotificationLaunchDestination.UserProfile,
            targetId = friendId,
            iconUrl = iconUrl,
            showAvatarTimestamp = true,
        )
    }

    private fun notifySocial(
        id: Int,
        title: String,
        message: CharSequence,
        destination: NotificationLaunchDestination,
        targetId: String,
        iconUrl: String?,
        showAvatarTimestamp: Boolean = false,
    ) {
        val requestToken = Any()
        val receivedAtMillis = System.currentTimeMillis()
        latestAvatarRequests[id] = requestToken
        notificationManager.notify(
            id,
            factory.social(
                id = id,
                title = title,
                message = message,
                destination = destination,
                targetId = targetId,
                receivedAtMillis = receivedAtMillis,
                showAvatarTimestamp = showAvatarTimestamp,
            ),
        )
        val imageUrl = iconUrl?.trim()?.takeIf(String::isNotEmpty) ?: run {
            latestAvatarRequests.remove(id, requestToken)
            return
        }
        val request = ImageRequest.Builder(context)
            .data(imageUrl)
            .size(NOTIFICATION_ICON_SIZE, NOTIFICATION_ICON_SIZE)
            .scale(Scale.FILL)
            .allowHardware(false)
            .target(
                onError = {
                    Log.w("VRCMNotification", "Unable to load notification avatar: $imageUrl")
                    latestAvatarRequests.remove(id, requestToken)
                },
                onSuccess = { image ->
                    if (latestAvatarRequests[id] !== requestToken) return@target
                    latestAvatarRequests.remove(id, requestToken)
                    if (!canNotify()) return@target
                    notificationManager.notify(
                        id,
                        factory.social(
                            id = id,
                            title = title,
                            message = message,
                            destination = destination,
                            targetId = targetId,
                            largeIcon = notificationIcon(image.toBitmap()),
                            receivedAtMillis = receivedAtMillis,
                            showAvatarTimestamp = showAvatarTimestamp,
                        ),
                    )
                },
            )
            .build()
        imageLoader.enqueue(request)
    }
    override fun notifyBoop(notificationId: String, displayName: String, emojiId: String?, iconUrl: String?) {
        if (!canNotify()) return
        val glyph = when (emojiId) {
            "default_heart" -> "❤"; "default_hand_wave" -> "👋"; "default_laugh" -> "😂"
            "default_thumbs_up" -> "👍"; "default_thinking" -> "🤔"; "default_wow" -> "😮"
            "default_angry" -> "😠"; else -> "✦"
        }
        val id = notificationId.hashCode()
        notifySocial(
            id = id,
            title = context.getString(R.string.friend_boop_title, displayName),
            message = glyph,
            destination = NotificationLaunchDestination.NotificationCenter,
            targetId = notificationId,
            iconUrl = iconUrl,
        )
    }
    override fun notifyFriendRequest(notificationId: String, displayName: String, iconUrl: String?) {
        if (!canNotify()) return
        val id = notificationId.hashCode()
        notifySocial(
            id = id,
            title = context.getString(R.string.friend_request_title, displayName),
            message = "",
            destination = NotificationLaunchDestination.NotificationCenter,
            targetId = notificationId,
            iconUrl = iconUrl,
        )
    }

    override fun notifyGroupEvent(notificationId: String, type: String, groupName: String, message: String, iconUrl: String?) {
        if (!canNotify()) return
        val id = notificationId.hashCode()
        notifySocial(
            id = id,
            title = groupEventTitle(type, groupName),
            message = message,
            destination = NotificationLaunchDestination.NotificationCenter,
            targetId = notificationId,
            iconUrl = iconUrl,
        )
    }

    private fun groupEventTitle(type: String, groupName: String): String {
        val name = groupName.ifBlank { context.getString(R.string.group_notification_default_name) }
        return when (type.trim().lowercase()) {
            "group.announcement" -> context.getString(R.string.group_announcement_title, name)
            "group.event.created" -> context.getString(R.string.group_event_created_title, name)
            "group.event.starting" -> context.getString(R.string.group_event_starting_title, name)
            "group.informative" -> context.getString(R.string.group_informative_title, name)
            "groupchange" -> context.getString(R.string.group_change_title, name)
            "group.joinrequest" -> context.getString(R.string.group_join_request_title, name)
            "group.transfer" -> context.getString(R.string.group_transfer_title, name)
            "group.queueready" -> context.getString(R.string.group_queue_ready_title, name)
            else -> context.getString(R.string.group_notification_title, name)
        }
    }

    override fun notifyVrchatServiceIncident(indicator: String, description: String) {
        if (!canNotify()) return
        val message = when (indicator) {
            "minor" -> context.getString(R.string.vrchat_status_minor)
            "major" -> context.getString(R.string.vrchat_status_major)
            "critical" -> context.getString(R.string.vrchat_status_critical)
            else -> description.ifBlank { context.getString(R.string.vrchat_status_unknown) }
        }
        notificationManager.notify(
            SERVICE_STATUS_NOTIFICATION_ID,
            factory.serviceStatus(context.getString(R.string.vrchat_status_incident_title), message),
        )
    }

    override fun notifyVrchatServiceRestored() {
        if (!canNotify()) return
        notificationManager.notify(
            SERVICE_STATUS_NOTIFICATION_ID,
            factory.serviceStatus(
                context.getString(R.string.vrchat_status_restored_title),
                context.getString(R.string.vrchat_status_restored_message),
            ),
        )
    }

    private fun canNotify() = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Keep the source aspect ratio while always passing the system a complete square bitmap. */
    private fun notificationIcon(source: Bitmap): Bitmap {
        val size = NOTIFICATION_ICON_SIZE
        if (source.width <= 0 || source.height <= 0) return source

        val scale = minOf(size.toFloat() / source.width, size.toFloat() / source.height)
        val width = (source.width * scale).toInt().coerceAtLeast(1)
        val height = (source.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { output ->
            Canvas(output).drawBitmap(
                source,
                null,
                RectF(
                    (size - width) / 2f,
                    (size - height) / 2f,
                    (size + width) / 2f,
                    (size + height) / 2f,
                ),
                Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
            )
        }
    }

    private companion object {
        const val NOTIFICATION_ICON_SIZE = 64
    }
}
