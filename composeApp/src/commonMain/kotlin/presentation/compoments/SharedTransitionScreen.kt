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
                    CompositionLocalProvider(
                        LocalSharedTransitionScreenScope provides this@SharedTransitionLayout,
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
val LocalSharedTransitionScreenScope: ProvidableCompositionLocal<SharedTransitionScope> =
    staticCompositionLocalOf { error("SharedTransitionScope is not provided") }

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

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedElementBy(
    key: String,
    useSuffixKey: Boolean = true,
    sharedTransitionScope: SharedTransitionScope = LocalSharedTransitionScreenScope.current,
    animatedVisibilityScope: AnimatedVisibilityScope = LocalAnimatedVisibilityScope.current,
    boundsTransform: BoundsTransform = DefaultBoundsTransform,
    renderInOverlayDuringTransition: Boolean = true,
    zIndexInOverlay: Float = 0f,
    clipInOverlayDuringTransition: OverlayClip = ParentClip,
): Modifier =
    with(sharedTransitionScope) {
        val suffixKey = LocalSharedSuffixKey.current
        this@sharedElementBy.sharedElement(
            sharedContentState = rememberSharedContentState(sharedContentKey(key, suffixKey, useSuffixKey)),
            animatedVisibilityScope = animatedVisibilityScope,
            boundsTransform = boundsTransform,
            renderInOverlayDuringTransition = renderInOverlayDuringTransition,
            zIndexInOverlay = zIndexInOverlay,
            clipInOverlayDuringTransition = clipInOverlayDuringTransition
        )
    }


@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedBoundsBy(
    key: String,
    useSuffixKey: Boolean = true,
    sharedTransitionScope: SharedTransitionScope = LocalSharedTransitionScreenScope.current,
    animatedVisibilityScope: AnimatedVisibilityScope = LocalAnimatedVisibilityScope.current,
    resizeMode: ResizeMode = ResizeMode.RemeasureToBounds,
    boundsTransform: BoundsTransform = DefaultBoundsTransform,
    renderInOverlayDuringTransition: Boolean = true,
    zIndexInOverlay: Float = 0f,
    clipInOverlayDuringTransition: OverlayClip = ParentClip,
): Modifier =
    with(sharedTransitionScope) {
        val suffixKey = LocalSharedSuffixKey.current
        this@sharedBoundsBy.sharedBounds(
            sharedContentState = rememberSharedContentState(sharedContentKey(key, suffixKey, useSuffixKey)),
            animatedVisibilityScope = animatedVisibilityScope,
            resizeMode = resizeMode,
            boundsTransform = boundsTransform,
            renderInOverlayDuringTransition = renderInOverlayDuringTransition,
            zIndexInOverlay = zIndexInOverlay,
            clipInOverlayDuringTransition = clipInOverlayDuringTransition
        )
    }
