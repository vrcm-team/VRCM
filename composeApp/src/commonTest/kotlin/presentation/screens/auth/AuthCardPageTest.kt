package io.github.vrcmteam.vrcm.presentation.screens.auth

import io.github.vrcmteam.vrcm.presentation.screens.auth.data.AuthCardPage
import io.github.vrcmteam.vrcm.presentation.screens.auth.data.normalizeVerifyCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AuthCardPageTest {
    @Test
    fun recoveryCodeAcceptsCopiedFormatsAndIsSubmittedAsHyphenatedPair() {
        val page = AuthCardPage.TFACode

        assertEquals("ab12-cd34", page.normalizeVerifyCode("ab12-cd34"))
        assertEquals("ab12-cd34", page.normalizeVerifyCode(" ab12cd34 "))
        assertEquals("ab12-cd34", page.normalizeVerifyCode("ab12 cd34"))

        assertNull(page.normalizeVerifyCode("ab12-cd3"))
        assertNull(page.normalizeVerifyCode("ab12-cd345"))
        assertNull(page.normalizeVerifyCode("ab12_cd34"))
    }
}
