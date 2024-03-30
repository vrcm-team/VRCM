package io.github.vrcmteam.vrcm.presentation.compoments

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import org.koin.compose.koinInject

@Composable
fun AImage(
    modifier: Modifier = Modifier,
    imageData: Any?,
    color: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    contentDescription: String? = null,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val placeholder = remember(color) { ColorPainter(color) }
    val imageLoader: ImageLoader = koinInject()
    val platformContext = koinInject<PlatformContext>()
    val imageRequest: Any? =
        when (imageData) {
            is String -> remember(imageData){ ImageRequest.Builder(platformContext)
                    .data(imageData)
                    .crossfade(600)
                    .build()
            }
            is ImageRequest -> imageData
            else -> imageData
        }
    AsyncImage(
        modifier = modifier,
        model = imageRequest,
        contentDescription = contentDescription,
        imageLoader = imageLoader,
        placeholder = placeholder,
        error = placeholder,
        contentScale = contentScale,
    )
}

