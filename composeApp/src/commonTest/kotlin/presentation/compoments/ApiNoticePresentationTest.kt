package io.github.vrcmteam.vrcm.presentation.compoments

import io.github.vrcmteam.vrcm.network.supports.ApiNotice
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ApiNoticePresentationTest {
    @Test
    fun activeApiNoticeSuppressesRegularToastUntilConsumed() {
        assertFalse(shouldAcceptRegularToast(ApiNotice.RateLimited))
        assertTrue(shouldAcceptRegularToast(null))
    }
}
