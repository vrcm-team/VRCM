package io.github.vrcmteam.vrcm.service

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class BackgroundNetworkWakeLockTest {
    @Test
    fun holdsCpuUntilForegroundServiceLeaseCloses() {
        val wakeLock = BackgroundNetworkWakeLock(RuntimeEnvironment.getApplication())
        try {
            assertTrue(wakeLock.isHeld)
        } finally {
            wakeLock.close()
        }

        assertFalse(wakeLock.isHeld)
        wakeLock.close()
    }
}
