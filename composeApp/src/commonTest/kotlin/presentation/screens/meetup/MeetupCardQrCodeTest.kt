package io.github.vrcmteam.vrcm.presentation.screens.meetup

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MeetupCardQrCodeTest {
    @Test
    fun qrPayloadAlwaysUsesPublicVrchatProfile() {
        assertEquals(
            "https://vrchat.com/home/user/usr_abc-123",
            meetupCardProfileUrl("usr_abc-123"),
        )
    }

    @Test
    fun qrPayloadRejectsAnythingThatIsNotAUserId() {
        assertFailsWith<IllegalArgumentException> { meetupCardProfileUrl("https://other") }
        assertFailsWith<IllegalArgumentException> { meetupCardProfileUrl("") }
        assertFailsWith<IllegalArgumentException> {
            meetupCardProfileUrl("usr_abc?query=https://evil")
        }
    }

    @Test
    fun profileLinkPayloadOnlyAcceptsWebUrls() {
        assertEquals(
            "https://x.com/someone",
            meetupCardProfileLinkUrl(" https://x.com/someone "),
        )
        // 资料链接字段里可能出现任意文本，能编成二维码的只有 http(s)。
        assertFailsWith<IllegalArgumentException> {
            meetupCardProfileLinkUrl("javascript:alert(1)")
        }
        assertFailsWith<IllegalArgumentException> { meetupCardProfileLinkUrl("") }
    }
}
