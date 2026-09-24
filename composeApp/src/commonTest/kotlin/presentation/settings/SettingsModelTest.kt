package io.github.vrcmteam.vrcm.presentation.settings

import com.russhwolf.settings.MapSettings
import io.github.vrcmteam.vrcm.presentation.settings.theme.ThemeColor
import io.github.vrcmteam.vrcm.storage.SettingsDao
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsModelTest {
    @Test
    fun clipboardReadingIsDisabledByDefaultAndOptInPersistsAcrossModelInstances() {
        val settings = MapSettings()
        val firstModel = SettingsModel(SettingsDao(settings), listOf(ThemeColor.Default))

        assertFalse(firstModel.settingsVo.clipboardReadingEnabled)
        firstModel.saveSettings(firstModel.settingsVo.copy(clipboardReadingEnabled = true))

        val restoredModel = SettingsModel(SettingsDao(settings), listOf(ThemeColor.Default))
        assertTrue(restoredModel.settingsVo.clipboardReadingEnabled)
    }

    @Test
    fun clipboardReadingPersistedByDefaultInOlderVersionsStaysDisabledAfterUpgrade() {
        // 旧版默认开启，保存任意设置时会把这个默认值一并写入；升级后不能因此继续在每次回到前台时读取剪贴板
        val settings = MapSettings("vrcm.clipboardReadingEnabled" to true, "vrcm.themeColor" to "Pink")

        val model = SettingsModel(SettingsDao(settings), listOf(ThemeColor.Default))

        assertFalse(model.settingsVo.clipboardReadingEnabled)
    }
}
