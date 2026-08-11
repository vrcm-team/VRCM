package io.github.vrcmteam.vrcm

import android.app.Application
import android.provider.Settings
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.logger.EmptyLogger
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AppSettingsIntentTest {
    @Test
    fun opensTheCurrentApplicationManagementPage() {
        val application = RuntimeEnvironment.getApplication() as Application

        AndroidAppPlatform(application, EmptyLogger()).openAppSettings()

        val intent = shadowOf(application).nextStartedActivity
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, intent.action)
        assertEquals("package:${application.packageName}", intent.data.toString())
    }
}
