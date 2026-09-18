package io.github.vrcmteam.vrcm.presentation.compoments

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.presentation.designsystem.AppActivityIndicator
import io.github.vrcmteam.vrcm.presentation.designsystem.AppButton
import io.github.vrcmteam.vrcm.presentation.designsystem.AppButtonSize
import io.github.vrcmteam.vrcm.presentation.designsystem.AppButtonStyle
import io.github.vrcmteam.vrcm.presentation.designsystem.AppText
import io.github.vrcmteam.vrcm.presentation.designsystem.AppTheme

@Composable
fun LoadingButton(
    modifier: Modifier = Modifier,
    text: String,
    enabled: Boolean = true,
    isLoading: Boolean,
    onClick: () -> Unit
) {
    AppButton(
        modifier = modifier,
        onClick = onClick,
        enabled = enabled,
        style = AppButtonStyle.Prominent,
        size = AppButtonSize.Large,
    ) {
        if (isLoading) {
            AppActivityIndicator(
                modifier = Modifier.size(24.dp),
                color = AppTheme.colors.onTint
            )
        } else {
            AppText(text = text)
        }
    }
}