package io.github.vrcmteam.vrcm.presentation.compoments

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.presentation.designsystem.AppIcon
import io.github.vrcmteam.vrcm.presentation.designsystem.AppSurface
import io.github.vrcmteam.vrcm.presentation.designsystem.AppTheme
import io.github.vrcmteam.vrcm.presentation.supports.AppIcons

@Composable
fun GroupIcon(
    iconUrl: String?,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
) {
    if (iconUrl.isNullOrBlank()) {
        AppSurface(
            modifier = modifier.size(size),
            shape = CircleShape,
            color = AppTheme.colors.fill
        ) {
            Box(contentAlignment = Alignment.Center) {
                AppIcon(
                    imageVector = AppIcons.Groups,
                    contentDescription = "GroupIcon",
                    tint = AppTheme.colors.tint
                )
            }
        }
        return
    }
    AImage(
        modifier = modifier
            .size(size)
            .clip(CircleShape),
        imageData = iconUrl,
        contentDescription = "GroupIcon"
    )
}
