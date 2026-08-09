package io.github.vrcmteam.vrcm.presentation.screens.meetup.editor

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.presentation.compoments.ToastText
import io.github.vrcmteam.vrcm.presentation.screens.gallery.editor.CropTransform
import io.github.vrcmteam.vrcm.presentation.screens.gallery.editor.CropTransformCalculator
import io.github.vrcmteam.vrcm.presentation.screens.gallery.editor.ImageSize
import io.github.vrcmteam.vrcm.presentation.screens.meetup.meetupCardAspectRatio
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings
import io.github.vrcmteam.vrcm.storage.meetup.MeetupCrop
import io.github.vrcmteam.vrcm.storage.meetup.MeetupOrientation
import org.koin.compose.koinInject
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 双方向裁剪对话框：分段控件切换竖屏/横屏，各自读写独立裁剪；
 * 手势期间只更新会话草稿，确认时由调用方提交 replacePhoto，
 * 失败保留对话框与旧配置。
 */
@Composable
fun MeetupCardCropDialog(
    sessionId: String,
    savingPhoto: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    orientations: List<MeetupOrientation> = MeetupOrientation.entries,
) {
    val photoSessions: MeetupPhotoSessionStore = koinInject()
    val calculator: CropTransformCalculator = koinInject()
    val session = remember(sessionId) { photoSessions.get(sessionId) }
    val locale = strings

    if (session == null) {
        LaunchedEffect(sessionId) {
            SharedFlowCentre.toastText.emit(ToastText.Error(locale.meetupCardSessionExpired))
            onDismiss()
        }
        return
    }

    var orientation by remember(sessionId) { mutableStateOf(orientations.first()) }
    val crop by session.crop(orientation).collectAsState()
    val source = session.prepared.originalSize

    Dialog(
        onDismissRequest = { if (!savingPhoto) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            // 宽屏上限制对话框尺寸，避免铺满整个桌面窗口。
            modifier = Modifier
                .sizeIn(maxWidth = 720.dp, maxHeight = 960.dp)
                .fillMaxSize(0.96f),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = locale.meetupCardCropTitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (orientations.size > 1) {
                    SingleChoiceSegmentedButtonRow {
                        orientations.forEachIndexed { index, entry ->
                            SegmentedButton(
                                selected = orientation == entry,
                                onClick = { orientation = entry },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = orientations.size,
                                ),
                            ) {
                                Text(
                                    text = when (entry) {
                                        MeetupOrientation.Portrait -> locale.meetupCardPortrait
                                        MeetupOrientation.Landscape -> locale.meetupCardLandscape
                                    },
                                )
                            }
                        }
                    }
                }
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    MeetupCropPreview(
                        session = session,
                        orientation = orientation,
                        crop = crop,
                        source = source,
                        calculator = calculator,
                        enabled = !savingPhoto,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss, enabled = !savingPhoto) {
                        Text(strings.cancel)
                    }
                    Button(
                        onClick = onConfirm,
                        enabled = !savingPhoto,
                        modifier = Modifier.padding(start = 8.dp),
                    ) {
                        if (savingPhoto) {
                            CircularProgressIndicator(
                                modifier = Modifier.padding(end = 8.dp).aspectRatio(1f),
                                strokeWidth = 2.dp,
                            )
                        }
                        Text(strings.confirm)
                    }
                }
            }
        }
    }
}

@Composable
private fun MeetupCropPreview(
    session: MeetupPhotoSession,
    orientation: MeetupOrientation,
    crop: MeetupCrop,
    source: ImageSize,
    calculator: CropTransformCalculator,
    enabled: Boolean,
) {
    val cropMapper = remember(calculator) { MeetupCropMapper(calculator) }
    BoxWithConstraints(
        // 裁剪视口跟随真实窗口纵横比，与预览/展示所见一致。
        modifier = Modifier
            .aspectRatio(meetupCardAspectRatio(orientation))
            .clipToBounds()
            .background(Color.Black),
    ) {
        val viewportWidth = constraints.maxWidth
        val viewportHeight = constraints.maxHeight
        if (viewportWidth <= 0 || viewportHeight <= 0) return@BoxWithConstraints
        val viewport = ImageSize(viewportWidth, viewportHeight)
        val reference = orientation.referenceViewport
        // 会话中的裁剪以参考视口为基准；编辑与渲染都换算到设备视口。
        val deviceCrop = cropMapper.derive(source, reference, viewport, crop)
        val geometry = calculator.geometry(
            source = source,
            viewport = viewport,
            transform = deviceCrop.toTransform(),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(session.id, orientation, enabled) {
                    if (!enabled) return@pointerInput
                    detectTransformGestures { _, pan, zoom, _ ->
                        val cover = calculator.zoomLimits(source, viewport, 0).cover
                        val currentDevice = cropMapper.derive(
                            source = source,
                            fromViewport = reference,
                            toViewport = viewport,
                            from = session.crop(orientation).value,
                        )
                        val transformed = calculator.transform(
                            source = source,
                            viewport = viewport,
                            current = currentDevice.toTransform(),
                            panX = pan.x,
                            panY = pan.y,
                            zoomChange = zoom,
                        )
                        val editedDevice = MeetupCrop(
                            centerOffsetX = transformed.centerOffsetX,
                            centerOffsetY = transformed.centerOffsetY,
                            // 身份卡照片始终 cover 视口，不允许缩出留边。
                            zoom = max(transformed.zoom, cover),
                        )
                        // 存储换算回参考视口基准，跨设备与展示端保持同一语义。
                        session.updateCrop(
                            orientation,
                            cropMapper.derive(source, viewport, reference, editedDevice),
                        )
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            // 与展示端一致地等比补足，裁剪预览不会出现展示时没有的空白边。
            val rawScaleX = geometry.imageWidth / viewportWidth
            val rawScaleY = geometry.imageHeight / viewportHeight
            val coverFix = maxOf(1f / rawScaleX, 1f / rawScaleY, 1f)
            Image(
                bitmap = session.prepared.preview,
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

private fun MeetupCrop.toTransform() = CropTransform(
    centerOffsetX = centerOffsetX,
    centerOffsetY = centerOffsetY,
    zoom = zoom,
)
