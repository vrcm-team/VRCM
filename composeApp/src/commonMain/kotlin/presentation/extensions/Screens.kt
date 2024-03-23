package io.github.vrcmteam.vrcm.presentation.extensions

import androidx.compose.runtime.Composable
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.getScreenModel
import org.koin.core.parameter.parameterSetOf


@Composable
inline fun <reified T : ScreenModel,reified P> Screen.getCallbackScreenModel(
    noinline onFailureCallback: (P) -> Unit
): T = getScreenModel {
    parameterSetOf(onFailureCallback)
}
