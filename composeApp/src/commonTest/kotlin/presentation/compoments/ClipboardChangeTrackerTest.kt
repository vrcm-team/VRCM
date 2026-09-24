package io.github.vrcmteam.vrcm.presentation.compoments

import com.russhwolf.settings.MapSettings
import io.github.vrcmteam.vrcm.storage.SettingsDao
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ClipboardChangeTrackerTest {
    @Test
    fun unchangedClipboardIsNotReadAgainEvenAfterRestart() {
        val settings = MapSettings()
        val tracker = ClipboardChangeTracker(SettingsDao(settings))

        assertTrue(tracker.shouldRead(changeToken = 7))
        tracker.markRead(changeToken = 7)
        assertFalse(tracker.shouldRead(changeToken = 7))

        // 重启 App 后仍记得读过的那份内容；有 App 复制了新内容、标记变了才再读
        val restarted = ClipboardChangeTracker(SettingsDao(settings))
        assertFalse(restarted.shouldRead(changeToken = 7))
        assertTrue(restarted.shouldRead(changeToken = 8))
    }

    @Test
    fun platformWithoutChangeMarkerAlwaysReads() {
        val tracker = ClipboardChangeTracker(SettingsDao(MapSettings()))

        tracker.markRead(changeToken = null)

        assertTrue(tracker.shouldRead(changeToken = null))
    }
}
