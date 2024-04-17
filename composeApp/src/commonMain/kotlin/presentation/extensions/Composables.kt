package io.github.vrcmteam.vrcm.presentation.extensions

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow

val currentNavigator: Navigator
    @Composable
    get() = LocalNavigator.currentOrThrow


@Composable
inline fun getInsetPadding(default: Int, direction: (WindowInsets, Density) -> Int) =
    with(LocalDensity.current) {
        val toDp = (direction(WindowInsets.systemBars, this).takeIf { it != 0 }
            ?: default).toDp()
        // 有时候12.toDp()会得到3+.dp的值
        default.dp.takeIf { it > toDp } ?: toDp
    }

@Composable
inline fun getInsetPadding(direction: (WindowInsets, Density) -> Int) =
    getInsetPadding(0, direction)

