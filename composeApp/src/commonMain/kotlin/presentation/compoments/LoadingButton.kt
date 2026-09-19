package io.github.vrcmteam.vrcm.presentation.compoments

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.presentation.designsystem.AppActivityIndicator
import io.github.vrcmteam.vrcm.presentation.designsystem.AppButton
import io.github.vrcmteam.vrcm.presentation.designsystem.AppButtonRole
import io.github.vrcmteam.vrcm.presentation.designsystem.AppButtonSize
import io.github.vrcmteam.vrcm.presentation.designsystem.AppButtonStyle
import io.github.vrcmteam.vrcm.presentation.designsystem.AppText
import io.github.vrcmteam.vrcm.presentation.designsystem.LocalContentColor

@Composable
fun LoadingButton(
    modifier: Modifier = Modifier,
    text: String,
    enabled: Boolean = true,
    isLoading: Boolean,
    style: AppButtonStyle = AppButtonStyle.Prominent,
    role: AppButtonRole = AppButtonRole.Default,
    onClick: () -> Unit
) {
    AppButton(
        modifier = modifier,
        onClick = onClick,
        enabled = enabled,
        style = style,
        role = role,
        size = AppButtonSize.Large,
    ) {
        if (isLoading) {
            // 转圈跟随按钮的前景色，换样式 / 角色时不用另配
            AppActivityIndicator(
                modifier = Modifier.size(24.dp),
                color = LocalContentColor.current
            )
        } else {
            AppText(text = text)
        }
    }
}