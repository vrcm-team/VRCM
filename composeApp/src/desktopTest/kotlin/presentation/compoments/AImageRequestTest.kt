package io.github.vrcmteam.vrcm.presentation.compoments

import coil3.PlatformContext
import coil3.memory.MemoryCache
import kotlin.test.Test
import kotlin.test.assertEquals

class AImageRequestTest {
    @Test
    fun originalImageRequestUsesSourceImageAsCachedPlaceholder() {
        val request = createAImageRequest(
            platformContext = PlatformContext.INSTANCE,
            imageUrl = "https://example.com/world.png",
            loadOriginalSize = true,
            cachedPlaceholderKey = "https://example.com/world-preview.png",
        )

        assertEquals(
            MemoryCache.Key("https://example.com/world-preview.png"),
            request.placeholderMemoryCacheKey,
        )
    }
}
