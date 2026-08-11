package io.github.vrcmteam.vrcm

import android.app.Application
import io.github.vrcmteam.vrcm.service.FriendActivityForegroundService
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.logger.EmptyLogger
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class BackgroundMonitoringTimerResetTest {
    @Test
    fun resetSignalsAnExistingMonitorWithoutStartingTheService() {
        val application = RuntimeEnvironment.getApplication() as Application
        val shadowApplication = shadowOf(application)

        AndroidAppPlatform(application, EmptyLogger()).resetBackgroundFriendMonitoringTimer()

        assertEquals(
            FriendActivityForegroundService.ACTION_RESET_MONITORING_TIMER,
            shadowApplication.broadcastIntents.last().action,
        )
        assertNull(shadowApplication.nextStartedService)
    }
}
