package io.github.vrcmteam.vrcm.presentation.screens.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.di.modules.presentationModule
import io.github.vrcmteam.vrcm.network.api.invite.data.InviteMessageData
import io.github.vrcmteam.vrcm.network.api.invite.data.InviteMessageType
import io.github.vrcmteam.vrcm.presentation.adaptive.LocalAppContentSize
import io.github.vrcmteam.vrcm.presentation.compoments.SharedTransitionScreen
import io.github.vrcmteam.vrcm.presentation.navigation.AppListRoute
import io.github.vrcmteam.vrcm.presentation.navigation.AppNavigator
import io.github.vrcmteam.vrcm.presentation.navigation.AppRoute
import io.github.vrcmteam.vrcm.presentation.navigation.BackNavigationPolicy
import io.github.vrcmteam.vrcm.presentation.navigation.LocalBackNavigationPolicy
import io.github.vrcmteam.vrcm.presentation.navigation.LocalNavigator
import kotlinx.coroutines.flow.MutableStateFlow
import org.koin.compose.KoinApplication
import org.koin.dsl.module
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class InviteMessageSettingsNavigationTest {
    @Test
    fun settingsEntryRendersInviteSlotsWithDuplicateServerIds() = runComposeUiTest {
        val source = RuntimeInviteMessageSource()
        val navigator = AppNavigator(mutableStateListOf<AppRoute>(SettingsRoute))

        setContent {
            KoinApplication(
                application = {
                    modules(
                        presentationModule,
                        module {
                            single<InviteMessageSlotsSource> { source }
                        },
                    )
                },
            ) {
                MaterialTheme {
                    CompositionLocalProvider(
                        LocalNavigator provides navigator,
                        LocalBackNavigationPolicy provides BackNavigationPolicy(),
                        LocalAppContentSize provides DpSize(360.dp, 800.dp),
                    ) {
                        SharedTransitionScreen(navigator = navigator) { route ->
                            when (route) {
                                SettingsRoute -> TextButton(
                                    onClick = { navigator.push(InviteMessageSlotsScreen) },
                                ) {
                                    Text("Invite entry")
                                }

                                else -> route.Content()
                            }
                        }
                    }
                }
            }
        }

        onNodeWithText("Invite entry").performClick()
        waitUntil(timeoutMillis = 10_000) {
            onAllNodesWithText("Runtime slot 0").fetchSemanticsNodes().isNotEmpty()
        }

        onNodeWithText("Runtime slot 0").fetchSemanticsNode()
    }
}

private data object SettingsRoute : AppListRoute {
    override val key: String = "settings-entry-test"

    @Composable
    override fun Content() = Unit
}

private class RuntimeInviteMessageSource : InviteMessageSlotsSource {
    private val session = InviteMessageSession(userId = "usr_runtime", generation = 1L)
    override val sessions = MutableStateFlow<InviteMessageSession?>(session)

    override fun isCurrent(session: InviteMessageSession): Boolean = session == this.session

    override suspend fun load(
        session: InviteMessageSession,
        messageType: InviteMessageType,
    ) = InviteMessageSourceResult(
        result = Result.success(
            List(12) { slot ->
                InviteMessageData(
                    canBeUpdated = true,
                    id = "shared-server-id",
                    message = "Runtime slot $slot",
                    messageType = messageType,
                    remainingCooldownMinutes = 0,
                    slot = slot,
                    updatedAt = "2026-09-09T00:00:00Z",
                )
            },
        ),
        session = session,
    )

    override suspend fun update(
        session: InviteMessageSession,
        messageType: InviteMessageType,
        slot: Int,
        message: String,
    ): InviteMessageSourceResult<List<InviteMessageData>>? = null

    override suspend fun reset(
        session: InviteMessageSession,
        messageType: InviteMessageType,
        slot: Int,
    ): InviteMessageSourceResult<List<InviteMessageData>>? = null
}
