package io.github.vrcmteam.vrcm.presentation.compoments

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.awtEventOrNull
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import io.github.vrcmteam.vrcm.core.shared.AppConst.APP_NAME
import io.github.vrcmteam.vrcm.presentation.designsystem.AppDivider
import io.github.vrcmteam.vrcm.presentation.designsystem.AppIcon
import io.github.vrcmteam.vrcm.presentation.designsystem.AppText
import io.github.vrcmteam.vrcm.presentation.designsystem.AppTheme
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings
import io.github.vrcmteam.vrcm.presentation.supports.AppIcons
import org.jetbrains.compose.resources.painterResource
import vrcm.composeapp.generated.resources.Res
import vrcm.composeapp.generated.resources.logo
import java.awt.event.MouseEvent

private val TitleBarHeight = 44.dp
private val WindowControlSize = 44.dp

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun FrameWindowScope.DesktopWindowTitleBar(
    windowState: WindowState,
    onCloseRequest: () -> Unit,
) {
    val locale = strings
    val isMaximized = windowState.placement == WindowPlacement.Maximized
    val toggleMaximized = {
        windowState.placement = if (isMaximized) {
            WindowPlacement.Floating
        } else {
            WindowPlacement.Maximized
        }
    }

    Box(
        Modifier
            .fillMaxWidth()
            .height(TitleBarHeight)
            .background(AppTheme.colors.groupedBackground)
    ) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            WindowDraggableArea(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .onPointerEvent(PointerEventType.Release) { event ->
                        val awtEvent = event.awtEventOrNull
                        if (awtEvent?.button == MouseEvent.BUTTON1 && awtEvent.clickCount == 2) {
                            toggleMaximized()
                        }
                    }
            ) {
                Row(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Image(
                        painter = painterResource(Res.drawable.logo),
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                    )
                    AppText(
                        text = APP_NAME,
                        modifier = Modifier.padding(start = 9.dp),
                        color = AppTheme.colors.label,
                        style = AppTheme.type.subheadlineEmphasized.copy(
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.sp,
                        ),
                    )
                }
            }
            WindowControlButton(
                icon = AppIcons.WindowMinimize,
                label = locale.desktopWindowMinimize,
                onClick = { windowState.isMinimized = true },
            )
            WindowControlButton(
                icon = if (isMaximized) AppIcons.WindowRestore else AppIcons.WindowMaximize,
                label = if (isMaximized) locale.desktopWindowRestore else locale.desktopWindowMaximize,
                onClick = toggleMaximized,
            )
            WindowControlButton(
                icon = AppIcons.Close,
                label = locale.desktopWindowClose,
                isCloseAction = true,
                onClick = onCloseRequest,
            )
        }
        AppDivider(
            modifier = Modifier.align(Alignment.BottomCenter),
            color = AppTheme.colors.separator.copy(alpha = 0.6f),
        )
    }
}

@Composable
private fun WindowControlButton(
    icon: ImageVector,
    label: String,
    isCloseAction: Boolean = false,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val containerColor = when {
        !isHovered -> Color.Transparent
        isCloseAction -> AppTheme.colors.destructiveSoft
        else -> AppTheme.colors.fill
    }
    val contentColor = when {
        isHovered && isCloseAction -> AppTheme.colors.onDestructiveSoft
        isHovered -> AppTheme.colors.label
        else -> AppTheme.colors.secondaryLabel
    }

    Box(
        modifier = Modifier
            .size(WindowControlSize)
            .background(containerColor)
            .hoverable(interactionSource)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        AppIcon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(18.dp),
            tint = contentColor,
        )
    }
}
