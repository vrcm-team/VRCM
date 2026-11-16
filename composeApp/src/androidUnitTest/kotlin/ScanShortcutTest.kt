package io.github.vrcmteam.vrcm

import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.util.Xml
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [25, 35])
class ScanShortcutTest {
    @Test
    fun launcherTaskResetIsIsolatedFromTheRunningApp() {
        val context = RuntimeEnvironment.getApplication()
        // Android adds these flags to the first intent of every static shortcut.
        val shortcut = shortcutIntent().addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_TASK_ON_HOME,
        )
        val entry = assertNotNull(context.packageManager.resolveActivity(shortcut, 0)?.activityInfo)
        assertTrue(
            entry.taskAffinity.isNullOrEmpty(),
            "The launcher's NEW_TASK | CLEAR_TASK flags must not target the running app task",
        )
        val controller = Robolectric.buildActivity(ScanShortcutActivity::class.java, shortcut).create()
        val forwarded = assertNotNull(shadowOf(controller.get()).nextStartedActivity)
        assertEquals(MainActivity::class.java.name, forwarded.component?.className)
        assertEquals(ScanShortcutActivity.ACTION_SCAN_QR_CODE, forwarded.action)
        val reuseFlags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        assertEquals(reuseFlags, forwarded.flags and reuseFlags)
        assertEquals(
            0,
            forwarded.flags and (Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_TASK_ON_HOME or Intent.FLAG_ACTIVITY_MULTIPLE_TASK),
        )
        assertTrue(controller.get().isFinishing)
        controller.destroy()
    }

    private fun shortcutIntent(): Intent {
        val context = RuntimeEnvironment.getApplication()
        return context.resources.getXml(R.xml.shortcuts).use { parser ->
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG && parser.name == "intent") {
                    return@use Intent.parseIntent(context.resources, parser, Xml.asAttributeSet(parser))
                }
            }
            error("Missing scan shortcut intent")
        }
    }

    @Test
    fun launcherPackageIsLiteralSoOemLaunchersCanReadItWithoutResolvingResources() {
        val context = RuntimeEnvironment.getApplication()
        context.resources.getXml(R.xml.shortcuts).use { parser ->
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG && parser.name == "intent") {
                    assertEquals(
                        context.packageName,
                        parser.getAttributeValue("http://schemas.android.com/apk/res/android", "targetPackage"),
                    )
                    return
                }
            }
            error("Missing scan shortcut intent")
        }
    }

    @Test
    fun launcherShortcutTargetsTheInstalledVariantAndSurvivesActivityRecreation() {
        val context = RuntimeEnvironment.getApplication()
        val shortcut = shortcutIntent()
        assertEquals(context.packageName, shortcut.component?.packageName)
        assertEquals(ScanShortcutActivity::class.java.name, shortcut.component?.className)
        val entry = context.packageManager.resolveActivity(shortcut, 0)
        assertTrue(entry?.activityInfo?.exported == true)
        val trampoline = Robolectric.buildActivity(ScanShortcutActivity::class.java, shortcut).create()
        val intent = assertNotNull(shadowOf(trampoline.get()).nextStartedActivity)
        trampoline.destroy()
        assertEquals(MainActivity::class.java.name, intent.component?.className)
        val resolved = context.packageManager.resolveActivity(intent, 0)
        assertEquals(MainActivity::class.java.name, resolved?.activityInfo?.name)
        assertTrue(resolved?.activityInfo?.exported == true)

        val controller = Robolectric.buildActivity(MainActivity::class.java, intent).create()
        val saved = Bundle()
        controller.saveInstanceState(saved)
        assertTrue(saved.getBoolean("pending-scan"))
        controller.destroy()

        val restored = Robolectric.buildActivity(MainActivity::class.java).create(saved)
        val restoredState = Bundle()
        restored.saveInstanceState(restoredState)
        assertTrue(restoredState.getBoolean("pending-scan"))
        restored.destroy()
    }

    @Test
    fun warmLaunchAlsoQueuesTheScanRequest() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).create()
        val trampoline = Robolectric.buildActivity(ScanShortcutActivity::class.java, shortcutIntent()).create()
        val forwarded = assertNotNull(shadowOf(trampoline.get()).nextStartedActivity)
        trampoline.destroy()
        controller.newIntent(forwarded)
        val saved = Bundle()
        controller.saveInstanceState(saved)
        assertTrue(saved.getBoolean("pending-scan"))
        controller.destroy()
    }
}
