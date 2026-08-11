package presentation.compoments

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.presentation.extensions.getInsetPadding
import io.github.vrcmteam.vrcm.presentation.supports.AppIcons

internal data class TopMenuActionColors(
    val container: Color,
    val content: Color,
)

internal fun topMenuActionColors(
    ratio: Float,
    onPrimary: Color,
    primary: Color,
    primaryContainer: Color,
): TopMenuActionColors {
    val clampedRatio = ratio.coerceIn(0f, 1f)
    return TopMenuActionColors(
        container = primaryContainer.copy(alpha = 0.6f * clampedRatio),
        content = lerp(onPrimary, primary, 1f - clampedRatio),
    )
}

@Composable
fun TopMenuBar(
    topBarHeight: Dp = 64.dp,
    sysTopPadding: Dp = getInsetPadding(WindowInsets::getTop),
    offsetDp: Dp,
    ratio: Float,
    color: Color = MaterialTheme.colorScheme.onPrimary,
    onReturn: () -> Unit,
    onMenu: (() -> Unit)?,
    actions: @Composable RowScope.(IconButtonColors) -> Unit = {},
) {
    // image上滑反比例
    val inverseRatio = 1 - ratio
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .height(topBarHeight + sysTopPadding)
                .offset(y = offsetDp)
                .background(
                    color.copy(alpha = inverseRatio), MaterialTheme.shapes.medium.copy(
                        topStart = CornerSize(0.dp),
                        topEnd = CornerSize(0.dp)
                    )
                )
                .padding(top = sysTopPadding),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val actionColors = topMenuActionColors(
                ratio = ratio,
                onPrimary = MaterialTheme.colorScheme.onPrimary,
                primary = MaterialTheme.colorScheme.primary,
                primaryContainer = MaterialTheme.colorScheme.primaryContainer,
            )
            val iconButtonColors = IconButtonColors(
                containerColor = actionColors.container,
                contentColor = actionColors.content,
                disabledContainerColor = actionColors.container,
                disabledContentColor = actionColors.content,
            )
            IconButton(
                modifier = Modifier
                    .padding(horizontal = 10.dp),
                colors = iconButtonColors,
                onClick = onReturn
            ) {
                Icon(
                    imageVector = AppIcons.ArrowBackIosNew,
                    tint = actionColors.content,
                    contentDescription = "ReturnIcon"
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Row(
                modifier = Modifier.padding(end = if (onMenu == null) 10.dp else 0.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                actions(iconButtonColors)
            }
            onMenu?.let {
                IconButton(
                    modifier = Modifier
                        .padding(horizontal = 10.dp),
                    colors = iconButtonColors,
                    onClick = it
                    ) {
                        Icon(
                            imageVector = AppIcons.Menu,
                            tint = actionColors.content,
                            contentDescription = "MenuIcon"
                        )
                }
            }
        }
    }
}
