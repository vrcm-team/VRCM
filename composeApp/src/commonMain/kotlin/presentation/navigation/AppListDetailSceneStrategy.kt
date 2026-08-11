/*
 * Copyright 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.vrcmteam.vrcm.presentation.navigation

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldDefaults
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.layout.MutableThreePaneScaffoldState
import androidx.compose.material3.adaptive.layout.PaneAdaptedValue
import androidx.compose.material3.adaptive.layout.PaneExpansionState
import androidx.compose.material3.adaptive.layout.PaneScaffoldDirective
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldAdaptStrategies
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldDestinationItem
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldRole
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldScope
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldState
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldValue
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.layout.calculateThreePaneScaffoldValue
import androidx.compose.material3.adaptive.navigation.BackNavigationBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntRect
import androidx.window.core.layout.WindowSizeClass
import androidx.window.core.layout.computeWindowSizeClass
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.NavigationEventTransitionState
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import io.github.vrcmteam.vrcm.presentation.animations.defaultSpring
import io.github.vrcmteam.vrcm.presentation.adaptive.LocalAppContentSize

private val PaneBoundsAnimationSpec = defaultSpring(
    visibilityThreshold = IntRect(1, 1, 1, 1),
)

internal val LocalPaneSharedTransitionsEnabled = staticCompositionLocalOf { true }

@ExperimentalMaterial3AdaptiveApi
@Composable
internal fun <T : Any> rememberAppListDetailSceneStrategy(
    backNavigationBehavior: BackNavigationBehavior = BackNavigationBehavior.PopLatest,
    directive: PaneScaffoldDirective = currentAppPaneScaffoldDirective(),
    adaptStrategies: ThreePaneScaffoldAdaptStrategies =
        ListDetailPaneScaffoldDefaults.adaptStrategies(),
    paneExpansionDragHandle: (@Composable ThreePaneScaffoldScope.(PaneExpansionState) -> Unit)? =
        null,
    paneExpansionState: PaneExpansionState? = null,
): AppListDetailSceneStrategy<T> =
    remember(
        backNavigationBehavior,
        directive,
        adaptStrategies,
        paneExpansionDragHandle,
        paneExpansionState,
    ) {
        AppListDetailSceneStrategy(
            backNavigationBehavior = backNavigationBehavior,
            directive = directive,
            adaptStrategies = adaptStrategies,
            paneExpansionDragHandle = paneExpansionDragHandle,
            paneExpansionState = paneExpansionState,
        )
    }

@Composable
@ExperimentalMaterial3AdaptiveApi
private fun currentAppPaneScaffoldDirective(): PaneScaffoldDirective {
    val contentSize = LocalAppContentSize.current
    val windowPosture = currentWindowAdaptiveInfo().windowPosture
    val windowSizeClass = WindowSizeClass.BREAKPOINTS_V1.computeWindowSizeClass(
        widthDp = contentSize.width.value,
        heightDp = contentSize.height.value,
    )
    return calculatePaneScaffoldDirective(
        WindowAdaptiveInfo(
            windowSizeClass = windowSizeClass,
            windowPosture = windowPosture,
        )
    )
}

@ExperimentalMaterial3AdaptiveApi
internal class AppListDetailSceneStrategy<T : Any>(
    val backNavigationBehavior: BackNavigationBehavior,
    val directive: PaneScaffoldDirective,
    val adaptStrategies: ThreePaneScaffoldAdaptStrategies =
        ListDetailPaneScaffoldDefaults.adaptStrategies(),
    val paneExpansionDragHandle:
        (@Composable ThreePaneScaffoldScope.(PaneExpansionState) -> Unit)? = null,
    val paneExpansionState: PaneExpansionState? = null,
) : SceneStrategy<T> {
    override fun SceneStrategyScope<T>.calculateScene(entries: List<NavEntry<T>>): Scene<T>? {
        val lastPaneMetadata = getPaneMetadata(entries.last()) ?: return null
        val sceneKey = lastPaneMetadata.sceneKey
        val scaffoldEntries = mutableListOf<NavEntry<T>>()
        val scaffoldEntryIndices = mutableListOf<Int>()
        val entriesAsNavItems = mutableListOf<ThreePaneScaffoldDestinationItem<Any>>()
        var detailPlaceholder: (@Composable ThreePaneScaffoldScope.() -> Unit)? = null

        var index = entries.lastIndex
        while (index >= 0) {
            val entry = entries[index]
            val paneMetadata = getPaneMetadata(entry) ?: break
            if (paneMetadata.sceneKey == sceneKey) {
                scaffoldEntryIndices.add(0, index)
                scaffoldEntries.add(0, entry)
                entriesAsNavItems.add(
                    0,
                    ThreePaneScaffoldDestinationItem(
                        pane = paneMetadata.role,
                        contentKey = entry.contentKey,
                    ),
                )
                if (paneMetadata is ListPaneMetadata) {
                    detailPlaceholder = paneMetadata.detailPlaceholder
                }
            }
            index--
        }

        if (scaffoldEntries.isEmpty()) return null
        val resolvedDetailPlaceholder = detailPlaceholder ?: return null

        val scene = AppListDetailScene(
            key = sceneKey,
            onBack = onBack,
            backNavigationBehavior = backNavigationBehavior,
            directive = directive,
            adaptStrategies = adaptStrategies,
            allEntries = entries,
            scaffoldEntries = scaffoldEntries,
            scaffoldEntryIndices = scaffoldEntryIndices,
            entriesAsNavItems = entriesAsNavItems,
            getPaneRole = { getPaneMetadata(it)?.role },
            detailPlaceholder = resolvedDetailPlaceholder,
            paneExpansionDragHandle = paneExpansionDragHandle,
            paneExpansionState = paneExpansionState,
        )

        return scene.takeIf { it.currentScaffoldValue.paneCount > 1 }
    }

    private sealed interface PaneMetadata {
        val sceneKey: Any
        val role: ThreePaneScaffoldRole
    }

    private class ListPaneMetadata(
        override val sceneKey: Any,
        val detailPlaceholder: @Composable ThreePaneScaffoldScope.() -> Unit,
    ) : PaneMetadata {
        override val role: ThreePaneScaffoldRole = ListDetailPaneScaffoldRole.List
    }

    private class DetailPaneMetadata(
        override val sceneKey: Any,
    ) : PaneMetadata {
        override val role: ThreePaneScaffoldRole = ListDetailPaneScaffoldRole.Detail
    }

    internal companion object {
        private const val PaneMetadataKey = "vrcm:list-detail-pane"

        fun listPane(
            sceneKey: Any = Unit,
            detailPlaceholder: @Composable ThreePaneScaffoldScope.() -> Unit = {},
        ): Map<String, Any> = mapOf(
            PaneMetadataKey to ListPaneMetadata(sceneKey, detailPlaceholder),
        )

        fun detailPane(sceneKey: Any = Unit): Map<String, Any> = mapOf(
            PaneMetadataKey to DetailPaneMetadata(sceneKey),
        )

        private fun <T : Any> getPaneMetadata(entry: NavEntry<T>): PaneMetadata? =
            entry.metadata[PaneMetadataKey] as? PaneMetadata
    }
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
private class AppListDetailScene<T : Any>(
    override val key: Any,
    val onBack: () -> Unit,
    val backNavigationBehavior: BackNavigationBehavior,
    val directive: PaneScaffoldDirective,
    val adaptStrategies: ThreePaneScaffoldAdaptStrategies,
    val allEntries: List<NavEntry<T>>,
    val scaffoldEntries: List<NavEntry<T>>,
    val scaffoldEntryIndices: List<Int>,
    val entriesAsNavItems: List<ThreePaneScaffoldDestinationItem<Any>>,
    val getPaneRole: (NavEntry<T>) -> ThreePaneScaffoldRole?,
    val detailPlaceholder: @Composable ThreePaneScaffoldScope.() -> Unit,
    val paneExpansionDragHandle:
        (@Composable ThreePaneScaffoldScope.(PaneExpansionState) -> Unit)?,
    val paneExpansionState: PaneExpansionState?,
) : Scene<T> {
    override val entries: List<NavEntry<T>>
        get() = scaffoldEntries

    override val previousEntries: List<NavEntry<T>>
        get() = onBackResult.previousEntries

    val currentScaffoldValue: ThreePaneScaffoldValue
        get() = calculateScaffoldValue(entriesAsNavItems)

    private class OnBackResult<T : Any>(
        val previousScaffoldValue: ThreePaneScaffoldValue?,
        val previousEntries: List<NavEntry<T>>,
    )

    private val onBackResult: OnBackResult<T> = calculateOnBackResult()

    private fun calculateOnBackResult(): OnBackResult<T> {
        val previousDestinationRelativeIndex = getPreviousDestinationIndex()
        val previousDestinationAbsoluteIndex =
            if (previousDestinationRelativeIndex < 0) {
                scaffoldEntryIndices.first() - 1
            } else {
                scaffoldEntryIndices[previousDestinationRelativeIndex]
            }
        val scaffoldEntryIndexSet = scaffoldEntryIndices.toSet()

        for (index in allEntries.lastIndex downTo 0) {
            if (index !in scaffoldEntryIndexSet) {
                return OnBackResult(
                    previousScaffoldValue = null,
                    previousEntries = ArrayList(allEntries.subList(0, index + 1)),
                )
            }
            if (index == previousDestinationAbsoluteIndex) {
                return OnBackResult(
                    previousScaffoldValue = calculateScaffoldValue(
                        entriesAsNavItems.subList(0, previousDestinationRelativeIndex + 1),
                    ),
                    previousEntries = ArrayList(allEntries.subList(0, index + 1)),
                )
            }
        }

        return OnBackResult(previousScaffoldValue = null, previousEntries = emptyList())
    }

    private fun getPreviousDestinationIndex(): Int {
        if (entriesAsNavItems.size <= 1) return -1

        val currentDestination = entriesAsNavItems.last()
        val currentScaffoldValue = currentScaffoldValue
        when (backNavigationBehavior) {
            BackNavigationBehavior.PopLatest -> return entriesAsNavItems.lastIndex - 1
            BackNavigationBehavior.PopUntilScaffoldValueChange ->
                for (index in entriesAsNavItems.lastIndex - 1 downTo 0) {
                    val previousValue = calculateScaffoldValue(entriesAsNavItems.subList(0, index + 1))
                    if (previousValue != currentScaffoldValue) return index
                }
            BackNavigationBehavior.PopUntilCurrentDestinationChange ->
                for (index in entriesAsNavItems.lastIndex - 1 downTo 0) {
                    if (entriesAsNavItems[index].pane != currentDestination.pane) return index
                }
            BackNavigationBehavior.PopUntilContentChange ->
                for (index in entriesAsNavItems.lastIndex - 1 downTo 0) {
                    val previousDestination = entriesAsNavItems[index]
                    if (previousDestination.contentKey != currentDestination.contentKey) return index
                    val previousValue = calculateScaffoldValue(entriesAsNavItems.subList(0, index + 1))
                    if (previousValue != currentScaffoldValue) return index
                }
        }
        return -1
    }

    private fun calculateScaffoldValue(
        destinationHistory: List<ThreePaneScaffoldDestinationItem<*>>,
    ): ThreePaneScaffoldValue = calculateThreePaneScaffoldValue(
        maxHorizontalPartitions = directive.maxHorizontalPartitions,
        maxVerticalPartitions = directive.maxVerticalPartitions,
        adaptStrategies = adaptStrategies,
        destinationHistory = destinationHistory,
    )

    override val content: @Composable () -> Unit = {
        val scaffoldValue = currentScaffoldValue
        val scaffoldState = remember { MutableThreePaneScaffoldState(scaffoldValue) }
        LaunchedEffect(scaffoldValue) { scaffoldState.animateTo(scaffoldValue) }

        val previousScaffoldValue = onBackResult.previousScaffoldValue
        val gestureInfo = remember(key, entries) { AppListDetailSceneInfo(key, entries) }
        val gestureState = rememberNavigationEventState(currentInfo = gestureInfo)
        NavigationBackHandler(
            state = gestureState,
            isBackEnabled = previousScaffoldValue != null,
            onBackCompleted = {
                repeat(allEntries.size - onBackResult.previousEntries.size) { onBack() }
            },
        )

        val transitionState = gestureState.transitionState
        LaunchedEffect(transitionState) {
            if (
                transitionState is NavigationEventTransitionState.InProgress &&
                previousScaffoldValue != null
            ) {
                scaffoldState.seekTo(
                    fraction = backProgressToStateProgress(
                        progress = transitionState.latestEvent.progress,
                        scaffoldValue = scaffoldValue,
                    ),
                    targetState = previousScaffoldValue,
                )
            } else {
                scaffoldState.animateTo(targetState = scaffoldValue)
            }
        }

        ListDetailContent(scaffoldState)
    }

    @Suppress("ComposableLambdaParameterNaming")
    @Composable
    private fun ListDetailContent(scaffoldState: ThreePaneScaffoldState) {
        val lastList = entries.findLast { getPaneRole(it) == ListDetailPaneScaffoldRole.List }
        val lastDetail = entries.findLast { getPaneRole(it) == ListDetailPaneScaffoldRole.Detail }

        ListDetailPaneScaffold(
            directive = directive,
            scaffoldState = scaffoldState,
            listPane = lastList?.let { entry ->
                {
                    AnimatedPane(
                        modifier = Modifier.preferredWidth(0.4f),
                        boundsAnimationSpec = PaneBoundsAnimationSpec,
                    ) {
                        CompositionLocalProvider(
                            LocalPaneSharedTransitionsEnabled provides false,
                        ) {
                            entry.Content()
                        }
                    }
                }
            } ?: {},
            detailPane = {
                AnimatedPane(
                    boundsAnimationSpec = PaneBoundsAnimationSpec,
                ) {
                    CompositionLocalProvider(
                        LocalPaneSharedTransitionsEnabled provides false,
                    ) {
                        if (lastDetail != null) {
                            lastDetail.Content()
                        } else {
                            detailPlaceholder()
                        }
                    }
                }
            },
            paneExpansionDragHandle = paneExpansionDragHandle,
            paneExpansionState = paneExpansionState,
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AppListDetailScene<*>) return false

        return key == other.key &&
            backNavigationBehavior == other.backNavigationBehavior &&
            directive == other.directive &&
            adaptStrategies == other.adaptStrategies &&
            allEntries == other.allEntries &&
            previousEntries == other.previousEntries &&
            scaffoldEntries == other.scaffoldEntries &&
            scaffoldEntryIndices == other.scaffoldEntryIndices &&
            entriesAsNavItems == other.entriesAsNavItems &&
            paneExpansionDragHandle == other.paneExpansionDragHandle &&
            paneExpansionState == other.paneExpansionState
    }

    override fun hashCode(): Int {
        var result = key.hashCode()
        result = 31 * result + backNavigationBehavior.hashCode()
        result = 31 * result + directive.hashCode()
        result = 31 * result + adaptStrategies.hashCode()
        result = 31 * result + allEntries.hashCode()
        result = 31 * result + previousEntries.hashCode()
        result = 31 * result + scaffoldEntries.hashCode()
        result = 31 * result + scaffoldEntryIndices.hashCode()
        result = 31 * result + entriesAsNavItems.hashCode()
        result = 31 * result + paneExpansionDragHandle.hashCode()
        result = 31 * result + paneExpansionState.hashCode()
        return result
    }
}

private data class AppListDetailSceneInfo(
    val sceneKey: Any,
    val sceneEntries: List<NavEntry<*>>,
) : NavigationEventInfo()

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
private fun backProgressToStateProgress(
    progress: Float,
    scaffoldValue: ThreePaneScaffoldValue,
): Float = AppListDetailPredictiveBackEasing.transform(progress) *
    when (scaffoldValue.expandedCount) {
        1 -> SinglePaneProgressRatio
        2 -> DualPaneProgressRatio
        else -> TriplePaneProgressRatio
    }

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
private val ThreePaneScaffoldValue.paneCount: Int
    get() = listOf(primary, secondary, tertiary).count { it != PaneAdaptedValue.Hidden }

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
private val ThreePaneScaffoldValue.expandedCount: Int
    get() = listOf(primary, secondary, tertiary).count { it == PaneAdaptedValue.Expanded }

private val AppListDetailPredictiveBackEasing: Easing = CubicBezierEasing(0.1f, 0.1f, 0f, 1f)
private const val SinglePaneProgressRatio = 0.1f
private const val DualPaneProgressRatio = 0.15f
private const val TriplePaneProgressRatio = 0.2f
