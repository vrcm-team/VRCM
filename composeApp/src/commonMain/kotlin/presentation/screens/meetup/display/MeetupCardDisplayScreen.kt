package io.github.vrcmteam.vrcm.presentation.screens.meetup.display

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.presentation.extensions.getInsetPadding
import io.github.vrcmteam.vrcm.presentation.screens.meetup.MeetupCardCanvas
import io.github.vrcmteam.vrcm.presentation.screens.meetup.MeetupCardScreenModel
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings
import io.github.vrcmteam.vrcm.presentation.supports.AppIcons
import io.github.vrcmteam.vrcm.storage.meetup.MeetupOrientation
import androidx.compose.foundation.layout.WindowInsets

/**
 * 身份牌全屏展示页：首帧立即绘制本地内容，刷新只替换对应图层；
 * 控制层默认隐藏，轻点显示，3 秒无交互淡出；展示期间保持沉浸与常亮。
 */
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

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        when (event.type) {
                            PointerEventType.Press, PointerEventType.Move -> controls.onInteraction()
                            else -> Unit
                        }
                    }
                }
            },
    ) {
        // 展示方向跟随真实视口，不依赖编辑器里选择的预览方向。
        val orientation = if (maxWidth > maxHeight) {
            MeetupOrientation.Landscape
        } else {
            MeetupOrientation.Portrait
        }
        MeetupCardCanvas(
            state = state,
            orientation = orientation,
            modifier = Modifier.fillMaxSize(),
        ) {
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
                        .padding(top = getInsetPadding(WindowInsets::getTop))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
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
        }
    }
}

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
