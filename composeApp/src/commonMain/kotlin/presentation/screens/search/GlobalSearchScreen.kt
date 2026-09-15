package io.github.vrcmteam.vrcm.presentation.screens.search

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.presentation.extensions.currentNavigator
import io.github.vrcmteam.vrcm.presentation.navigation.AppRoute
import io.github.vrcmteam.vrcm.presentation.screens.home.pager.PublicSearchContent
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings
import io.github.vrcmteam.vrcm.presentation.supports.AppIcons
import kotlinx.serialization.Serializable

@Serializable
object GlobalSearchScreen : AppRoute {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = currentNavigator
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(strings.fiendListPagerSearch) },
                    navigationIcon = {
                        IconButton(onClick = navigator::pop) {
                            Icon(AppIcons.ArrowBackIosNew, strings.back)
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
