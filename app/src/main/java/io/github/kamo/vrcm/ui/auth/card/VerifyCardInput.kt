package io.github.kamo.vrcm.ui.auth.card

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardReturn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.kamo.vrcm.ui.auth.AuthCardState
import io.github.kamo.vrcm.ui.auth.AuthUIState
import io.github.kamo.vrcm.ui.util.CodeTextField
import io.github.kamo.vrcm.ui.util.rememberImeState

@Composable
fun VerifyCardInput(
    uiState: AuthUIState,
    onVerifyCodeChange: (String) -> Unit,
    onClick: () -> Unit
) {
    Spacer(modifier = Modifier.height(68.dp))
    CodeTextField(
        modifier = Modifier
            .fillMaxWidth(),
        value = uiState.verifyCode,
        onValueChange = onVerifyCodeChange,
        length = 6,
        boxWidth = 48.dp,
        boxHeight = 48.dp,
        boxMargin = 12.dp,
        boxShape = RoundedCornerShape(4.dp),
        boxBackgroundColor = MaterialTheme.colorScheme.surface,
        textColor = MaterialTheme.colorScheme.onSurface
    )
    Spacer(modifier = Modifier.height(68.dp))
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
            text = "VERIFY",
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

