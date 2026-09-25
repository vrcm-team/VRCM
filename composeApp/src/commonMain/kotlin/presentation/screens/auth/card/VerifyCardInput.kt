package io.github.vrcmteam.vrcm.presentation.screens.auth.card

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.presentation.compoments.CodeTextField
import io.github.vrcmteam.vrcm.presentation.compoments.LoadingButton
import io.github.vrcmteam.vrcm.presentation.designsystem.AppButton
import io.github.vrcmteam.vrcm.presentation.designsystem.AppButtonSize
import io.github.vrcmteam.vrcm.presentation.designsystem.AppButtonStyle
import io.github.vrcmteam.vrcm.presentation.designsystem.AppShapes
import io.github.vrcmteam.vrcm.presentation.designsystem.AppText
import io.github.vrcmteam.vrcm.presentation.designsystem.AppTextField
import io.github.vrcmteam.vrcm.presentation.designsystem.AppTheme
import io.github.vrcmteam.vrcm.presentation.screens.auth.data.AuthCardPage
import io.github.vrcmteam.vrcm.presentation.screens.auth.data.AuthUIState
import io.github.vrcmteam.vrcm.presentation.screens.auth.data.normalizeVerifyCode
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings

@Composable
fun VerifyCardInput(
    uiState: AuthUIState,
    onVerifyCodeChange: (String) -> Unit,
    onSwitchTwoFactorMethod: () -> Unit,
    onClick: () -> Unit
) {
    val verifyCode = uiState.verifyCode
    val cardState = uiState.cardState
    val focusManager = LocalFocusManager.current
    if (cardState == AuthCardPage.TFACode) {
        // 恢复码是字母数字混合，用普通输入框；提交前再整理成接口要求的格式
        AppTextField(
            value = verifyCode,
            onValueChange = onVerifyCodeChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            placeholder = { AppText(strings.authRecoveryCodeHint) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Ascii,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
        )
    } else {
        CodeTextField(
            modifier = Modifier
                .fillMaxWidth(),
            value = verifyCode,
            onValueChange = {
                if (it.length == 6) {
                    focusManager.clearFocus()
                }
                onVerifyCodeChange(it)
            },
            length = 6,
            boxWidth = 48.dp,
            boxHeight = 48.dp,
            boxMargin = 12.dp,
            boxShape = AppShapes.s,
            boxBackgroundColor = AppTheme.colors.secondaryGroupedBackground,
            textColor = AppTheme.colors.label
        )
    }
    // 验证按钮与切换入口作为一组参与卡片的均分布局
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        LoadingButton(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 64.dp)
                .height(48.dp),
            text = strings.authVerifyButton,
            enabled = cardState.normalizeVerifyCode(verifyCode) != null,
            isLoading = uiState.btnIsLoading,
            onClick = onClick
        )
        // 开启验证器 2FA 的账号可以在验证器验证码与一次性恢复码之间切换；邮箱验证码没有替代方式
        if (cardState == AuthCardPage.TTFACode || cardState == AuthCardPage.TFACode) {
            AppButton(
                text = if (cardState == AuthCardPage.TFACode) {
                    strings.authUseAuthenticatorCode
                } else {
                    strings.authUseRecoveryCode
                },
                onClick = onSwitchTwoFactorMethod,
                style = AppButtonStyle.Plain,
                size = AppButtonSize.Small,
                enabled = !uiState.btnIsLoading,
            )
        }
    }
}
