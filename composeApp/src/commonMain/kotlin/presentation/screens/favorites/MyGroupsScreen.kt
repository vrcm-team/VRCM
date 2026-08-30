package io.github.vrcmteam.vrcm.presentation.screens.favorites

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.network.api.groups.data.LimitedGroup
import io.github.vrcmteam.vrcm.network.api.users.data.LimitedUserGroup
import io.github.vrcmteam.vrcm.presentation.compoments.SearchTextField
import io.github.vrcmteam.vrcm.presentation.compoments.renderGroupItems
import io.github.vrcmteam.vrcm.presentation.extensions.currentNavigator
import io.github.vrcmteam.vrcm.presentation.navigation.AppRoute
import io.github.vrcmteam.vrcm.presentation.screens.group.GroupProfileScreen
import io.github.vrcmteam.vrcm.presentation.screens.group.data.GroupProfileVo
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings
import io.github.vrcmteam.vrcm.presentation.supports.AppIcons
import kotlinx.serialization.Serializable
import org.koin.compose.viewmodel.koinViewModel

@Serializable
object MyGroupsScreen : AppRoute {
    @Composable
    override fun Content() {
        MyGroupsScreenContent()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MyGroupsScreenContent(
    model: FavoritesGroupsModel = koinViewModel(),
) {
    val navigator = currentNavigator
    val state by model.state.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        model.loadIfNeeded()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(strings.myGroups) },
                navigationIcon = {
                    IconButton(onClick = { navigator.pop() }) {
                        Icon(AppIcons.ArrowBackIosNew, strings.back)
                    }
                },
                actions = {
                    IconButton(
                        enabled = !state.isLoading,
                        onClick = model::refresh,
                    ) {
                        Icon(AppIcons.Update, strings.refresh)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding),
        ) {
            SearchTextField(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                value = state.searchText,
                onValueChange = model::setSearchText,
            )
            if (state.isLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
            Box(Modifier.fillMaxSize()) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    renderGroupItems(state.visibleGroups.map { it.toLimitedGroup() }) { group, suffix ->
                        navigator push GroupProfileScreen(GroupProfileVo(group), suffix)
                    }
                }

                val empty = state.visibleGroups.isEmpty()
                if (state.isLoading && empty) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                } else if (state.error != null && empty) {
                    StateMessage(strings.myGroupsLoadFailed, strings.retry, model::refresh)
                } else if (empty) {
                    StateMessage(strings.myGroupsEmpty, null, null)
                } else if (state.error != null) {
                    ErrorBanner(strings.myGroupsLoadFailed, model::refresh)
                }
            }
        }
    }
}

private fun LimitedUserGroup.toLimitedGroup() = LimitedGroup(
    id = groupId,
    name = name,
    shortCode = shortCode,
    description = description,
    iconUrl = iconUrl,
    bannerUrl = bannerUrl,
    memberCount = memberCount,
    membershipStatus = "member",
)
