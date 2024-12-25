package io.github.vrcmteam.vrcm.presentation.screens.auth.card

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.presentation.compoments.IPasswordField
import io.github.vrcmteam.vrcm.presentation.compoments.ITextField
import io.github.vrcmteam.vrcm.presentation.compoments.LoadingButton
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
            imageVector = AppIcons.AccountCircle,
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
}

