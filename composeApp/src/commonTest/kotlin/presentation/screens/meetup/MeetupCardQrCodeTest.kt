package io.github.vrcmteam.vrcm.presentation.screens.meetup

import io.github.vrcmteam.vrcm.storage.meetup.MeetupQrLinkType
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
    fun deepLinkTypeUsesVrcmScheme() {
        assertEquals(
            "vrcm://user/usr_abc-123",
            meetupCardProfileUrl("usr_abc-123", MeetupQrLinkType.VrcmDeepLink),
        )
        assertFailsWith<IllegalArgumentException> {
            meetupCardProfileUrl("https://other", MeetupQrLinkType.VrcmDeepLink)
        }
    }

    @Test
    fun qrPayloadRejectsAnythingThatIsNotAUserId() {
        assertFailsWith<IllegalArgumentException> { meetupCardProfileUrl("https://other") }
        assertFailsWith<IllegalArgumentException> { meetupCardProfileUrl("") }
        assertFailsWith<IllegalArgumentException> {
            meetupCardProfileUrl("usr_abc?query=https://evil")
        }
    }
}
