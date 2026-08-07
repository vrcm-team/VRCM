package io.github.vrcmteam.vrcm.presentation.screens.world

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.vrcmteam.vrcm.presentation.navigation.AppDetailRoute
import org.koin.compose.viewmodel.koinViewModel
import io.github.vrcmteam.vrcm.presentation.navigation.LocalNavigator
import io.github.vrcmteam.vrcm.presentation.navigation.currentOrThrow
import com.skydoves.landscapist.ImageOptions
import com.skydoves.landscapist.coil3.CoilImage
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

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val model: RecentWorldsScreenModel = koinViewModel()
        val scope = rememberCoroutineScope()
        val hiddenWorldCannotViewText = strings.hiddenWorldCannotView

        LaunchedEffect(Unit) {
            model.loadRecentWorlds()
        }

        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = strings.recentWorldsTitle,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(AppIcons.ArrowBackIosNew, contentDescription = "Back")
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
                    CircularProgressIndicator()
                }
            } else if (model.worlds.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = strings.recentWorldsEmpty,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
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
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                } else {
                                    TextButton(onClick = model::retryLoadMoreRecentWorlds) {
                                        Text(strings.retry)
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
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(sharedImageCacheKey) },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (world.id == "???") {
                Box(
                    modifier = Modifier
                        .size(80.dp, 45.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = AppIcons.VisibilityOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }
            } else {
                CoilImage(
                    imageModel = { sharedImageCacheKey },
                    imageOptions = ImageOptions(contentScale = ContentScale.Crop),
                    imageLoader = { koinInject() },
                    modifier = Modifier
                        .sharedBoundsBy("${world.id}WorldImage")
                        .size(80.dp, 45.dp)
                        .clip(RoundedCornerShape(8.dp)),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (world.id == "???") world.favoriteId ?: world.name else world.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (world.id == "???") strings.hiddenWorld else world.authorName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}
