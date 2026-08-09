package io.github.vrcmteam.vrcm.presentation.screens.meetup

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import io.github.vrcmteam.vrcm.presentation.screens.gallery.editor.CropTransform
import io.github.vrcmteam.vrcm.presentation.screens.gallery.editor.CropTransformCalculator
import io.github.vrcmteam.vrcm.presentation.screens.gallery.editor.ImageSize
import io.github.vrcmteam.vrcm.presentation.screens.meetup.editor.MeetupCropMapper
import io.github.vrcmteam.vrcm.presentation.screens.meetup.editor.referenceViewport
import io.github.vrcmteam.vrcm.storage.meetup.MeetupCrop
import io.github.vrcmteam.vrcm.storage.meetup.MeetupOrientation
import org.koin.compose.koinInject

/**
 * 按标准化裁剪参数实时渲染身份卡照片。裁剪以方向参考视口（9:16 / 16:9）
 * 为归一化基准存储，渲染时按实际视口换算：保留焦点、保证 cover，
 * 真机纵横比（如 19.5:9）与参考视口不同也不会出现留边或构图偏移。
 * 照片缺失或加载失败时静默显示下层主题背景，不改变文字布局。
 */
@Composable
fun MeetupCardPhoto(
    photoModel: String?,
    photoSize: ImageSize?,
    crop: MeetupCrop,
    orientation: MeetupOrientation,
    calculator: CropTransformCalculator,
    modifier: Modifier = Modifier,
) {
    if (photoModel == null) return
    val cropMapper = remember(calculator) { MeetupCropMapper(calculator) }
    val imageLoader: ImageLoader = koinInject()
    val source = photoSize?.takeIf { it.width > 0 && it.height > 0 }
    if (source == null) {
        // 原图尺寸未知（历史配置或后台刷新下载的资料背景）时不做自定义裁剪：
        // Coil 的固有尺寸取决于布局大小，用它算几何会让预览与展示得到不同结果，
        // 甚至出现空白边。这里直接按 Crop 铺满，保证两端一致且始终无空白边。
        AsyncImage(
            model = photoModel,
            contentDescription = null,
            imageLoader = imageLoader,
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
        return
    }
    BoxWithConstraints(modifier = modifier.clipToBounds()) {
        val painter = rememberAsyncImagePainter(model = photoModel, imageLoader = imageLoader)
        val painterState by painter.state.collectAsState()
        if (painterState !is AsyncImagePainter.State.Success) return@BoxWithConstraints

        val viewportWidth = constraints.maxWidth
        val viewportHeight = constraints.maxHeight
        if (viewportWidth <= 0 || viewportHeight <= 0) return@BoxWithConstraints

        val viewport = ImageSize(viewportWidth, viewportHeight)
        val derivedCrop = cropMapper.derive(
            source = source,
            fromViewport = orientation.referenceViewport,
            toViewport = viewport,
            from = crop,
        )
        val geometry = calculator.geometry(
            source = source,
            viewport = viewport,
            transform = CropTransform(
                centerOffsetX = derivedCrop.centerOffsetX,
                centerOffsetY = derivedCrop.centerOffsetY,
                zoom = derivedCrop.zoom,
            ),
        )
        // 等比补足到至少铺满视口：任何数值误差都不该让照片露出空白边。
        val rawScaleX = geometry.imageWidth / viewportWidth
        val rawScaleY = geometry.imageHeight / viewportHeight
        val coverFix = maxOf(1f / rawScaleX, 1f / rawScaleY, 1f)
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Image(
                painter = painter,
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = rawScaleX * coverFix
                        scaleY = rawScaleY * coverFix
                        translationX = geometry.translationX
                        translationY = geometry.translationY
                    },
            )
        }
    }
}
