package io.github.vrcmteam.vrcm.di.modules

import io.github.vrcmteam.vrcm.presentation.settings.SettingsModel
import org.koin.core.annotation.KoinInternalApi
import org.koin.core.definition.Kind
import kotlin.test.Test
import kotlin.test.assertEquals

class PresentationModuleScopeTest {
    /**
     * The Application side reports a refused background monitoring start and the settings UI applies
     * it, so both must resolve the same SettingsModel. Registering it as a factory still compiles
     * and still runs, but the report reaches a flow nobody observes and the switch silently keeps
     * claiming that monitoring is on.
     */
    @OptIn(KoinInternalApi::class)
    @Test
    fun settingsModelIsSharedBetweenApplicationAndSettingsUi() {
        val definitions = presentationModule.mappings.values
            .map { it.beanDefinition }
            .filter { it.primaryType == SettingsModel::class }

        assertEquals(1, definitions.size, "SettingsModel should be declared exactly once")
        assertEquals(Kind.Singleton, definitions.single().kind)
    }
}
