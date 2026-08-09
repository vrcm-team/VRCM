package io.github.vrcmteam.vrcm.core.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AppDeepLinksTest {
    @Test
    fun recognizesVrchatProfileAndCustomSchemeLinks() {
        assertEquals(
            AppDeepLink.UserProfile("usr_abc-123"),
            AppDeepLinks.parse("https://vrchat.com/home/user/usr_abc-123"),
        )
        assertEquals(
            AppDeepLink.UserProfile("usr_abc"),
            AppDeepLinks.parse("https://www.vrchat.com/home/user/usr_abc?utm=camera"),
        )
        assertEquals(
            AppDeepLink.UserProfile("usr_abc"),
            AppDeepLinks.parse("vrcm://user/usr_abc"),
        )
    }

    @Test
    fun rejectsUnrelatedOrMalformedLinks() {
        assertNull(AppDeepLinks.parse("https://vrchat.com/home/world/wrld_123"))
        assertNull(AppDeepLinks.parse("https://evil.com/home/user/usr_abc"))
        assertNull(AppDeepLinks.parse("http://vrchat.com/home/user/usr_abc"))
        assertNull(AppDeepLinks.parse("vrcm://user/"))
        assertNull(AppDeepLinks.parse("not a url"))
    }

    @Test
    fun pendingLinkIsConsumedOnceAndNewerLinkSurvives() {
        AppDeepLinks.offerUrl("https://vrchat.com/home/user/usr_first")
        val first = AppDeepLinks.pending.value
        assertEquals(AppDeepLink.UserProfile("usr_first"), first)

        AppDeepLinks.offerUrl("vrcm://user/usr_second")
        AppDeepLinks.consume(first as AppDeepLink)
        assertEquals(AppDeepLink.UserProfile("usr_second"), AppDeepLinks.pending.value)

        AppDeepLinks.consume(AppDeepLink.UserProfile("usr_second"))
        assertNull(AppDeepLinks.pending.value)
    }
}
