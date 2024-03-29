package io.github.vrcmteam.vrcm.presentation.extensions

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import io.github.vrcmteam.vrcm.presentation.compoments.snackBarToastText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

val currentNavigator: Navigator
    @Composable
    get() = LocalNavigator.currentOrThrow


@Composable
inline fun createFailureCallbackDoNavigation(
    navigator: Navigator = currentNavigator,
    crossinline screen: suspend () -> Screen
): (String) -> Unit {
    var snackBarToastText by snackBarToastText
    val scope = rememberCoroutineScope()
    return { text: String ->
        scope.launch {
            snackBarToastText = text
            delay(3000L)
            navigator replace screen()
        }
    }
}

@Composable
inline fun getInsetPadding(default: Int, direction: (WindowInsets, Density) -> Int) =
    with(LocalDensity.current) {
        (direction(NavigationBarDefaults.windowInsets, this).takeIf { it != 0 } ?: default).toDp()
    }

@Composable
inline fun getInsetPadding(direction: (WindowInsets, Density) -> Int) =
    getInsetPadding(0, direction)

