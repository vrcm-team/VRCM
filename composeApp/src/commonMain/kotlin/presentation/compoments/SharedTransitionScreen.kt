package io.github.vrcmteam.vrcm.presentation.compoments

import androidx.compose.animation.*
import androidx.compose.animation.SharedTransitionScope.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation.BackNavigationBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Alignment.Companion.Center
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.Scene
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import io.github.vrcmteam.vrcm.presentation.animations.DefaultBoundsTransform
import io.github.vrcmteam.vrcm.presentation.animations.DefaultScreenTransition
import io.github.vrcmteam.vrcm.presentation.animations.ParentClip
import io.github.vrcmteam.vrcm.presentation.navigation.AppNavigator
import io.github.vrcmteam.vrcm.presentation.navigation.AppRoute
import io.github.vrcmteam.vrcm.presentation.navigation.LocalBackNavigationPolicy
import io.github.vrcmteam.vrcm.presentation.navigation.LocalPaneSharedTransitionsEnabled
import io.github.vrcmteam.vrcm.presentation.navigation.adaptivePaneMetadata
import io.github.vrcmteam.vrcm.presentation.navigation.rememberAppListDetailSceneStrategy
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings
import io.github.vrcmteam.vrcm.presentation.supports.AppIcons

private const val AppRouteMetadataKey = "vrcm:app-route"

internal val Scene<AppRoute>.route: AppRoute
    get() = entries.last().metadata.getValue(AppRouteMetadataKey) as AppRoute

internal fun createAppNavEntry(
    route: AppRoute,
    metadata: Map<String, Any>,
    content: @Composable (AppRoute) -> Unit,
): NavEntry<AppRoute> = NavEntry(
    key = route,
    contentKey = route.key,
    metadata = metadata,
    content = content,
)

@OptIn(
    ExperimentalSharedTransitionApi::class,
    ExperimentalComposeUiApi::class,
    ExperimentalMaterial3AdaptiveApi::class,
)
@Composable
fun SharedTransitionScreen(
    navigator: AppNavigator,
    modifier: Modifier = Modifier,
    transitionSpec: AnimatedContentTransitionScope<Scene<AppRoute>>.() -> ContentTransform = {
        DefaultScreenTransition
    },
    popTransitionSpec: AnimatedContentTransitionScope<Scene<AppRoute>>.() -> ContentTransform = {
        DefaultScreenTransition
    },
    content: @Composable (AppRoute) -> Unit = { it.Content() },
) {
    val backNavigationPolicy = LocalBackNavigationPolicy.current
    val saveableStateDecorator = rememberSaveableStateHolderNavEntryDecorator<AppRoute>()
    val viewModelStoreDecorator = rememberViewModelStoreNavEntryDecorator<AppRoute>()
    val paneDragInteractionSource = remember { MutableInteractionSource() }
    val interceptionState = rememberNavigationEventState(NavigationEventInfo.None)
    val listDetailStrategy = rememberAppListDetailSceneStrategy<AppRoute>(
        backNavigationBehavior = BackNavigationBehavior.PopLatest,
        paneExpansionDragHandle = { state ->
            Box(
                modifier = Modifier
                    .width(24.dp)
                    .fillMaxHeight()
                    .paneExpansionDraggable(
                        state = state,
                        minTouchTargetSize = 48.dp,
                        interactionSource = paneDragInteractionSource,
                    ),
                contentAlignment = Center,
            ) {
                VerticalDivider(
                    modifier = Modifier.fillMaxHeight(),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
            }
        },
    )

    SharedTransitionLayout(modifier) {
        NavDisplay(
            backStack = navigator.backStack,
            modifier = modifier,
            onBack = { navigator.pop() },
            entryDecorators = listOf(saveableStateDecorator, viewModelStoreDecorator),
            sceneStrategy = listDetailStrategy,
            sharedTransitionScope = this@SharedTransitionLayout,
            transitionSpec = transitionSpec,
            popTransitionSpec = popTransitionSpec,
            entryProvider = { route ->
                createAppNavEntry(
                    route = route,
                    metadata = mapOf(AppRouteMetadataKey to route) +
                        route.adaptivePaneMetadata { EmptyDetailPane() },
                ) { screen ->
                    val sharedTransitionScope = this@SharedTransitionLayout.takeIf {
                        LocalPaneSharedTransitionsEnabled.current
                    }
                    CompositionLocalProvider(
                        LocalSharedTransitionScreenScope provides sharedTransitionScope,
                        LocalAnimatedVisibilityScope provides LocalNavAnimatedContentScope.current,
                    ) {
                        content(screen)
                    }
                }
            },
        )
        NavigationBackHandler(
            state = interceptionState,
            isBackEnabled = backNavigationPolicy.shouldInterceptBack(),
            onBackCompleted = {
                backNavigationPolicy.handleBack(canNavigateBack = false) {}
            },
        )
    }
}

@Composable
private fun EmptyDetailPane() {
    val locale = strings
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        Icon(
            imageVector = AppIcons.Mirror,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.32f),
        )
        Text(
            text = locale.widePaneEmptyTitle,
            modifier = Modifier.widthIn(max = 360.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = locale.widePaneEmptyHint,
            modifier = Modifier.widthIn(max = 360.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
    }
}


@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScreenScope: ProvidableCompositionLocal<SharedTransitionScope?> =
    compositionLocalOf { null }

/**
 * 用于区分多个同级页面共享元素错位问题:比如两个Page中key相同的元素会在滑动时错位
 */
val LocalSharedSuffixKey: ProvidableCompositionLocal<String> =
    staticCompositionLocalOf { "" }

val LocalAnimatedVisibilityScope: ProvidableCompositionLocal<AnimatedVisibilityScope> =
    staticCompositionLocalOf { error("AnimatedVisibilityScope is not provided") }

@OptIn(ExperimentalSharedTransitionApi::class)
internal val SharedTextBoundsResizeMode: ResizeMode =
    ResizeMode.scaleToBounds(
        contentScale = ContentScale.FillWidth,
        alignment = Center,
    )

internal fun sharedContentKey(key: String, suffixKey: String, useSuffixKey: Boolean): String =
    if (!useSuffixKey || suffixKey.isBlank()) key else "$key:$suffixKey"

internal fun groupNameSharedKey(groupId: String): String = "${groupId}GroupName"

/** 标记可用于 Compose 共享元素配对的类型化 key。 */
interface SharedElementKey

private data class StringSharedElementKey(val value: String) : SharedElementKey

/** 使用导航时生成的唯一 [token] 配对容器变换的源与目标。 */
data class ContainerTransformKey(val token: String) : SharedElementKey

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedBoundsReveal(
    sharedElementKey: SharedElementKey,
    sharedTransitionScope: SharedTransitionScope = LocalSharedTransitionScreenScope.current
        ?: error("SharedTransitionScope is not provided"),
    animatedVisibilityScope: AnimatedVisibilityScope = LocalAnimatedVisibilityScope.current,
    boundsTransform: BoundsTransform = DefaultBoundsTransform,
    resizeMode: ResizeMode = ResizeMode.RemeasureToBounds,
    clipInOverlayDuringTransition: OverlayClip = ParentClip,
    renderInOverlayDuringTransition: Boolean = true,
    zIndexInOverlay: Float = 0f,
): Modifier = sharedBoundsWithDefaults(
    sharedElementKey = sharedElementKey,
    sharedTransitionScope = sharedTransitionScope,
    animatedVisibilityScope = animatedVisibilityScope,
    boundsTransform = boundsTransform,
    resizeMode = resizeMode,
    clipInOverlayDuringTransition = clipInOverlayDuringTransition,
    renderInOverlayDuringTransition = renderInOverlayDuringTransition,
    zIndexInOverlay = zIndexInOverlay,
)

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedBoundsWithDefaults(
    sharedElementKey: SharedElementKey,
    sharedTransitionScope: SharedTransitionScope = LocalSharedTransitionScreenScope.current
        ?: error("SharedTransitionScope is not provided"),
    animatedVisibilityScope: AnimatedVisibilityScope = LocalAnimatedVisibilityScope.current,
    boundsTransform: BoundsTransform = DefaultBoundsTransform,
    resizeMode: ResizeMode = ResizeMode.RemeasureToBounds,
    clipInOverlayDuringTransition: OverlayClip = ParentClip,
    renderInOverlayDuringTransition: Boolean = true,
    zIndexInOverlay: Float = 0f,
): Modifier = with(sharedTransitionScope) {
    this@sharedBoundsWithDefaults.sharedBounds(
        sharedContentState = rememberSharedContentState(sharedElementKey),
        animatedVisibilityScope = animatedVisibilityScope,
        boundsTransform = boundsTransform,
        resizeMode = resizeMode,
        clipInOverlayDuringTransition = clipInOverlayDuringTransition,
        renderInOverlayDuringTransition = renderInOverlayDuringTransition,
        zIndexInOverlay = zIndexInOverlay,
    )
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedElementReveal(
    sharedElementKey: SharedElementKey,
    sharedTransitionScope: SharedTransitionScope = LocalSharedTransitionScreenScope.current
        ?: error("SharedTransitionScope is not provided"),
    animatedVisibilityScope: AnimatedVisibilityScope = LocalAnimatedVisibilityScope.current,
    boundsTransform: BoundsTransform = DefaultBoundsTransform,
    clipInOverlayDuringTransition: OverlayClip = ParentClip,
    renderInOverlayDuringTransition: Boolean = true,
    zIndexInOverlay: Float = 0f,
): Modifier = sharedElementWithDefaults(
    sharedElementKey = sharedElementKey,
    sharedTransitionScope = sharedTransitionScope,
    animatedVisibilityScope = animatedVisibilityScope,
    boundsTransform = boundsTransform,
    clipInOverlayDuringTransition = clipInOverlayDuringTransition,
    renderInOverlayDuringTransition = renderInOverlayDuringTransition,
    zIndexInOverlay = zIndexInOverlay,
)

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedElementWithDefaults(
    sharedElementKey: SharedElementKey,
    sharedTransitionScope: SharedTransitionScope = LocalSharedTransitionScreenScope.current
        ?: error("SharedTransitionScope is not provided"),
    animatedVisibilityScope: AnimatedVisibilityScope = LocalAnimatedVisibilityScope.current,
    boundsTransform: BoundsTransform = DefaultBoundsTransform,
    clipInOverlayDuringTransition: OverlayClip = ParentClip,
    renderInOverlayDuringTransition: Boolean = true,
    zIndexInOverlay: Float = 0f,
): Modifier = with(sharedTransitionScope) {
    this@sharedElementWithDefaults.sharedElement(
        sharedContentState = rememberSharedContentState(sharedElementKey),
        animatedVisibilityScope = animatedVisibilityScope,
        boundsTransform = boundsTransform,
        clipInOverlayDuringTransition = clipInOverlayDuringTransition,
        renderInOverlayDuringTransition = renderInOverlayDuringTransition,
        zIndexInOverlay = zIndexInOverlay,
    )
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedElementBy(
    key: String,
    useSuffixKey: Boolean = true,
    suffixKey: String? = null,
    sharedTransitionScope: SharedTransitionScope? = LocalSharedTransitionScreenScope.current,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    boundsTransform: BoundsTransform = DefaultBoundsTransform,
    renderInOverlayDuringTransition: Boolean = true,
    zIndexInOverlay: Float = 0f,
    clipInOverlayDuringTransition: OverlayClip = ParentClip,
): Modifier {
    val transitionScope = sharedTransitionScope ?: return this
    val resolvedSuffixKey = suffixKey ?: LocalSharedSuffixKey.current
    val visibilityScope = animatedVisibilityScope ?: LocalAnimatedVisibilityScope.current
    return sharedElementWithDefaults(
        sharedElementKey = StringSharedElementKey(
            sharedContentKey(key, resolvedSuffixKey, useSuffixKey),
        ),
        sharedTransitionScope = transitionScope,
        animatedVisibilityScope = visibilityScope,
        boundsTransform = boundsTransform,
        renderInOverlayDuringTransition = renderInOverlayDuringTransition,
        zIndexInOverlay = zIndexInOverlay,
        clipInOverlayDuringTransition = clipInOverlayDuringTransition,
    )
}


@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedBoundsBy(
    key: String,
    useSuffixKey: Boolean = true,
    suffixKey: String? = null,
    sharedTransitionScope: SharedTransitionScope? = LocalSharedTransitionScreenScope.current,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    resizeMode: ResizeMode = ResizeMode.RemeasureToBounds,
    boundsTransform: BoundsTransform = DefaultBoundsTransform,
    renderInOverlayDuringTransition: Boolean = true,
    zIndexInOverlay: Float = 0f,
    clipInOverlayDuringTransition: OverlayClip = ParentClip,
): Modifier {
    val transitionScope = sharedTransitionScope ?: return this
    val resolvedSuffixKey = suffixKey ?: LocalSharedSuffixKey.current
    val visibilityScope = animatedVisibilityScope ?: LocalAnimatedVisibilityScope.current
    return sharedBoundsWithDefaults(
        sharedElementKey = StringSharedElementKey(
            sharedContentKey(key, resolvedSuffixKey, useSuffixKey),
        ),
        sharedTransitionScope = transitionScope,
        animatedVisibilityScope = visibilityScope,
        resizeMode = resizeMode,
        boundsTransform = boundsTransform,
        renderInOverlayDuringTransition = renderInOverlayDuringTransition,
        zIndexInOverlay = zIndexInOverlay,
        clipInOverlayDuringTransition = clipInOverlayDuringTransition,
    )
}

/**
 * 将当前容器登记为 [token] 对应的共享边界；没有共享过渡作用域时保持原 Modifier 不变。
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedContainerTransform(
    token: String,
    sharedTransitionScope: SharedTransitionScope? = LocalSharedTransitionScreenScope.current,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    boundsTransform: BoundsTransform = DefaultBoundsTransform,
    resizeMode: ResizeMode = ResizeMode.RemeasureToBounds,
    clipInOverlayDuringTransition: OverlayClip = ParentClip,
    renderInOverlayDuringTransition: Boolean = true,
): Modifier {
    val transitionScope = sharedTransitionScope ?: return this
    val visibilityScope = animatedVisibilityScope ?: LocalAnimatedVisibilityScope.current
    return sharedBoundsReveal(
        sharedElementKey = ContainerTransformKey(token),
        sharedTransitionScope = transitionScope,
        animatedVisibilityScope = visibilityScope,
        boundsTransform = boundsTransform,
        resizeMode = resizeMode,
        clipInOverlayDuringTransition = clipInOverlayDuringTransition,
        renderInOverlayDuringTransition = renderInOverlayDuringTransition,
    )
}
