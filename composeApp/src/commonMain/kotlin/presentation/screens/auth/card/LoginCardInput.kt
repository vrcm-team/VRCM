package io.github.vrcmteam.vrcm.presentation.screens.auth.card

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.presentation.compoments.LoadingButton
import io.github.vrcmteam.vrcm.presentation.screens.auth.data.AuthUIState
import io.github.vrcmteam.vrcm.presentation.supports.PasswordMissEndVisualTransformation
import io.github.vrcmteam.vrcm.screens.theme.BigRoundedShape

@Composable
fun LoginCardInput(
    uiState: AuthUIState,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onClick: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    var isFocus by remember { mutableStateOf(false) }
    Spacer(modifier = Modifier.height(36.dp))
    LoginTextField(
        imageVector = Icons.Outlined.AccountCircle,
        hintText = "Username",
        textValue = uiState.username,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
        keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
        onValueChange = onUsernameChange
    )
    Spacer(modifier = Modifier.height(16.dp))
    LoginTextField(
        imageVector = Icons.Outlined.Lock,
        hintText = "Password",
        textValue = uiState.password,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
        visualTransformation =
        if (isFocus) {
            PasswordMissEndVisualTransformation()
        } else {
            PasswordVisualTransformation()
        },
        onFocusChanged = { focusState -> isFocus = focusState.isFocused },
        onValueChange = onPasswordChange
    )
    Spacer(modifier = Modifier.height(36.dp))
    LoadingButton(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 64.dp)
            .height(48.dp),
        text = "LOGIN",
        enabled = uiState.password.isNotBlank() && uiState.username.isNotBlank(),
        isLoading = uiState.btnIsLoading,
        onClick = onClick
    )
}

@Composable
fun LoginTextField(
    imageVector: ImageVector,
    hintText: String,
    textValue: String,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    onFocusChanged: (FocusState) -> Unit = {},
    onValueChange: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = BigRoundedShape
            )
            .background(
                color = MaterialTheme.colorScheme.surface,
                shape = BigRoundedShape
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            modifier = Modifier
                .padding(start = 12.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            imageVector = imageVector,
            contentDescription = "",
            tint = MaterialTheme.colorScheme.outline
        )
        BasicTextField(
            modifier = Modifier
                .onFocusChanged(onFocusChanged)
                .fillMaxWidth()
                .padding(end = 12.dp, top = 12.dp, bottom = 12.dp),
            value = textValue,
            decorationBox = { innerTextField ->
                Box {
                    if (textValue.isEmpty()) {
                        Text(
                            text = hintText,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    innerTextField()
                }
            },
            onValueChange = { onValueChange(it.trim()) },
            singleLine = true,
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface)
        )
    }
}


