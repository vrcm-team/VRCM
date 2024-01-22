package io.github.kamo.vrcm.ui.auth.card

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.PersonOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.github.kamo.vrcm.ui.auth.AuthUIState
import io.github.kamo.vrcm.ui.util.PasswordMissEndVisualTransformation

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
        imageVector = Icons.Rounded.PersonOutline,
        hintText = "Username",
        textValue = uiState.username,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
        keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
        onValueChange = onUsernameChange
    )
    Spacer(modifier = Modifier.height(16.dp))
    LoginTextField(
        imageVector = Icons.Rounded.Key,
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
    Button(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 64.dp)
            .height(48.dp),
        shape = RoundedCornerShape(24.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ),
        onClick = onClick,
    ) {
        Text(
            text = "LOGIN",
            style = MaterialTheme.typography.titleMedium,
        )
    }
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
                shape = RoundedCornerShape(size = 16.dp)
            )
            .background(color = MaterialTheme.colorScheme.surface),
        verticalAlignment = Alignment.CenterVertically
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
            onValueChange = onValueChange,
            singleLine = true,
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            textStyle = MaterialTheme.typography.bodyLarge,
        )
    }
}


