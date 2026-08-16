package io.github.vrcmteam.vrcm.presentation.notifications

import android.app.Application
import android.app.Notification
import android.graphics.Bitmap
import android.text.format.DateFormat
import io.github.vrcmteam.vrcm.service.NotificationLaunchDestination
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class FriendNotificationFactoryTest {
    private val context = RuntimeEnvironment.getApplication() as Application
    private val factory = FriendNotificationFactory(context)

    @Test
    fun avatarNotificationUsesSubTextForItsArrivalTime() {
        val notification = factory.social(
            id = 1,
            title = "Friend online",
            message = "Available",
            destination = NotificationLaunchDestination.UserProfile,
            targetId = "usr_friend",
            largeIcon = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888),
            showAvatarTimestamp = true,
        )

        assertFalse(notification.extras.getBoolean(Notification.EXTRA_SHOW_WHEN, true))
        assertEquals(
            DateFormat.getTimeFormat(context).format(Date(notification.`when`)),
            notification.extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString(),
        )
    }

    @Test
    fun notificationWithoutAvatarKeepsTheSystemTimestamp() {
        val notification = factory.social(
            id = 2,
            title = "Friend offline",
            message = "",
            destination = NotificationLaunchDestination.UserProfile,
            targetId = "usr_friend",
        )

        assertTrue(notification.extras.getBoolean(Notification.EXTRA_SHOW_WHEN))
        assertNull(notification.extras.getCharSequence(Notification.EXTRA_SUB_TEXT))
    }
}
