package io.github.vrcmteam.vrcm.presentation.screens.search

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.presentation.designsystem.AppIcon
import io.github.vrcmteam.vrcm.presentation.designsystem.AppIconButton
import io.github.vrcmteam.vrcm.presentation.designsystem.AppNavBar
import io.github.vrcmteam.vrcm.presentation.designsystem.AppScaffold
import io.github.vrcmteam.vrcm.presentation.designsystem.AppText
import io.github.vrcmteam.vrcm.presentation.extensions.currentNavigator
import io.github.vrcmteam.vrcm.presentation.navigation.AppRoute
import io.github.vrcmteam.vrcm.presentation.screens.home.pager.PublicSearchContent
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings
import io.github.vrcmteam.vrcm.presentation.supports.AppIcons
import kotlinx.serialization.Serializable

@Serializable
object GlobalSearchScreen : AppRoute {
        @Composable
    override fun Content() {
        val navigator = currentNavigator
        AppScaffold(
            topBar = {
                AppNavBar(
                    title = { AppText(strings.fiendListPagerSearch) },
                    navigationIcon = {
                        AppIconButton(onClick = navigator::pop) {
                            AppIcon(AppIcons.ArrowBackIosNew, strings.back)
                        }
                    },
                )
            },
        ) { padding ->
            PublicSearchContent(
                modifier = Modifier.fillMaxSize().padding(padding),
                bottomContentPadding = 24.dp,
            )
        }
    }
}
