package io.github.vrcmteam.vrcm.presentation.extensions

import androidx.compose.runtime.Composable
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow

/**
 * 不这样拿拿不到页面上同一个HomeScreenModel对象, 导致不一致性
 */
@Composable
inline fun <reified T : ScreenModel> koinScreenModelByLastItem() = with(LocalNavigator.currentOrThrow.lastItem) {
        koinScreenModel<T>()
    }
