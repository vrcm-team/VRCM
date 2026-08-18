package io.github.vrcmteam.vrcm.presentation.settings

import com.russhwolf.settings.MapSettings
import io.github.vrcmteam.vrcm.presentation.settings.theme.ThemeColor
import io.github.vrcmteam.vrcm.storage.SettingsDao
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsModelTest {
    @Test
    fun clipboardReadingIsEnabledByDefaultAndOptOutPersistsAcrossModelInstances() {
        val settings = MapSettings()
        val firstModel = SettingsModel(SettingsDao(settings), listOf(ThemeColor.Default))

        assertTrue(firstModel.settingsVo.clipboardReadingEnabled)
        firstModel.saveSettings(firstModel.settingsVo.copy(clipboardReadingEnabled = false))

        val restoredModel = SettingsModel(SettingsDao(settings), listOf(ThemeColor.Default))
        assertFalse(restoredModel.settingsVo.clipboardReadingEnabled)
    }
}
