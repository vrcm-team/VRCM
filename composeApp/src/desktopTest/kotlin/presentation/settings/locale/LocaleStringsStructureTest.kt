package io.github.vrcmteam.vrcm.presentation.settings.locale

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocaleStringsStructureTest {
    @Test
    fun catalogValuesAreNotConstructorParameters() {
        assertTrue(
            LocaleStrings::class.java.declaredConstructors.none { constructor ->
                constructor.parameterTypes.any { it == String::class.java }
            },
        )
    }

    @Test
    fun translatedLocalesOverrideTheEntireCatalog() {
        val catalogGetters = LocaleStrings::class.java.stringGetterNames()

        listOf(
            LocaleStringsJa::class.java,
            LocaleStringsZhHans::class.java,
            LocaleStringsZhHant::class.java,
        ).forEach { localeClass ->
            assertEquals(catalogGetters, localeClass.stringGetterNames(), localeClass.simpleName)
        }
    }

    private fun Class<*>.stringGetterNames(): Set<String> =
        declaredMethods
            .filter { it.parameterCount == 0 && it.returnType == String::class.java }
            .mapTo(mutableSetOf()) { it.name }
}
