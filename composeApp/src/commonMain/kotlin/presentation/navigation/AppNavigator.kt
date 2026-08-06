package io.github.vrcmteam.vrcm.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldScope
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack

interface AppRoute : NavKey {
    val key: String
        get() = "${this::class.qualifiedName}:${hashCode()}"

    @Composable
    fun Content()
}

interface AppListRoute : AppRoute

interface AppDetailRoute : AppRoute

enum class AppRoutePane {
    List,
    Detail,
    FullWindow,
}

val AppRoute.pane: AppRoutePane
    get() = when (this) {
        is AppListRoute -> AppRoutePane.List
        is AppDetailRoute -> AppRoutePane.Detail
        else -> AppRoutePane.FullWindow
    }

private const val AppListDetailSceneKey = "vrcm:list-detail"

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
internal fun AppRoute.adaptivePaneMetadata(
    detailPlaceholder: @Composable ThreePaneScaffoldScope.() -> Unit = {},
): Map<String, Any> = when (pane) {
    AppRoutePane.List -> AppListDetailSceneStrategy.listPane(
        sceneKey = AppListDetailSceneKey,
        detailPlaceholder = detailPlaceholder,
    )
    AppRoutePane.Detail -> AppListDetailSceneStrategy.detailPane(AppListDetailSceneKey)
    AppRoutePane.FullWindow -> emptyMap()
}

val LocalNavigator = staticCompositionLocalOf<AppNavigator?> { null }

val <T> ProvidableCompositionLocal<T?>.currentOrThrow: T
    @Composable get() = current ?: error("CompositionLocal is null")

@Stable
class AppNavigator internal constructor(
    internal val backStack: MutableList<AppRoute>,
) {
    val items: List<AppRoute>
        get() = backStack

    val lastItem: AppRoute
        get() = backStack.last()

    val size: Int
        get() = backStack.size

    val canPop: Boolean
        get() = backStack.size > 1

    infix fun push(route: AppRoute) {
        if (backStack.lastOrNull() == route) return
        backStack += route
    }

    fun pop(): Boolean {
        if (!canPop) return false
        backStack.removeAt(backStack.lastIndex)
        return true
    }

    infix fun replace(route: AppRoute) {
        backStack[backStack.lastIndex] = route
    }

    infix fun replaceAll(route: AppRoute) {
        backStack.clear()
        backStack += route
    }
}

@Composable
fun rememberAppNavigator(initialRoute: AppRoute): AppNavigator {
    val navBackStack = rememberNavBackStack(appSavedStateConfiguration, initialRoute)
    @Suppress("UNCHECKED_CAST")
    val appBackStack = navBackStack as MutableList<AppRoute>
    return remember(navBackStack) { AppNavigator(appBackStack) }
}
