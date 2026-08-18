package io.github.vrcmteam.vrcm.presentation.screens.gallery

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.intercept.Interceptor
import coil3.request.ErrorResult
import io.github.vrcmteam.vrcm.presentation.compoments.LocalSharedTransitionDialogScope
import org.koin.compose.KoinApplication
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class, ExperimentalSharedTransitionApi::class)
class PrintFallbackMetadataLayoutTest {
    @Test
    fun narrowCanvasWithLargeFontKeepsBothFieldsInsideBottomBand() = runComposeUiTest {
        setContent {
            MetadataTestHost(fontScale = 2f) {
                Box(
                    modifier = Modifier
                        .size(CanvasWidth, CanvasHeight)
                        .testTag(CanvasTag),
                ) {
                    AnimatedVisibility(visible = true) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            ZoomableImage(
                                id = "prnt_layout",
                                imageUrl = "",
                                animatedVisibilityScope = this@AnimatedVisibility,
                                onDismiss = {},
                                printLayoutEnabled = true,
                                printAuthorName = Author,
                                printTimestamp = LongRawTimestamp,
                            )
                        }
                    }
                }
            }
        }

        waitForIdle()
        val canvas = onNodeWithTag(CanvasTag).getBoundsInRoot()
        val author = onNodeWithText(Author).getBoundsInRoot()
        val timestamp = onNodeWithText(LongRawTimestamp).getBoundsInRoot()
        val photo = PrintDisplayGeometry.targetPhotoRect(
            Rect(
                0f,
                0f,
                (canvas.right - canvas.left).value,
                (canvas.bottom - canvas.top).value,
            )
        )
        val bottomBandTop = canvas.top + photo.bottom.dp

        assertTrue(author.right - author.left > 0.dp, "拍摄者不能被时间压成零宽")
        assertTrue(timestamp.right - timestamp.left > 0.dp, "时间必须保留独立的可见区域")
        assertTrue(author.right <= timestamp.left, "拍摄者与时间不能重叠")
        assertTrue(author.top >= bottomBandTop, "拍摄者必须位于照片下方白边内")
        assertTrue(timestamp.top >= bottomBandTop, "时间必须位于照片下方白边内")
        assertTrue(author.bottom <= canvas.bottom, "拍摄者不能越出画布")
        assertTrue(timestamp.bottom <= canvas.bottom, "时间不能越出画布")
    }

    @Composable
    private fun MetadataTestHost(
        fontScale: Float,
        content: @Composable () -> Unit,
    ) {
        val imageLoader = ImageLoader.Builder(PlatformContext.INSTANCE)
            .components {
                add(
                    Interceptor { chain ->
                        ErrorResult(
                            image = null,
                            request = chain.request,
                            throwable = IllegalStateException("No network in layout tests"),
                        )
                    }
                )
            }
            .build()
        KoinApplication(
            application = {
                modules(
                    module {
                        single<PlatformContext> { PlatformContext.INSTANCE }
                        single<ImageLoader> { imageLoader }
                    }
                )
            },
        ) {
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(base.density, fontScale),
            ) {
                MaterialTheme {
                    SharedTransitionLayout {
                        CompositionLocalProvider(
                            LocalSharedTransitionDialogScope provides this@SharedTransitionLayout,
                            content = content,
                        )
                    }
                }
            }
        }
    }

    private companion object {
        val CanvasWidth = 240.dp
        val CanvasHeight = 168.75.dp
        const val CanvasTag = "print_metadata_canvas"
        const val Author = "Photographer With A Very Long Display Name"
        const val LongRawTimestamp =
            "not-an-instant-with-a-very-long-value-that-must-not-starve-the-author"
    }
}
