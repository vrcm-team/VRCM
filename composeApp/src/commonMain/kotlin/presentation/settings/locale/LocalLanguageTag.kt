package io.github.vrcmteam.vrcm.presentation.settings.locale

import androidx.compose.ui.text.intl.Locale


enum class LanguageTag(val tag: String,val displayName: String) {
    EN("en","English"),
    JA("ja","日本語"),
    ZH_HANS("zh-Hans","简体中文"),
    ZH_HANT("zh-Hant","繁體中文"),;

    companion object {
        fun fromTag(tag: String): LanguageTag? {
            return entries.firstOrNull { it.tag == tag }
        }
        val Default :LanguageTag
            get() {
                val currentLocale = Locale.current
                val currentTag =  when (currentLocale.script.isNotEmpty()) {
                    true -> currentLocale.language
                    false -> "${currentLocale.language}-${currentLocale.script}"
                }
               return entries.firstOrNull { it.tag.startsWith(currentTag)} ?: entries.first()
            }
    }
}
