package io.github.vrcmteam.vrcm.presentation.compoments

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import io.github.vrcmteam.vrcm.presentation.navigation.AppNavigator
import io.github.vrcmteam.vrcm.presentation.screens.avatar.AvatarProfileScreen
import io.github.vrcmteam.vrcm.presentation.screens.avatar.data.AvatarProfileVo
import io.github.vrcmteam.vrcm.presentation.screens.group.GroupProfileScreen
import io.github.vrcmteam.vrcm.presentation.screens.group.data.GroupProfileVo
import io.github.vrcmteam.vrcm.presentation.screens.home.HomeScreen
import io.github.vrcmteam.vrcm.presentation.screens.user.UserProfileScreen
import io.github.vrcmteam.vrcm.presentation.screens.user.data.UserProfileVo
import io.github.vrcmteam.vrcm.presentation.screens.world.WorldProfileScreen
import io.github.vrcmteam.vrcm.presentation.screens.world.data.WorldProfileVo
import io.github.vrcmteam.vrcm.presentation.settings.locale.LocaleStrings
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings
import io.github.vrcmteam.vrcm.presentation.supports.AppIcons
import io.github.vrcmteam.vrcm.service.OfficialLinkContent
import io.github.vrcmteam.vrcm.service.OfficialLinkInbox
import io.github.vrcmteam.vrcm.service.OfficialLinkService
import io.github.vrcmteam.vrcm.service.OfficialLinkTarget
import io.github.vrcmteam.vrcm.service.OfficialLinkType
import io.github.vrcmteam.vrcm.service.parseOfficialLink
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
internal fun OfficialLinkPrompt(
    navigator: AppNavigator,
    inbox: OfficialLinkInbox,
) {
    val clipboard = LocalClipboardManager.current
    val service: OfficialLinkService = koinInject()
    val locale = strings
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val incomingRequest by inbox.pendingRequest.collectAsState()
    var foregroundGeneration by remember { mutableIntStateOf(0) }
    var lastInspectedTargetKey by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingText by rememberSaveable { mutableStateOf<String?>(null) }
    var isOpening by remember { mutableStateOf(false) }
    var openFailed by remember { mutableStateOf(false) }
    var isOpeningExternalLink by remember { mutableStateOf(false) }
    var failedExternalLink by rememberSaveable { mutableStateOf<String?>(null) }
    var invalidExternalLink by rememberSaveable { mutableStateOf(false) }
    val isAuthenticated = navigator.items.any { it is HomeScreen }
    val target = pendingText?.let(::parseOfficialLink)

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        foregroundGeneration++
    }

    LaunchedEffect(foregroundGeneration, isAuthenticated) {
        if (foregroundGeneration == 0 || !isAuthenticated || pendingText != null) return@LaunchedEffect
        val clipboardText = runCatching { clipboard.getText()?.text }.getOrNull() ?: return@LaunchedEffect
        val clipboardTarget = parseOfficialLink(clipboardText) ?: return@LaunchedEffect
        val targetKey = "${clipboardTarget.type}:${clipboardTarget.id}"
        if (targetKey == lastInspectedTargetKey) return@LaunchedEffect
        lastInspectedTargetKey = targetKey
        pendingText = clipboardText.trim()
    }

    LaunchedEffect(incomingRequest?.id, isAuthenticated) {
        val request = incomingRequest ?: return@LaunchedEffect
        if (!isAuthenticated) return@LaunchedEffect
        failedExternalLink = null
        invalidExternalLink = false
        val incomingTarget = parseOfficialLink(request.url)
        if (incomingTarget == null) {
            inbox.consume(request)
            invalidExternalLink = true
            return@LaunchedEffect
        }

        pendingText = null
        lastInspectedTargetKey = "${incomingTarget.type}:${incomingTarget.id}"
        isOpeningExternalLink = true
        try {
            service.resolve(incomingTarget)
                .onSuccess { content ->
                    inbox.consume(request)
                    navigator push content.toRoute()
                }
                .onFailure {
                    inbox.consume(request)
                    failedExternalLink = incomingTarget.canonicalUrl()
                }
        } finally {
            isOpeningExternalLink = false
        }
    }

    if (isOpeningExternalLink) {
        AlertDialog(
            onDismissRequest = {},
            icon = { Icon(AppIcons.Link, contentDescription = null) },
            title = { Text(locale.officialLinkPromptTitle) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Text(locale.loading)
                }
            },
            confirmButton = {},
        )
    }

    if (failedExternalLink != null || invalidExternalLink) {
        val retryTarget = failedExternalLink?.let(::parseOfficialLink)
        AlertDialog(
            onDismissRequest = {
                failedExternalLink = null
                invalidExternalLink = false
            },
            icon = { Icon(AppIcons.Link, contentDescription = null) },
            title = { Text(locale.officialLinkPromptTitle) },
            text = {
                Text(
                    text = locale.officialLinkOpenFailed,
                    color = MaterialTheme.colorScheme.error,
                )
            },
            confirmButton = {
                if (retryTarget != null) {
                    Button(
                        onClick = {
                            failedExternalLink = null
                            inbox.submit(retryTarget.canonicalUrl())
                        },
                    ) {
                        Text(locale.retry)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        failedExternalLink = null
                        invalidExternalLink = false
                    },
                ) {
                    Text(locale.cancel)
                }
            },
        )
    }

    if (target != null) {
        AlertDialog(
            onDismissRequest = {
                if (!isOpening) pendingText = null
            },
            icon = { Icon(AppIcons.Link, contentDescription = null) },
            title = { Text(locale.officialLinkPromptTitle) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        locale.officialLinkPromptMessage.replace(
                            "%s",
                            target.type.localizedName(locale),
                        )
                    )
                    if (openFailed) {
                        Text(
                            text = locale.officialLinkOpenFailed,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    modifier = Modifier.widthIn(min = 96.dp),
                    enabled = !isOpening,
                    onClick = {
                        isOpening = true
                        openFailed = false
                        scope.launch {
                            service.resolve(target)
                                .onSuccess { content ->
                                    pendingText = null
                                    navigator push content.toRoute()
                                }
                                .onFailure { openFailed = true }
                            isOpening = false
                        }
                    },
                ) {
                    if (isOpening) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.size(8.dp))
                        Text(locale.loading)
                    } else {
                        Text(locale.officialLinkOpen)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isOpening,
                    onClick = { pendingText = null },
                ) {
                    Text(locale.cancel)
                }
            },
        )
    }
}

private fun OfficialLinkType.localizedName(locale: LocaleStrings): String = when (this) {
    OfficialLinkType.User -> locale.officialLinkTypeUser
    OfficialLinkType.World -> locale.officialLinkTypeWorld
    OfficialLinkType.Group -> locale.officialLinkTypeGroup
    OfficialLinkType.Avatar -> locale.officialLinkTypeAvatar
}

private fun OfficialLinkTarget.canonicalUrl(): String =
    "https://vrchat.com/home/${type.pathSegment}/$id"

private fun OfficialLinkContent.toRoute() = when (this) {
    is OfficialLinkContent.User -> UserProfileScreen(UserProfileVo(data))
    is OfficialLinkContent.World -> WorldProfileScreen(WorldProfileVo(data))
    is OfficialLinkContent.Group -> GroupProfileScreen(GroupProfileVo(data))
    is OfficialLinkContent.Avatar -> AvatarProfileScreen(AvatarProfileVo(data))
}
