package io.github.vrcmteam.vrcm.presentation.screens.auth.card

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.presentation.compoments.CodeTextField
import io.github.vrcmteam.vrcm.presentation.compoments.LoadingButton
import io.github.vrcmteam.vrcm.presentation.screens.auth.data.AuthUIState

@Composable
fun VerifyCardInput(
    uiState: AuthUIState,
    onVerifyCodeChange: (String) -> Unit,
    onClick: () -> Unit
) {
    val verifyCode = uiState.verifyCode
    Spacer(modifier = Modifier.height(68.dp))
    val focusManager = LocalFocusManager.current
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
        boxShape = MaterialTheme.shapes.small,
        boxBackgroundColor = MaterialTheme.colorScheme.surface,
        textColor = MaterialTheme.colorScheme.onSurface
    )
    Spacer(modifier = Modifier.height(68.dp))
    LoadingButton(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 64.dp)
            .height(48.dp),
        text = "VERIFY",
        enabled = verifyCode.isNotBlank() && verifyCode.length == 6,
        isLoading = uiState.btnIsLoading,
        onClick = onClick
    )
}