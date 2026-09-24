package io.github.vrcmteam.vrcm.presentation.screens.auth.card

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.core.shared.AppConst
import io.github.vrcmteam.vrcm.getAppPlatform
import io.github.vrcmteam.vrcm.presentation.compoments.IPasswordField
import io.github.vrcmteam.vrcm.presentation.compoments.ITextField
import io.github.vrcmteam.vrcm.presentation.compoments.LoadingButton
import io.github.vrcmteam.vrcm.presentation.designsystem.AppText
import io.github.vrcmteam.vrcm.presentation.designsystem.AppTheme
import io.github.vrcmteam.vrcm.presentation.extensions.openUrl
import io.github.vrcmteam.vrcm.presentation.screens.auth.data.AuthUIState
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings
import io.github.vrcmteam.vrcm.presentation.supports.AppIcons

@Composable
fun LoginCardInput(
    uiState: AuthUIState,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onClick: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    var isFocus by remember { mutableStateOf(false) }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ITextField(
            modifier = Modifier.padding(horizontal = 32.dp),
            painter = rememberVectorPainter(AppIcons.AccountCircle),
            hintText = strings.authLoginUsername,
            textValue = uiState.username,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Next
            ),
            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
            onValueChange = onUsernameChange
        )
        IPasswordField(
            modifier = Modifier.padding(horizontal = 32.dp),
            imageVector = AppIcons.Lock,
            hintText = strings.authLoginPassword,
            textValue = uiState.password,
            isFocus = isFocus,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            onFocusChanged = { focusState -> isFocus = focusState.isFocused },
            onValueChange = onPasswordChange
        )
    }
    // 登录按钮与条款说明作为一组参与卡片的均分布局，说明紧贴在按钮下方
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        LoadingButton(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 64.dp)
                .height(48.dp),
            text = strings.authLoginButton,
            enabled = uiState.password.isNotBlank() && uiState.username.isNotBlank(),
            isLoading = uiState.btnIsLoading,
            onClick = onClick
        )
        AuthTermsNotice(modifier = Modifier.padding(horizontal = 32.dp))
    }
}

/** 登录即同意 VRChat 条款：内容由 VRChat 托管与审核，使用本应用同样受其服务条款和社区准则约束。 */
@Composable
private fun AuthTermsNotice(modifier: Modifier = Modifier) {
    val platform = getAppPlatform()
    val template = strings.authTermsNotice
    // 顺序与文案中的两个 %s 对应
    val links = listOf(
        strings.authTermsOfService to AppConst.VRCHAT_TERMS_URL,
        strings.authCommunityGuidelines to AppConst.VRCHAT_COMMUNITY_GUIDELINES_URL,
    )
    val linkStyles = TextLinkStyles(style = SpanStyle(color = AppTheme.colors.tint))
    val text = remember(template, links, linkStyles, platform) {
        buildAnnotatedString {
            val parts = template.split("%s")
            parts.forEachIndexed { index, part ->
                append(part)
                if (index == parts.lastIndex) return@forEachIndexed
                val (label, url) = links.getOrNull(index) ?: return@forEachIndexed
                withLink(LinkAnnotation.Clickable(tag = url, styles = linkStyles) { platform.openUrl(url) }) {
                    append(label)
                }
            }
        }
    }
    AppText(
        text = text,
        modifier = modifier,
        style = AppTheme.type.footnote,
        color = AppTheme.colors.secondaryLabel,
        textAlign = TextAlign.Center,
    )
}
