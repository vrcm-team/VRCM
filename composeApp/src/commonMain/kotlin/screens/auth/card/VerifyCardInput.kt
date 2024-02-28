package io.github.vrcmteam.vrcm.screens.auth.card

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.screens.auth.AuthUIState
import io.github.vrcmteam.vrcm.screens.theme.SmallRoundedShape
import io.github.vrcmteam.vrcm.screens.util.CodeTextField
import io.github.vrcmteam.vrcm.screens.util.LoadingButton

@Composable
fun VerifyCardInput(
    uiState: AuthUIState,
    onVerifyCodeChange: (String) -> Unit,
    onClick: () -> Unit
) {
    val verifyCode = uiState.verifyCode
    Spacer(modifier = Modifier.height(68.dp))
    CodeTextField(
        modifier = Modifier
            .fillMaxWidth(),
        value = verifyCode,
        onValueChange = onVerifyCodeChange,
        length = 6,
        boxWidth = 48.dp,
        boxHeight = 48.dp,
        boxMargin = 12.dp,
        boxShape = SmallRoundedShape,
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