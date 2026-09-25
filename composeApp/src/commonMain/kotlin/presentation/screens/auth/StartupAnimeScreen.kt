package io.github.vrcmteam.vrcm.presentation.screens.auth

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.getAppPlatform
import io.github.vrcmteam.vrcm.presentation.navigation.AppRoute
import org.koin.compose.viewmodel.koinViewModel
import io.github.vrcmteam.vrcm.presentation.navigation.LocalNavigator
import io.github.vrcmteam.vrcm.presentation.navigation.currentOrThrow
import io.github.vrcmteam.vrcm.presentation.compoments.AuthFold
import io.github.vrcmteam.vrcm.presentation.designsystem.AppTheme
import io.github.vrcmteam.vrcm.service.VersionService
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject
import org.koin.core.logger.Logger
import presentation.compoments.UpdateDialog
import presentation.screens.auth.data.VersionVo

@Serializable
object StartupAnimeScreen : AppRoute {
    @Composable
    override fun Content() {
        val durationMillis = 1000
        val current = LocalNavigator.currentOrThrow
        var isStartUp by remember { mutableStateOf(false) }
        val startUpAnime = { isStartUp = true }
        val authScreenModel = koinViewModel<AuthScreenModel>()

        LaunchedEffect(Unit) {
            delay(500)
            startUpAnime()
        }
        BoxWithConstraints {

            val iconYOffset by animateDpAsState(
                if (isStartUp) maxHeight.times(-0.2f) else 0.dp,
                tween(durationMillis),
                label = "LogoOffset"
            )

            val authSurfaceOffset by animateDpAsState(
                if (isStartUp) 0.dp else maxHeight.times(0.42f),
                tween(durationMillis),
                label = "AuthSurfaceOffset"
            )
            val authSurfaceAlpha by animateFloatAsState(
                if (isStartUp) 1.00f else 0.00f,
                tween(durationMillis),
                label = "AuthSurfaceAlpha",
                finishedListener = {  current replace AuthScreen }
            )
            // 开屏是纯色底（与 iOS 启动屏 LaunchBackground 一致），随 logo 上移过渡到登录页的强调色浅底
            val backgroundColor by animateColorAsState(
                if (isStartUp) AppTheme.colors.tintSoft else AppTheme.colors.secondaryGroupedBackground,
                tween(durationMillis),
                label = "StartupBackground"
            )
            AuthFold(
                authUIState = authScreenModel.uiState,
                iconYOffset = iconYOffset,
                cardYOffset = authSurfaceOffset,
                cardAlpha = authSurfaceAlpha,
                cardHeightDp = maxHeight.times(0.42f),
                backgroundColor = backgroundColor,
            )
        }


    }

}

@Composable
fun VersionDialog() {
    val updateSource = getAppPlatform().updateSource ?: return
    val versionService: VersionService = koinInject()
    val logger: Logger = koinInject()
    var version by remember { mutableStateOf(VersionVo()) }
    LaunchedEffect(versionService) {
        versionService.checkVersion(updateSource, checkRemember = true)
            .onFailure { logger.error("Failed to check version: ${it.message.orEmpty()}") }
            .onSuccess {
                if (it.hasNewVersion) {
                    version = VersionVo(
                        tagName = it.tagName,
                        htmlUrl = it.htmlUrl,
                        body = it.body,
                        hasNewVersion = true,
                        downloadUrl = it.downloadUrl,
                        versionName = it.versionName,
                    )
                }
            }
    }
    UpdateDialog(
        version = version,
        onDismissRequest = {
            version = VersionVo()
        },
        onRememberVersion = versionService::rememberVersion
    )
}
