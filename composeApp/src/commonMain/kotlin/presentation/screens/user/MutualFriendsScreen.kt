package io.github.vrcmteam.vrcm.presentation.screens.user

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import io.github.vrcmteam.vrcm.presentation.compoments.renderUserItem
import io.github.vrcmteam.vrcm.presentation.compoments.renderUserItems
import io.github.vrcmteam.vrcm.presentation.screens.user.data.UserProfileVo
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings
import io.github.vrcmteam.vrcm.presentation.supports.AppIcons

data class MutualFriendsScreen(
    private val userId: String,
    private val userName: String,
) : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val model: MutualFriendsScreenModel = koinScreenModel()
        val mutualFriends by remember { derivedStateOf { model.mutualFriends.sortedBy { it.displayName } } }
        val totalCount by remember { derivedStateOf { model.mutualFriends.size } }
        val visibleMutualFriends by remember {
            derivedStateOf {
                mutualFriends.filter { it.id != HIDDEN_MUTUAL_USER_ID }
            }
        }
        val hiddenCount by remember { derivedStateOf { totalCount - visibleMutualFriends.size } }
        val strings = strings
        val titleText by remember {
            derivedStateOf {
                if (hiddenCount > 0) {
                    strings.mutualFriendsCountWithHidden
                        .replace("%total%", totalCount.toString())
                        .replace("%hidden%", hiddenCount.toString())
                } else {
                    strings.mutualFriendsCount
                        .replace("%total%", totalCount.toString())
                }
            }
        }
        val contentState = resolveMutualFriendsContentState(
            hasLoadedSuccessfully = model.hasLoadedSuccessfully,
            isLoading = model.isLoading,
            hasError = model.errorMessage != null,
            totalCount = totalCount,
        )
        val displayName = userName.ifBlank { strings.users }

        LaunchedEffect(userId) {
            model.load(userId)
        }

        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = titleText,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(
                                painter = rememberVectorPainter(AppIcons.ArrowBackIosNew),
                                tint = MaterialTheme.colorScheme.primary,
                                contentDescription = "back"
                            )
                        }
                    }
                )
            },
            contentColor = MaterialTheme.colorScheme.primary
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                when (contentState) {
                    MutualFriendsContentState.Loading -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }

                    MutualFriendsContentState.Error -> {
                        Column(modifier = Modifier.align(Alignment.Center)) {
                            Text(
                                text = strings.mutualFriendsLoadFailed,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                            TextButton(
                                modifier = Modifier.align(Alignment.CenterHorizontally),
                                onClick = { model.load(userId) }
                            ) {
                                Text(strings.retry)
                            }
                        }
                    }

                    MutualFriendsContentState.Empty -> {
                        Text(
                            modifier = Modifier.align(Alignment.Center),
                            text = strings.mutualFriendsEmpty.replace("%s", displayName),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }

                    MutualFriendsContentState.Content -> {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            renderUserItems(visibleMutualFriends) {
                                navigator push UserProfileScreen(UserProfileVo(it))
                            }
                        }
                    }
                }
            }
        }
    }
}

internal enum class MutualFriendsContentState {
    Loading,
    Error,
    Empty,
    Content,
}

internal fun resolveMutualFriendsContentState(
    hasLoadedSuccessfully: Boolean,
    isLoading: Boolean,
    hasError: Boolean,
    totalCount: Int,
): MutualFriendsContentState = when {
    !hasLoadedSuccessfully && isLoading -> MutualFriendsContentState.Loading
    !hasLoadedSuccessfully && hasError -> MutualFriendsContentState.Error
    totalCount == 0 -> MutualFriendsContentState.Empty
    else -> MutualFriendsContentState.Content
}

private const val HIDDEN_MUTUAL_USER_ID = "usr_00000000-0000-0000-0000-000000000000"
