package io.github.vrcmteam.vrcm.presentation.compoments

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.network.api.attributes.BlueprintType
import io.github.vrcmteam.vrcm.presentation.designsystem.AppTheme
import io.github.vrcmteam.vrcm.presentation.navigation.AppNavigator
import io.github.vrcmteam.vrcm.presentation.navigation.AppRoute
import io.github.vrcmteam.vrcm.presentation.navigation.LocalNavigator
import io.github.vrcmteam.vrcm.presentation.screens.group.GroupProfileScreen
import io.github.vrcmteam.vrcm.presentation.screens.home.data.FriendLocation
import io.github.vrcmteam.vrcm.presentation.screens.home.data.HomeInstanceVo
import io.github.vrcmteam.vrcm.presentation.screens.user.UserProfileScreen
import io.github.vrcmteam.vrcm.presentation.screens.user.data.UserProfileVo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalTestApi::class, ExperimentalSharedTransitionApi::class)
class LocationCardOwnerNavigationTest {
    @Test
    fun expandedUserOwnerOpensProfileWithoutTogglingCard() = runComposeUiTest {
        val owner = HomeInstanceVo.Owner("usr_owner", OwnerName, BlueprintType.User)
        val navigator = AppNavigator(mutableListOf(HomeRoute))
        var cardClicks = 0
        setLocationCard(owner, navigator, isSelected = true) { cardClicks++ }

        onNodeWithText(OwnerName).performTouchInput { click() }

        runOnIdle {
            assertEquals(
                UserProfileScreen(UserProfileVo(owner.id, owner.displayName), SharedSuffix),
                navigator.lastItem,
            )
            assertEquals(2, navigator.size)
            assertEquals(0, cardClicks)
        }
    }

    @Test
    fun expandedGroupOwnerOpensGroupInsteadOfUserProfile() = runComposeUiTest {
        val owner = HomeInstanceVo.Owner("grp_owner", OwnerName, BlueprintType.Group)
        val navigator = AppNavigator(mutableListOf(HomeRoute))
        var cardClicks = 0
        setLocationCard(owner, navigator, isSelected = true) { cardClicks++ }

        onNodeWithText(OwnerName).performTouchInput { click() }

        runOnIdle {
            assertIs<GroupProfileScreen>(navigator.lastItem)
            assertEquals(2, navigator.size)
            assertEquals(0, cardClicks)
        }
    }

    @Test
    fun collapsedOwnerKeepsCardClickBehavior() = runComposeUiTest {
        val owner = HomeInstanceVo.Owner("grp_owner", OwnerName, BlueprintType.Group)
        val navigator = AppNavigator(mutableListOf(HomeRoute))
        var cardClicks = 0
        setLocationCard(owner, navigator, isSelected = false) { cardClicks++ }

        onNodeWithText(OwnerName).performTouchInput { click() }

        runOnIdle {
            assertEquals(HomeRoute, navigator.lastItem)
            assertEquals(1, cardClicks)
        }
    }

    private fun ComposeUiTest.setLocationCard(
        owner: HomeInstanceVo.Owner,
        navigator: AppNavigator,
        isSelected: Boolean,
        onClickCard: () -> Unit,
    ) {
        val location = FriendLocation(
            location = "wrld_test:1",
            instants = mutableStateOf(
                HomeInstanceVo(worldImageUrl = "", worldName = "World", owner = owner),
            ),
            friends = mutableMapOf(),
        )
        setContent {
            CompositionLocalProvider(
                LocalNavigator provides navigator,
                LocalSharedSuffixKey provides SharedSuffix,
            ) {
                AppTheme {
                    Box(Modifier.size(width = 360.dp, height = 600.dp)) {
                        LocationCard(
                            location = location,
                            isSelected = isSelected,
                            onClickWorldImage = {},
                            onClickLocationCard = { onClickCard() },
                        ) {}
                    }
                }
            }
        }
    }

    private object HomeRoute : AppRoute {
        @Composable
        override fun Content() = Unit
    }

    private companion object {
        const val OwnerName = "An owner name that is too long to fit in the room card"
        const val SharedSuffix = "owner-navigation-test"
    }
}
