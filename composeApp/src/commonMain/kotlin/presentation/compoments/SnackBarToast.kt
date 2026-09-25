package io.github.vrcmteam.vrcm.presentation.compoments

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.network.supports.ApiNotice
import io.github.vrcmteam.vrcm.network.supports.ApiNoticeCenter
import io.github.vrcmteam.vrcm.presentation.designsystem.AppBanner
import io.github.vrcmteam.vrcm.presentation.designsystem.AppBannerData
import io.github.vrcmteam.vrcm.presentation.designsystem.AppBannerHost
import io.github.vrcmteam.vrcm.presentation.designsystem.AppBannerHostState
import io.github.vrcmteam.vrcm.presentation.designsystem.AppTheme
import io.github.vrcmteam.vrcm.presentation.settings.locale.LocaleStrings
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings
import org.koin.compose.koinInject

internal fun ApiNotice.localizedMessage(locale: LocaleStrings): String = when (this) {
    ApiNotice.RateLimited -> locale.apiRequestRateLimited
}

internal fun shouldAcceptRegularToast(activeNotice: ApiNotice?): Boolean =
    activeNotice == null

/**
 * toast弹窗
 */
@Composable
fun SnackBarToast(
    text: String,
    modifier: Modifier = Modifier,
    onEffect: () -> Unit,
    containerColor: Color = AppTheme.colors.elevated().secondarySystemBackground,
    contentColor: Color = AppTheme.colors.label,
    content: @Composable (AppBannerData) -> Unit = {
        AppBanner(message = it.message, containerColor = containerColor, contentColor = contentColor)
    }
) {
    val bannerHostState = remember { AppBannerHostState() }

    LaunchedEffect(text) {
        if (text.isNotBlank()) {
            bannerHostState.show(text)
            onEffect()
        }
    }
    AppBannerHost(
        modifier = modifier,
        hostState = bannerHostState,
        banner = content,
    )
}

@Composable
fun SnackBarToastBox(
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.TopCenter,
    content: @Composable () -> Unit
) {
    val apiNoticeCenter: ApiNoticeCenter = koinInject()
    val activeNotice by apiNoticeCenter.activeNotice.collectAsState()
    val locale = strings

    Box {
        CompositionLocalProvider(
            LocalSnackBarToastText provides remember { mutableStateOf(ToastText.Normal) }
        ) {
            content()
            var sackBarToastText by LocalSnackBarToastText.current
            LaunchedEffect(apiNoticeCenter){
                SharedFlowCentre.toastText.collect { toast ->
                    if (shouldAcceptRegularToast(apiNoticeCenter.activeNotice.value)) {
                        sackBarToastText = toast
                    }
                }
            }
            val apiToast = activeNotice?.let {
                ToastText.Info(it.localizedMessage(locale))
            }
            val displayedToast = apiToast ?: sackBarToastText
            val colors = AppTheme.colors
            val theme = when (displayedToast) {
                is ToastText.Error -> colors.destructiveSoft to colors.onDestructiveSoft
                is ToastText.Success ,
                is ToastText.Info ,
                ToastText.Normal -> colors.elevated().secondarySystemBackground to colors.label
            }
            SnackBarToast(
                modifier = modifier.align(alignment),
                text = displayedToast.text,
                onEffect = {
                    activeNotice?.let(apiNoticeCenter::consume)
                    sackBarToastText = ToastText.Normal
                },
                containerColor = theme.first,
                contentColor = theme.second,
            )
        }
    }

}

val LocalSnackBarToastText: ProvidableCompositionLocal<MutableState<ToastText>> =
    compositionLocalOf { error("No text provided") }


sealed class ToastText(val text: String) {
    class Success(text: String) : ToastText(text)
    class Error(text: String) : ToastText(text)
    class Info(text: String) : ToastText(text)
//    class Warning(text: String) : ToastText(text)
    data object Normal : ToastText("")
}
