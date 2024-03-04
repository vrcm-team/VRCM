package io.github.vrcmteam.vrcm.presentation.compoments

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import io.github.vrcmteam.vrcm.presentation.theme.MediumRoundedShape

@Composable
fun SnackBarToast(
    text: String,
    modifier: Modifier = Modifier,
    onEffect: () -> Unit,
    content: @Composable (SnackbarData) -> Unit = {
        Text(text = it.visuals.message)
    }
) {
    val snackBarHostState = remember { SnackbarHostState() }

    LaunchedEffect(text) {
        if (text.isNotBlank()) {
            snackBarHostState.showSnackbar(text)
            onEffect()
        }
    }
    SnackbarHost(
        modifier = modifier,
        hostState = snackBarHostState
    ) {
        Snackbar(
            containerColor = MaterialTheme.colorScheme.onPrimary,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = MediumRoundedShape,
            actionOnNewLine = true
        ) {
            content(it)
        }
    }
}