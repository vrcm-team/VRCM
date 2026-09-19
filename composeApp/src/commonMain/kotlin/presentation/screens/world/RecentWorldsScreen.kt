package io.github.vrcmteam.vrcm.presentation.screens.world

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.ImageLoader
import coil3.compose.AsyncImage
import io.github.vrcmteam.vrcm.presentation.designsystem.*
import io.github.vrcmteam.vrcm.presentation.navigation.AppDetailRoute
import org.koin.compose.viewmodel.koinViewModel
import io.github.vrcmteam.vrcm.presentation.navigation.LocalNavigator
import io.github.vrcmteam.vrcm.presentation.navigation.currentOrThrow
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.network.api.worlds.WorldsApi
import io.github.vrcmteam.vrcm.network.api.worlds.data.WorldData
import io.github.vrcmteam.vrcm.presentation.compoments.ToastText
import io.github.vrcmteam.vrcm.presentation.compoments.sharedBoundsBy
import io.github.vrcmteam.vrcm.presentation.compoments.shouldLoadNextPage
import io.github.vrcmteam.vrcm.presentation.screens.world.data.WorldProfileVo
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings
import io.github.vrcmteam.vrcm.presentation.supports.AppIcons
import io.github.vrcmteam.vrcm.service.AuthService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject

class RecentWorldsScreenModel(
    private val authService: AuthService,
    private val worldsApi: WorldsApi,
) : ViewModel() {

    private val _worlds = mutableStateOf<List<WorldData>>(emptyList())
    val worlds by _worlds

    private val _isLoading = mutableStateOf(true)
    val isLoading by _isLoading

    private val _isLoadingMore = mutableStateOf(false)
    val isLoadingMore by _isLoadingMore

    private val _endReached = mutableStateOf(false)
    val endReached by _endReached

    private var pagingState = RecentWorldPagingState<WorldData>()
    private var initialLoadStarted = false
    private var refreshInProgress = false
    val loadMoreFailed: Boolean get() = pagingState.failedOffset != null

    fun loadRecentWorlds() {
        val showLoading = !initialLoadStarted
        initialLoadStarted = true
        loadPage(reset = true, showLoading = showLoading)
    }

    fun loadMoreRecentWorlds() {
        if (!pagingState.canAutoLoadNextPage()) return
        loadPage(reset = false)
    }

    fun retryLoadMoreRecentWorlds() {
        if (!loadMoreFailed) return
        pagingState = prepareRecentWorldPageRetry(pagingState)
        loadPage(reset = false)
    }

    private fun loadPage(reset: Boolean, showLoading: Boolean = false) {
        if (refreshInProgress || _isLoadingMore.value ||
            (!reset && (_isLoading.value || _endReached.value))
        ) return

        if (reset) {
            refreshInProgress = true
            if (showLoading) {
                _worlds.value = emptyList()
                _endReached.value = false
                _isLoading.value = true
            }
        } else {
            _isLoadingMore.value = true
        }

        val pageOffset = if (reset) 0 else pagingState.nextOffset
        viewModelScope.launch(Dispatchers.IO) {
            try {
                authService.reTryAuthCatching {
                    worldsApi.getRecentWorlds(n = RECENT_WORLDS_PAGE_SIZE, offset = pageOffset)
                }.onSuccess { page ->
                    pagingState = appendRecentWorldPage(
                        current = if (reset) RecentWorldPagingState() else pagingState,
                        page = page,
                        pageSize = RECENT_WORLDS_PAGE_SIZE,
                        keySelector = { it.id },
                    )
                    _worlds.value = pagingState.items
                    _endReached.value = pagingState.endReached
                }.onFailure {
                    if (!reset) {
                        pagingState = markRecentWorldPageFailed(pagingState)
                    }
                    SharedFlowCentre.toastText.emit(ToastText.Error(it.message.toString()))
                }
            } finally {
                if (reset) refreshInProgress = false
                _isLoading.value = false
                _isLoadingMore.value = false
            }
        }
    }

    private companion object {
        const val RECENT_WORLDS_PAGE_SIZE = 50
    }
}

internal data class RecentWorldPagingState<T>(
    val items: List<T> = emptyList(),
    val nextOffset: Int = 0,
    val endReached: Boolean = false,
    val failedOffset: Int? = null,
)

internal fun <T, K> appendRecentWorldPage(
    current: RecentWorldPagingState<T>,
    page: List<T>,
    pageSize: Int,
    keySelector: (T) -> K,
): RecentWorldPagingState<T> = RecentWorldPagingState(
    items = (current.items + page).distinctBy(keySelector),
    nextOffset = current.nextOffset + page.size,
    endReached = page.size < pageSize,
)

internal fun <T> markRecentWorldPageFailed(
    current: RecentWorldPagingState<T>,
): RecentWorldPagingState<T> = current.copy(failedOffset = current.nextOffset)

internal fun <T> prepareRecentWorldPageRetry(
    current: RecentWorldPagingState<T>,
): RecentWorldPagingState<T> = current.copy(failedOffset = null)

internal fun <T> RecentWorldPagingState<T>.canAutoLoadNextPage(): Boolean =
    failedOffset != nextOffset

@Serializable
object RecentWorldsScreen : AppDetailRoute {

        @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val model: RecentWorldsScreenModel = koinViewModel()
        val scope = rememberCoroutineScope()
        val hiddenWorldCannotViewText = strings.hiddenWorldCannotView

        LaunchedEffect(Unit) {
            model.loadRecentWorlds()
        }

        AppScaffold(
            containerColor = AppTheme.colors.systemBackground,
            topBar = {
                AppNavBar(
                    edgeColor = AppTheme.colors.systemBackground,
                    title = {
                        AppText(
                            text = strings.recentWorldsTitle,
                        )
                    },
                    navigationIcon = {
                        AppIconButton(onClick = { navigator.pop() }) {
                            AppIcon(AppIcons.ArrowBackIosNew, contentDescription = "Back")
                        }
                    }
                )
            }
        ) { paddingValues ->
            if (model.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    AppActivityIndicator()
                }
            } else if (model.worlds.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    AppText(
                        text = strings.recentWorldsEmpty,
                        style = AppTheme.type.body,
                        color = AppTheme.colors.secondaryLabel,
                    )
                }
            } else {
                val listState = rememberLazyListState()
                LaunchedEffect(listState) {
                    snapshotFlow {
                        val layoutInfo = listState.layoutInfo
                        val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
                        lastVisibleIndex to model.worlds.size
                    }.distinctUntilChanged().collect { (lastVisibleIndex, totalItemsCount) ->
                        if (shouldLoadNextPage(lastVisibleIndex, totalItemsCount)) {
                            model.loadMoreRecentWorlds()
                        }
                    }
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    items(model.worlds, key = { it.id }) { world ->
                        RecentWorldItem(world) { sharedImageCacheKey ->
                            if (world.id == "???") {
                                scope.launch {
                                    SharedFlowCentre.toastText.emit(ToastText.Info(hiddenWorldCannotViewText))
                                }
                            } else {
                                navigator.push(
                                    WorldProfileScreen(
                                        worldProfileVO = WorldProfileVo(world),
                                        sharedImageCacheKey = sharedImageCacheKey,
                                    )
                                )
                            }
                        }
                    }
                    if (model.isLoadingMore || model.loadMoreFailed) {
                        item(key = "recent-worlds-loading") {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (model.isLoadingMore) {
                                    AppActivityIndicator(modifier = Modifier.size(24.dp))
                                } else {
                                    AppButton(onClick = model::retryLoadMoreRecentWorlds, style = AppButtonStyle.Plain) {
                                        AppText(strings.retry)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun RecentWorldItem(world: WorldData, onClick: (String?) -> Unit) {
    val sharedImageCacheKey = (world.thumbnailImageUrl ?: world.imageUrl)
        .orEmpty()
        .ifBlank { null }
    // iOS「信息」那种列表行：通栏、没有卡片底，行间是一条从文字起的发丝线
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(sharedImageCacheKey) },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = AppSpacing.row, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(RecentWorldThumbnailGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (world.id == "???") {
                Box(
                    modifier = Modifier
                        .size(RecentWorldThumbnailSize)
                        .clip(AppShapes.s)
                        .background(AppTheme.colors.fill),
                    contentAlignment = Alignment.Center
                ) {
                    AppIcon(
                        imageVector = AppIcons.VisibilityOff,
                        contentDescription = null,
                        tint = AppTheme.colors.secondaryLabel,
                        modifier = Modifier.size(24.dp)
                    )
                }
            } else {
                AsyncImage(
                    model = sharedImageCacheKey,
                    contentDescription = null,
                    imageLoader = koinInject<ImageLoader>(),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .sharedBoundsBy("${world.id}WorldImage")
                        .size(RecentWorldThumbnailSize)
                        .clip(AppShapes.s),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                AppText(
                    text = if (world.id == "???") world.favoriteId ?: world.name else world.name,
                    style = AppTheme.type.body,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                AppText(
                    text = if (world.id == "???") strings.hiddenWorld else world.authorName,
                    style = AppTheme.type.footnote,
                    color = AppTheme.colors.secondaryLabel,
                    maxLines = 1,
                )
            }
        }
        AppDivider(Modifier.padding(start = AppSpacing.row + RecentWorldThumbnailSize.width + RecentWorldThumbnailGap))
    }
}

private val RecentWorldThumbnailSize = DpSize(80.dp, 45.dp)
private val RecentWorldThumbnailGap = 12.dp
