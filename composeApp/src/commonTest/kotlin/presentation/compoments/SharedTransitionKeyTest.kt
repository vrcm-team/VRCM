package io.github.vrcmteam.vrcm.presentation.compoments

import io.github.vrcmteam.vrcm.presentation.screens.auth.StartupAnimeScreen
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

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

    @Test
    fun appNavEntryUsesBundleSaveableContentKey() {
        val entry = createAppNavEntry(
            route = StartupAnimeScreen,
            metadata = emptyMap(),
        ) {}

        assertIs<String>(entry.contentKey)
        assertEquals(StartupAnimeScreen.key, entry.contentKey)
    }
}
