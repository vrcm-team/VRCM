package io.github.vrcmteam.vrcm.presentation.screens.gallery.editor

import io.github.vrcmteam.vrcm.presentation.settings.locale.LocaleStringsEn
import kotlin.test.Test
import kotlin.test.assertEquals

class PrintImageFailureLocalizationTest {
    @Test
    fun desktopRegionDecodeUnavailableUsesPlatformCapabilityMessage() {
        assertEquals(
            LocaleStringsEn.printEditorDesktopRegionDecodeUnavailable,
            PrintImageFailure.DesktopRegionDecodeUnavailable.localizedMessage(LocaleStringsEn),
        )
    }
}
