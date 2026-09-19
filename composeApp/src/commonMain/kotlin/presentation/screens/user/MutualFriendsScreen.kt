package io.github.vrcmteam.vrcm.presentation.screens.user

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.presentation.designsystem.AppActivityIndicator
import io.github.vrcmteam.vrcm.presentation.designsystem.AppButton
import io.github.vrcmteam.vrcm.presentation.designsystem.AppButtonStyle
import io.github.vrcmteam.vrcm.presentation.designsystem.AppIcon
import io.github.vrcmteam.vrcm.presentation.designsystem.AppIconButton
import io.github.vrcmteam.vrcm.presentation.designsystem.AppNavBar
import io.github.vrcmteam.vrcm.presentation.designsystem.AppScaffold
import io.github.vrcmteam.vrcm.presentation.designsystem.AppText
import io.github.vrcmteam.vrcm.presentation.designsystem.AppTheme
import io.github.vrcmteam.vrcm.presentation.navigation.AppDetailRoute
import org.koin.compose.viewmodel.koinViewModel
import io.github.vrcmteam.vrcm.presentation.navigation.LocalNavigator
import io.github.vrcmteam.vrcm.presentation.navigation.currentOrThrow
import io.github.vrcmteam.vrcm.presentation.compoments.renderUserItems
import io.github.vrcmteam.vrcm.presentation.screens.user.data.UserProfileVo
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings
import io.github.vrcmteam.vrcm.presentation.supports.AppIcons
import kotlinx.serialization.Serializable

@Serializable
data class MutualFriendsScreen(
    private val userId: String,
    private val userName: String,
) : AppDetailRoute {

        @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val model: MutualFriendsScreenModel = koinViewModel()
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

        AppScaffold(
            containerColor = AppTheme.colors.systemBackground,
            topBar = {
                AppNavBar(
                    edgeColor = AppTheme.colors.systemBackground,
                    title = {
                        AppText(
                            text = titleText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        AppIconButton(onClick = { navigator.pop() }) {
                            AppIcon(
                                painter = rememberVectorPainter(AppIcons.ArrowBackIosNew),
                                contentDescription = "back"
                            )
                        }
                    }
                )
            },
        ) { paddingValues ->
            // 顶部留白做外边距；底部安全区交给列表的 contentPadding，列表能滚到系统导航条下面
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = paddingValues.calculateTopPadding())
            ) {
                when (contentState) {
                    MutualFriendsContentState.Loading -> {
                        AppActivityIndicator(modifier = Modifier.align(Alignment.Center))
                    }

                    MutualFriendsContentState.Error -> {
                        Column(modifier = Modifier.align(Alignment.Center)) {
                            AppText(
                                text = strings.mutualFriendsLoadFailed,
                                style = AppTheme.type.subheadline,
                                color = AppTheme.colors.destructive
                            )
                            AppButton(
                                modifier = Modifier.align(Alignment.CenterHorizontally),
                                onClick = { model.load(userId) },
                                style = AppButtonStyle.Plain,
                            ) {
                                AppText(strings.retry)
                            }
                        }
                    }

                    MutualFriendsContentState.Empty -> {
                        AppText(
                            modifier = Modifier.align(Alignment.Center),
                            text = strings.mutualFriendsEmpty.replace("%s", displayName),
                            style = AppTheme.type.subheadline,
                            color = AppTheme.colors.tertiaryLabel
                        )
                    }

                    MutualFriendsContentState.Content -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                bottom = paddingValues.calculateBottomPadding() + 8.dp,
                            ),
                        ) {
                            renderUserItems(visibleMutualFriends) { user, sharedSuffixKey ->
                                navigator push UserProfileScreen(
                                    userProfileVO = UserProfileVo(user),
                                    sharedSuffixKey = sharedSuffixKey,
                                )
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
