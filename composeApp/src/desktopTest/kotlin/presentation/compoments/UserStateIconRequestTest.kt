package io.github.vrcmteam.vrcm.presentation.compoments

import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.intercept.Interceptor
import coil3.memory.MemoryCache
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import org.koin.compose.KoinApplication
import org.koin.dsl.module
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class UserStateIconRequestTest {
    @Test
    fun sourceAvatarCacheIsUsedWhileTargetAvatarLoads() = runComposeUiTest {
        val requests = CopyOnWriteArrayList<ImageRequest>()
        val platformContext = PlatformContext.INSTANCE
        val imageLoader = ImageLoader.Builder(platformContext)
            .components {
                add(
                    Interceptor { chain ->
                        requests += chain.request
                        ErrorResult(
                            image = null,
                            request = chain.request,
                            throwable = IllegalStateException("Request captured by test"),
                        )
                    },
                )
            }
            .build()

        setContent {
            KoinApplication(
                application = {
                    modules(
                        module {
                            single<PlatformContext> { platformContext }
                            single<ImageLoader> { imageLoader }
                        },
                    )
                },
            ) {
                MaterialTheme {
                    UserStateIcon(
                        modifier = Modifier.size(54.dp),
                        iconUrl = TargetAvatarUrl,
                        cachedPlaceholderKey = SourceAvatarUrl,
                    )
                }
            }
        }

        waitUntil(timeoutMillis = 5_000) {
            requests.any { it.data == TargetAvatarUrl }
        }
        val avatarRequest = requests.first { it.data == TargetAvatarUrl }
        assertEquals(
            MemoryCache.Key(SourceAvatarUrl),
            avatarRequest.placeholderMemoryCacheKey,
        )
    }

    private companion object {
        const val TargetAvatarUrl = "https://example.com/home-avatar.png"
        const val SourceAvatarUrl = "https://example.com/auth-avatar.png"
    }
}
