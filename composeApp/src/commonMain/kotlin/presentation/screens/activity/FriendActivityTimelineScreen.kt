package io.github.vrcmteam.vrcm.presentation.screens.activity

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.presentation.compoments.AImage
import io.github.vrcmteam.vrcm.presentation.designsystem.AppActivityIndicator
import io.github.vrcmteam.vrcm.presentation.designsystem.AppButton
import io.github.vrcmteam.vrcm.presentation.designsystem.AppButtonStyle
import io.github.vrcmteam.vrcm.presentation.designsystem.AppFilterChip
import io.github.vrcmteam.vrcm.presentation.designsystem.AppIcon
import io.github.vrcmteam.vrcm.presentation.designsystem.AppIconButton
import io.github.vrcmteam.vrcm.presentation.designsystem.AppNavBar
import io.github.vrcmteam.vrcm.presentation.designsystem.AppScaffold
import io.github.vrcmteam.vrcm.presentation.designsystem.AppShapes
import io.github.vrcmteam.vrcm.presentation.designsystem.AppSurface
import io.github.vrcmteam.vrcm.presentation.designsystem.AppText
import io.github.vrcmteam.vrcm.presentation.designsystem.AppTheme
import io.github.vrcmteam.vrcm.presentation.navigation.AppDetailRoute
import io.github.vrcmteam.vrcm.presentation.navigation.LocalNavigator
import io.github.vrcmteam.vrcm.presentation.navigation.currentOrThrow
import io.github.vrcmteam.vrcm.presentation.screens.user.ActivityEventRow
import io.github.vrcmteam.vrcm.presentation.screens.user.UserProfileScreen
import io.github.vrcmteam.vrcm.presentation.screens.user.data.UserProfileVo
import io.github.vrcmteam.vrcm.presentation.screens.world.WorldProfileScreen
import io.github.vrcmteam.vrcm.presentation.screens.world.data.WorldProfileVo
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings
import io.github.vrcmteam.vrcm.presentation.supports.AppIcons
import io.github.vrcmteam.vrcm.service.FriendActivityEvent
import io.github.vrcmteam.vrcm.service.FriendActivityAccessType
import io.github.vrcmteam.vrcm.service.OfficialLinkType
import io.github.vrcmteam.vrcm.service.parseOfficialId
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@Serializable
object FriendActivityTimelineScreen : AppDetailRoute {
        @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val model: FriendActivityTimelineModel = koinViewModel()
        val state by model.state.collectAsState()
        val filter by model.filter.collectAsState()

        AppScaffold(
            topBar = {
                AppNavBar(
                    title = { AppText(strings.friendActivityTimelineTitle) },
                    navigationIcon = {
                        AppIconButton(onClick = { navigator.pop() }) {
                            AppIcon(
                                imageVector = AppIcons.ArrowBackIosNew,
                                contentDescription = strings.friendActivityBack,
                            )
                        }
                    },
                )
            },
        ) { padding ->
            FriendActivityTimelineContent(
                state = state,
                filter = filter,
                onFilterSelected = model::selectFilter,
                onRetry = model::retry,
                onLoadMore = model::loadMore,
                onRetryLoadMore = model::retryLoadMore,
                onUserClick = { event ->
                    navigator push UserProfileScreen(
                        UserProfileVo(
                            id = event.friendUserId,
                            displayName = event.displayName,
                            profileImageUrl = event.profileImageUrl,
                        )
                    )
                },
                onWorldClick = { event ->
                    val worldId = event.navigableWorldId() ?: return@FriendActivityTimelineContent
                    navigator push WorldProfileScreen(
                        WorldProfileVo(worldId = worldId, worldName = event.worldName.orEmpty())
                    )
                },
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
fun FriendActivityTimelineContent(
    state: FriendActivityTimelineState,
    filter: FriendActivityTimelineFilter,
    onFilterSelected: (FriendActivityTimelineFilter) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onRetryLoadMore: () -> Unit,
    onUserClick: (FriendActivityEvent) -> Unit,
    onWorldClick: (FriendActivityEvent) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    headerContent: (@Composable () -> Unit)? = null,
    controlsInList: Boolean = headerContent != null,
    showFilterControls: Boolean = true,
    bottomNavigationPadding: Dp = 0.dp,
) {
    val contentState = state as? FriendActivityTimelineState.Content
    LaunchedEffect(
        listState,
        contentState?.hasMore,
        contentState?.isLoadingMore,
        contentState?.loadMoreError,
    ) {
        val content = contentState ?: return@LaunchedEffect
        if (!content.hasMore || content.isLoadingMore || content.loadMoreError) return@LaunchedEffect
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            layoutInfo.totalItemsCount > 0 &&
                lastVisibleIndex >= layoutInfo.totalItemsCount - LOAD_MORE_THRESHOLD
        }.filter { it }.first()
        onLoadMore()
    }

    if (!controlsInList) {
        Column(modifier = modifier.fillMaxSize()) {
            if (showFilterControls) {
                ActivityTimelineFilters(filter, onFilterSelected)
            }
            ActivityTimelineObservedHint()
            ActivityTimelineList(
                state = state,
                filter = filter,
                onFilterSelected = onFilterSelected,
                onRetry = onRetry,
                onRetryLoadMore = onRetryLoadMore,
                onUserClick = onUserClick,
                onWorldClick = onWorldClick,
                listState = listState,
                modifier = Modifier.weight(1f),
                headerContent = headerContent,
                includeControls = false,
                showFilterControls = showFilterControls,
                bottomNavigationPadding = bottomNavigationPadding,
            )
        }
    } else {
        ActivityTimelineList(
            state = state,
            filter = filter,
            onFilterSelected = onFilterSelected,
            onRetry = onRetry,
            onRetryLoadMore = onRetryLoadMore,
            onUserClick = onUserClick,
            onWorldClick = onWorldClick,
            listState = listState,
            modifier = modifier,
            headerContent = headerContent,
            includeControls = true,
            showFilterControls = showFilterControls,
            bottomNavigationPadding = bottomNavigationPadding,
        )
    }
}

@Composable
private fun ActivityTimelineList(
    state: FriendActivityTimelineState,
    filter: FriendActivityTimelineFilter,
    onFilterSelected: (FriendActivityTimelineFilter) -> Unit,
    onRetry: () -> Unit,
    onRetryLoadMore: () -> Unit,
    onUserClick: (FriendActivityEvent) -> Unit,
    onWorldClick: (FriendActivityEvent) -> Unit,
    listState: LazyListState,
    modifier: Modifier,
    headerContent: (@Composable () -> Unit)? = null,
    includeControls: Boolean,
    showFilterControls: Boolean,
    bottomNavigationPadding: Dp,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().navigationBarsPadding(),
        state = listState,
        contentPadding = PaddingValues(
            top = if (includeControls) 0.dp else 16.dp,
            bottom = 16.dp + bottomNavigationPadding,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (headerContent != null) {
            item(key = "activity-header") {
                headerContent()
            }
        }
        if (includeControls && showFilterControls) {
            item(key = "activity-filters") {
                ActivityTimelineFilters(filter, onFilterSelected)
            }
        }
        if (includeControls) {
            item(key = "activity-observed-hint") {
                ActivityTimelineObservedHint()
            }
        }

        when (state) {
            FriendActivityTimelineState.Loading -> item(key = "activity-loading") {
                TimelineMessage(
                    if (includeControls) Modifier.padding(vertical = 48.dp)
                    else Modifier.fillParentMaxHeight(),
                ) {
                    AppActivityIndicator()
                }
            }
            FriendActivityTimelineState.Error -> item(key = "activity-error") {
                TimelineMessage(
                    if (includeControls) Modifier.padding(vertical = 48.dp)
                    else Modifier.fillParentMaxHeight(),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        AppText(strings.friendActivityTimelineError)
                        AppButton(onClick = onRetry, style = AppButtonStyle.Plain) { AppText(strings.retry) }
                    }
                }
            }
            is FriendActivityTimelineState.Content -> if (state.events.isEmpty()) {
                item(key = "activity-empty") {
                    TimelineMessage(
                        if (includeControls) Modifier.padding(vertical = 48.dp)
                        else Modifier.fillParentMaxHeight(),
                    ) {
                        AppText(strings.friendActivityTimelineEmpty)
                    }
                }
            } else {
                state.events.groupBy(FriendActivityEvent::activityDate).forEach { (date, dateEvents) ->
                    item(key = "date:$date") {
                        AppText(
                            text = date,
                            modifier = Modifier.padding(
                                start = 16.dp,
                                top = 8.dp,
                                end = 16.dp,
                                bottom = 2.dp,
                            ),
                            style = AppTheme.type.subheadlineEmphasized,
                            color = AppTheme.colors.secondaryLabel,
                        )
                    }
                    itemsIndexed(dateEvents, key = { _, event -> event.id }) { _, event ->
                        FriendTimelineEvent(
                            event = event,
                            onUserClick = { onUserClick(event) },
                            onWorldClick = if (event.navigableWorldId() != null) {
                                { onWorldClick(event) }
                            } else {
                                null
                            },
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                }
                if (state.isLoadingMore) {
                    item(key = "activity-load-more") {
                        Box(
                            Modifier.fillMaxWidth().padding(16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            AppActivityIndicator(Modifier.size(24.dp))
                        }
                    }
                } else if (state.loadMoreError) {
                    item(key = "activity-load-more-error") {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            AppText(
                                strings.friendActivityLoadMoreError,
                                Modifier.weight(1f),
                                color = AppTheme.colors.destructive,
                                style = AppTheme.type.caption1,
                            )
                            AppButton(onClick = onRetryLoadMore, style = AppButtonStyle.Plain) { AppText(strings.retry) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivityTimelineFilters(
    filter: FriendActivityTimelineFilter,
    onFilterSelected: (FriendActivityTimelineFilter) -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(FriendActivityTimelineFilter.entries, key = { it.name }) { option ->
            AppFilterChip(
                selected = option == filter,
                onClick = { onFilterSelected(option) },
                label = { AppText(option.label()) },
            )
        }
    }
}

@Composable
private fun ActivityTimelineObservedHint() {
    AppText(
        text = strings.friendActivityObservedHint,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        style = AppTheme.type.caption1,
        color = AppTheme.colors.secondaryLabel,
    )
}

@Composable
private fun TimelineMessage(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.padding(24.dp), contentAlignment = Alignment.Center) {
            content()
        }
    }
}

@Composable
private fun FriendTimelineEvent(
    event: FriendActivityEvent,
    onUserClick: () -> Unit,
    onWorldClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    AppSurface(
        modifier = modifier.fillMaxWidth(),
        shape = AppShapes.m,
        color = AppTheme.colors.secondaryGroupedBackground,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            AImage(
                imageData = event.profileImageUrl,
                contentDescription = event.displayName,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onUserClick),
            )
            Column(modifier = Modifier.weight(1f)) {
                AppText(
                    text = event.displayName.ifBlank { event.friendUserId },
                    modifier = Modifier.clickable(onClick = onUserClick),
                    style = AppTheme.type.body,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                ActivityEventRow(
                    event = event,
                    onWorldClick = onWorldClick,
                )
            }
        }
    }
}

@Composable
internal fun FriendActivityTimelineFilter.label(): String = when (this) {
    FriendActivityTimelineFilter.All -> strings.friendActivityFilterAll
    FriendActivityTimelineFilter.Presence -> strings.friendActivityFilterPresence
    FriendActivityTimelineFilter.Location -> strings.friendActivityFilterLocation
    FriendActivityTimelineFilter.Profile -> strings.friendActivityFilterProfile
    FriendActivityTimelineFilter.Meetup -> strings.friendActivityFilterMeetup
}

@OptIn(ExperimentalTime::class)
private fun FriendActivityEvent.activityDate(): String =
    Instant.fromEpochMilliseconds(occurredAtMillis)
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .date
        .toString()

internal fun FriendActivityEvent.navigableWorldId(): String? {
    if (accessType != FriendActivityAccessType.Public) return null
    val target = parseOfficialId(worldId.orEmpty()) ?: return null
    return target.id.takeIf { target.type == OfficialLinkType.World }
}

private const val LOAD_MORE_THRESHOLD = 6
