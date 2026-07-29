package io.github.vrcmteam.vrcm.presentation.compoments

import kotlin.test.Test
import kotlin.test.assertEquals

class SharedTransitionKeyTest {
    @Test
    fun nonBlankSuffixDistinguishesTheSharedContentKey() {
        assertEquals(
            "avtr_exampleAvatarImage:Search",
            sharedContentKey(
                key = "avtr_exampleAvatarImage",
                suffixKey = "Search",
                useSuffixKey = true,
            ),
        )
    }

    @Test
    fun disabledSuffixKeepsTheBaseSharedContentKey() {
        assertEquals(
            "avtr_exampleAvatarImage",
            sharedContentKey(
                key = "avtr_exampleAvatarImage",
                suffixKey = "Search",
                useSuffixKey = false,
            ),
        )
    }
}
