package io.github.vrcmteam.vrcm.presentation.screens.meetup.display

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.core.extensions.saveImageBytesToGallery
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.getAppPlatform
import io.github.vrcmteam.vrcm.presentation.compoments.ToastText
import io.github.vrcmteam.vrcm.presentation.compoments.sharedBoundsBy
import io.github.vrcmteam.vrcm.presentation.screens.gallery.editor.PlatformImageCodec
import io.github.vrcmteam.vrcm.presentation.screens.meetup.MeetupCardCanvas
import io.github.vrcmteam.vrcm.presentation.screens.meetup.MeetupCardResizeMode
import io.github.vrcmteam.vrcm.presentation.screens.meetup.MeetupCardScreenModel
import io.github.vrcmteam.vrcm.presentation.screens.meetup.meetupCardSharedKey
import io.github.vrcmteam.vrcm.presentation.screens.meetup.rotateClockwise
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings
import io.github.vrcmteam.vrcm.presentation.supports.AppIcons
import io.github.vrcmteam.vrcm.storage.meetup.MeetupOrientation
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import org.koin.compose.koinInject

/**
 * 身份牌全屏展示页：首帧立即绘制本地内容，刷新只替换对应图层；
 * 控制层默认隐藏，轻点切换显示/隐藏，3 秒无交互淡出；展示期间保持沉浸与常亮。
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun MeetupCardDisplayContent(
    model: MeetupCardScreenModel,
    onBack: () -> Unit,
    onEdit: () -> Unit,
) {
    val state by model.state.collectAsState()
    val scope = rememberCoroutineScope()
    val controls = remember { MeetupControlsState(scope) }
    DisposableEffect(controls) {
        onDispose { controls.close() }
    }
    MeetupPresentationEffect(enabled = true)

    var actionInFlight by remember { mutableStateOf(false) }
    // iOS 应用锁定竖屏，没有真实横屏视口，只能由用户主动把卡片横过来看。
    var forcedLandscape by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    // 保存的回调跑在协程里，文案必须在组合期取出来。
    val saveSuccessMessage = strings.imageSaveSuccess
    val saveFailedMessage = strings.imageSaveFailed
    val saveErrorTemplate = strings.imageSaveError
    val platform = getAppPlatform()
    val imageCodec = koinInject<PlatformImageCodec>()
    // 只录制卡片图层，控制层不会被存进图片。
    val cardLayer = rememberGraphicsLayer()

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(controls) {
                detectTapGestures(onTap = { controls.onTap() })
            }
            .pointerInput(controls) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.type == PointerEventType.Move) {
                            controls.onActivity()
                        }
                    }
                }
            },
    ) {
        // 展示方向跟随真实视口，不依赖编辑器里选择的预览方向。
        val viewportLandscape = maxWidth > maxHeight
        val orientation = if (viewportLandscape || forcedLandscape) {
            MeetupOrientation.Landscape
        } else {
            MeetupOrientation.Portrait
        }
        // 竖屏视口里看横版：整卡按横屏排版后顺时针转 90 度铺满屏幕，
        // 旋转放在共享元素之内，进出编辑页的变换仍按未旋转的全屏边界走。
        val rotateCard = forcedLandscape && !viewportLandscape
        val layoutDirection = LocalLayoutDirection.current
        val safePadding = WindowInsets.safeDrawing.asPaddingValues()
        val viewportInsets = MeetupViewportInsets(
            left = safePadding.calculateLeftPadding(layoutDirection),
            top = safePadding.calculateTopPadding(),
            right = safePadding.calculateRightPadding(layoutDirection),
            bottom = safePadding.calculateBottomPadding(),
        )
        // 强制横版先按横屏坐标排版再顺时针旋转，因此安全区也要逆向映射到源坐标。
        val cardInsets = if (rotateCard) {
            viewportInsets.sourceInsetsBeforeClockwiseRotation()
        } else {
            viewportInsets
        }
        // 整卡与编辑页预览共享同一元素：进出编辑页时在全屏与缩略图之间连续变换。
        MeetupCardCanvas(
            state = state,
            orientation = orientation,
            contentPadding = cardInsets.asPaddingValues(layoutDirection),
            modifier = Modifier
                .fillMaxSize()
                .sharedBoundsBy(
                    key = meetupCardSharedKey(state.ownerUserId),
                    useSuffixKey = false,
                    resizeMode = MeetupCardResizeMode,
                )
                .let { if (rotateCard) it.rotateClockwise() else it }
                // 录制在旋转之内：存出来的是正着的横版图，不是躺倒的截图。
                .drawWithContent {
                    cardLayer.record { this@drawWithContent.drawContent() }
                    drawLayer(cardLayer)
                },
        )
        // 控制层不参与共享变换，否则会跟着整卡一起缩放。
        run {
            val visible by controls.visible.collectAsState()
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .meetupControlPadding(
                            viewportInsets.copy(bottom = 0.dp).withMinimum(
                                left = 12.dp,
                                top = 12.dp,
                                right = 12.dp,
                                bottom = 12.dp,
                            ),
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ControlIconButton(
                        onClick = {
                            if (!actionInFlight) {
                                actionInFlight = true
                                onBack()
                            }
                        },
                    ) {
                        Icon(
                            painter = rememberVectorPainter(AppIcons.ArrowBackIosNew),
                            tint = Color.White,
                            contentDescription = "back",
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = when (orientation) {
                            MeetupOrientation.Portrait -> strings.meetupCardPortrait
                            MeetupOrientation.Landscape -> strings.meetupCardLandscape
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    ControlIconButton(
                        onClick = {
                            if (!actionInFlight) {
                                actionInFlight = true
                                onEdit()
                            }
                        },
                    ) {
                        Icon(
                            painter = rememberVectorPainter(AppIcons.Edit),
                            tint = Color.White,
                            contentDescription = strings.meetupCardEdit,
                        )
                    }
                }
            }
            // 对卡片本身的操作放在底部两角，离拇指近，也不挡住顶部的身份信息。
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .meetupControlPadding(
                            viewportInsets.copy(top = 0.dp).withMinimum(
                                left = 12.dp,
                                top = 12.dp,
                                right = 12.dp,
                                bottom = 12.dp,
                            ),
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 视口本来就是横的时候不需要这个开关。
                    if (!viewportLandscape) {
                        ControlIconButton(
                            onClick = {
                                controls.onActivity()
                                forcedLandscape = !forcedLandscape
                            },
                        ) {
                            Icon(
                                painter = rememberVectorPainter(AppIcons.ScreenRotation),
                                tint = if (forcedLandscape) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    Color.White
                                },
                                contentDescription = strings.meetupCardRotatePreview,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    ControlIconButton(
                        onClick = {
                            if (saving) return@ControlIconButton
                            controls.onActivity()
                            saving = true
                            scope.launch {
                                val saved = runCatching {
                                    val bytes = imageCodec.encodePng(cardLayer.toImageBitmap())
                                    platform.saveImageBytesToGallery(
                                        bytes = bytes,
                                        fileName = meetupCardImageFileName(state.ownerUserId),
                                    )
                                }
                                saved.onSuccess { success ->
                                    SharedFlowCentre.toastText.emit(
                                        if (success) {
                                            ToastText.Success(saveSuccessMessage)
                                        } else {
                                            ToastText.Error(saveFailedMessage)
                                        },
                                    )
                                }.onFailure { error ->
                                    SharedFlowCentre.toastText.emit(
                                        ToastText.Error(
                                            saveErrorTemplate
                                                .replace("%s", error.message.orEmpty()),
                                        ),
                                    )
                                }
                                saving = false
                            }
                        },
                    ) {
                        if (saving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = Color.White,
                            )
                        } else {
                            Icon(
                                painter = rememberVectorPainter(AppIcons.SaveAlt),
                                tint = Color.White,
                                contentDescription = strings.meetupCardSaveImage,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 物理屏幕四边的安全区；避免 RTL 或强制旋转把刘海边距映射到错误方向。 */
@Immutable
private data class MeetupViewportInsets(
    val left: Dp,
    val top: Dp,
    val right: Dp,
    val bottom: Dp,
) {
    fun withMinimum(left: Dp, top: Dp, right: Dp, bottom: Dp) = MeetupViewportInsets(
        left = maxOf(this.left, left),
        top = maxOf(this.top, top),
        right = maxOf(this.right, right),
        bottom = maxOf(this.bottom, bottom),
    )

    /** 源画面顺时针旋转后，四条边分别落到实际视口的上、右、下、左。 */
    fun sourceInsetsBeforeClockwiseRotation() = MeetupViewportInsets(
        left = top,
        top = right,
        right = bottom,
        bottom = left,
    )

    fun asPaddingValues(layoutDirection: LayoutDirection): PaddingValues = PaddingValues(
        start = if (layoutDirection == LayoutDirection.Ltr) left else right,
        top = top,
        end = if (layoutDirection == LayoutDirection.Ltr) right else left,
        bottom = bottom,
    )
}

private fun Modifier.meetupControlPadding(insets: MeetupViewportInsets): Modifier = absolutePadding(
    left = insets.left,
    top = insets.top,
    right = insets.right,
    bottom = insets.bottom,
)

/** 每次保存都带时间戳，避免相册里同名互相覆盖。 */
@OptIn(ExperimentalTime::class)
private fun meetupCardImageFileName(ownerUserId: String): String =
    "VRCM_${ownerUserId}_${Clock.System.now().toEpochMilliseconds()}.png"

@Composable
private fun ControlIconButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier.background(Color.Black.copy(alpha = 0.35f), CircleShape),
    ) {
        IconButton(onClick = onClick, content = content)
    }
}
