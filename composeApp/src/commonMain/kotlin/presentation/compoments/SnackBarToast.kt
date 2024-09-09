package io.github.vrcmteam.vrcm.presentation.compoments

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre

/**
 * toast弹窗
 */
@Composable
fun SnackBarToast(
    text: String,
    modifier: Modifier = Modifier,
    onEffect: () -> Unit,
    containerColor: Color = MaterialTheme.colorScheme.onPrimary,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
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
            containerColor = containerColor,
            contentColor = contentColor,
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
        Box(modifier = Modifier.fillMaxWidth()){
            Text(
                modifier = Modifier.align(Alignment.Center),
                text = it.visuals.message
            )
        }
    },
    content: @Composable () -> Unit
) {

    Box {
        CompositionLocalProvider(
            LocalSnackBarToastText provides remember { mutableStateOf(ToastText.Normal) }
        ) {
            content()
            var sackBarToastText by LocalSnackBarToastText.current
            LaunchedEffect(Unit){
                SharedFlowCentre.toastText.collect {
                    sackBarToastText = it
                }
            }
            val theme = when (sackBarToastText) {
                is ToastText.Error -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
                is ToastText.Success ,
                is ToastText.Info ,
                ToastText.Normal -> MaterialTheme.colorScheme.onPrimary to MaterialTheme.colorScheme.onSurface
            }
            SnackBarToast(
                modifier = modifier.align(alignment),
                text = sackBarToastText.text,
                onEffect = { sackBarToastText = ToastText.Normal },
                containerColor = theme.first,
                contentColor = theme.second,
                content = toastContent
            )
        }
    }

}

val LocalSnackBarToastText: ProvidableCompositionLocal<MutableState<ToastText>> =
    compositionLocalOf { error("No text provided") }


sealed class ToastText(val text: String) {
    class Success(text: String) : ToastText(text)
    class Error(text: String) : ToastText(text)
    class Info(text: String) : ToastText(text)
//    class Warning(text: String) : ToastText(text)
    data object Normal : ToastText("")
}
