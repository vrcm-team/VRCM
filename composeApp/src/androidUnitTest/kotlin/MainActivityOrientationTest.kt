package io.github.vrcmteam.vrcm

import android.app.Application
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [24, 35])
class MainActivityOrientationTest {
    @Test
    @Config(qualifiers = "sw411dp-w411dp-h891dp-port")
    fun locksNarrowScreenWhenActivityIsCreated() {
        val controller = Robolectric.buildActivity(MainActivity::class.java)

        val activity = controller.create().get()

        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, activity.requestedOrientation)
        controller.destroy()
    }

    @Test
    fun updatesOrientationPolicyWhenDeviceWidthClassChanges() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).get()

        activity.onConfigurationChanged(
            Configuration(activity.resources.configuration).apply {
                screenWidthDp = 891
                screenHeightDp = 411
                smallestScreenWidthDp = 411
            },
        )
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, activity.requestedOrientation)

        activity.onConfigurationChanged(
            Configuration(activity.resources.configuration).apply {
                screenWidthDp = 840
                screenHeightDp = 673
                smallestScreenWidthDp = 673
            },
        )
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, activity.requestedOrientation)
    }
}
