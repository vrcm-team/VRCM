package io.github.vrcmteam.vrcm.presentation.compoments

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * toast弹窗
 */
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
            shape = MaterialTheme.shapes.medium,
            actionOnNewLine = true
        ) {
            content(it)
        }
    }
}

@Composable
fun SnackBarToastBox(
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.TopCenter,
    toastContent: @Composable (SnackbarData) -> Unit = {
        Text(text = it.visuals.message)
    },
    content: @Composable BoxScope.() -> Unit
) {
    Box {
        CompositionLocalProvider(
            LocalSnackBarToastText provides mutableStateOf("")
        ) {
            content()
            val sackBarToastText = LocalSnackBarToastText.current
            SnackBarToast(
                modifier = modifier.align(alignment),
                text = sackBarToastText.value,
                onEffect = { sackBarToastText.value = "" },
                content = toastContent
            )
        }
    }

}

val LocalSnackBarToastText: ProvidableCompositionLocal<MutableState<String>> =
    compositionLocalOf { error("No text provided") }

val snackBarToastText: MutableState<String>
    @Composable
    get() = LocalSnackBarToastText.current
