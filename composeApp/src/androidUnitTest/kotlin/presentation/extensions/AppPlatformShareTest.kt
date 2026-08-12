package io.github.vrcmteam.vrcm.presentation.extensions

import android.app.Application
import android.content.Intent
import io.github.vrcmteam.vrcm.AndroidAppPlatform
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.logger.EmptyLogger
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AppPlatformShareTest {
    @Test
    fun sharesUrlAsPlainTextThroughTheSystemChooser() {
        val application = RuntimeEnvironment.getApplication() as Application
        val platform = AndroidAppPlatform(application, EmptyLogger())
        val url = "https://vrchat.com/home/user/usr_example"

        assertTrue(platform.shareUrl(url))

        val chooser = assertNotNull(shadowOf(application).nextStartedActivity)
        val sendIntent = assertNotNull(
            chooser.getParcelableExtra(Intent.EXTRA_INTENT) as Intent?,
        )
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        assertEquals(Intent.ACTION_SEND, sendIntent.action)
        assertEquals("text/plain", sendIntent.type)
        assertEquals(url, sendIntent.getStringExtra(Intent.EXTRA_TEXT))
    }
}
