package io.github.vrcmteam.vrcm.presentation.notifications

import android.app.Notification
import android.app.NotificationManager
import android.graphics.Bitmap
import coil3.ImageLoader
import coil3.asImage
import coil3.request.Disposable
import coil3.request.ImageRequest
import coil3.request.ImageResult
import com.russhwolf.settings.SharedPreferencesSettings
import io.github.vrcmteam.vrcm.presentation.settings.SettingsModel
import io.github.vrcmteam.vrcm.presentation.settings.theme.ThemeColor
import io.github.vrcmteam.vrcm.storage.SettingsDao
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class AndroidFriendOnlineNotifierTest {
    private val context = RuntimeEnvironment.getApplication()
    private val preferences = SharedPreferencesSettings.Factory(context).create("notifications-test")
    private val settingsModel = SettingsModel(SettingsDao(preferences), listOf(ThemeColor.Default))
    private val notificationManager = context.getSystemService(NotificationManager::class.java)
    private val imageLoader = RecordingImageLoader(ImageLoader.Builder(context).build())
    private val notifier = AndroidFriendOnlineNotifier(context, imageLoader, SettingsDao(preferences))

    @After
    fun tearDown() {
        imageLoader.shutdown()
    }

    @Test
    fun compatibilityModeSkipsTheAvatarForNewNotifications() {
        settingsModel.saveSettings(settingsModel.settingsVo.copy(notificationCompatibilityModeEnabled = true))

        notifier.notifyFriendRequest("fr_request", "Friend", AVATAR_URL)

        assertTrue(imageLoader.requests.isEmpty())
        assertSystemTimestampWithoutAvatar(onlyNotification())
    }

    @Test
    fun enablingCompatibilityModeWhileAnAvatarLoadsKeepsTheSystemTimestamp() {
        notifier.notifyOffline("usr_friend", "Friend", AVATAR_URL)
        val loadingAvatar = imageLoader.requests.single()
        settingsModel.saveSettings(settingsModel.settingsVo.copy(notificationCompatibilityModeEnabled = true))
        loadingAvatar.completeWithAvatar()

        assertSystemTimestampWithoutAvatar(onlyNotification())

        settingsModel.saveSettings(settingsModel.settingsVo.copy(notificationCompatibilityModeEnabled = false))
        notifier.notifyOffline("usr_friend", "Friend", AVATAR_URL)
        imageLoader.requests.last().completeWithAvatar()

        val notification = onlyNotification()
        assertNotNull(notification.getLargeIcon())
        assertFalse(notification.extras.getBoolean(Notification.EXTRA_SHOW_WHEN))
        assertNotNull(notification.extras.getCharSequence(Notification.EXTRA_SUB_TEXT))
    }

    private fun onlyNotification(): Notification = notificationManager.activeNotifications.single().notification

    private fun assertSystemTimestampWithoutAvatar(notification: Notification) {
        assertNull(notification.getLargeIcon())
        assertTrue(notification.extras.getBoolean(Notification.EXTRA_SHOW_WHEN))
        assertNull(notification.extras.getCharSequence(Notification.EXTRA_SUB_TEXT))
    }

    private fun ImageRequest.completeWithAvatar() {
        requireNotNull(target).onSuccess(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).asImage())
    }

    private class RecordingImageLoader(delegate: ImageLoader) : ImageLoader by delegate {
        val requests = mutableListOf<ImageRequest>()

        override fun enqueue(request: ImageRequest): Disposable {
            requests += request
            return object : Disposable {
                override val job = CompletableDeferred<ImageResult>().apply { cancel() }
                override val isDisposed: Boolean
                    get() = job.isCancelled

                override fun dispose() {
                    job.cancel()
                }
            }
        }
    }

    private companion object {
        const val AVATAR_URL = "https://example.com/avatar.png"
    }
}
