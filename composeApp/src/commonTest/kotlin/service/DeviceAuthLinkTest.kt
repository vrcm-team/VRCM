package io.github.vrcmteam.vrcm.service

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DeviceAuthLinkTest {
    @Test
    fun readsCodeFromOfficialDeviceLinksRegardlessOfParameterOrder() {
        assertEquals("F36XWSHN", parseDeviceAuthCode("https://vrchat.com/home/device?code=F36XWSHN&source=app"))
        assertEquals("U4RQANX4", parseDeviceAuthCode("https://www.vrchat.com/home/device?source=app&code=U4RQANX4"))
    }

    @Test
    fun rejectsLookalikesOtherRoutesAndAmbiguousOrMalformedCodes() {
        listOf(
            "http://vrchat.com/home/device?code=F36XWSHN",
            "https://vrchat.com.evil.example/home/device?code=F36XWSHN",
            "https://vrchat.com@evil.example/home/device?code=F36XWSHN",
            "https://user@vrchat.com/home/device?code=F36XWSHN",
            "https://vrchat.com:8443/home/device?code=F36XWSHN",
            "https://vrchat.com/home/user/usr_abc?code=F36XWSHN",
            "https://vrchat.com/home/device/extra?code=F36XWSHN",
            "https://vrchat.com/home/device#code=F36XWSHN",
            "https://vrchat.com/home/device?code=F36XWSHN&code=U4RQANX4",
            "https://vrchat.com/home/device?code=",
            "https://vrchat.com/home/device?code=F36XWSHN%0A",
            "https://vrchat.com/home/device?code=F36XWSH",
            "https://vrchat.com/home/device?code=f36xwshn",
        ).forEach { assertNull(parseDeviceAuthCode(it), it) }
    }
}
