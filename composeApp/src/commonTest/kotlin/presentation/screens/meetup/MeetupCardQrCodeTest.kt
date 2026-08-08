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
}
