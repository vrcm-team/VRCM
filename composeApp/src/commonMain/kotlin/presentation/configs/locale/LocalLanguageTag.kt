package io.github.vrcmteam.vrcm.presentation.configs.locale

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf


val LocalLanguageTag: ProvidableCompositionLocal<MutableState<LanguageTag>> =
    compositionLocalOf { mutableStateOf(LanguageTag.EN) }


enum class LanguageTag(val tag: String,val displayName: String) {
    EN("en","English"),
    JA("ja","日本語"),
    ZH_HANS("zh-Hans","简体中文"),
    ZH_HANT("zh-Hant","繁體中文"),
}
