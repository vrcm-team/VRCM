package io.github.vrcmteam.vrcm.presentation.screens.scan

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.alexzhirkevich.qrose.rememberQrCodePainter
import io.github.alexzhirkevich.qrose.options.*
import io.github.vrcmteam.vrcm.core.extensions.shareImageBytes
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.getAppPlatform
import io.github.vrcmteam.vrcm.presentation.compoments.AImage
import io.github.vrcmteam.vrcm.presentation.compoments.ToastText
import io.github.vrcmteam.vrcm.presentation.designsystem.*
import io.github.vrcmteam.vrcm.presentation.extensions.currentNavigator
import io.github.vrcmteam.vrcm.presentation.navigation.AppRoute
import io.github.vrcmteam.vrcm.presentation.screens.gallery.editor.PlatformImageCodec
import io.github.vrcmteam.vrcm.presentation.screens.gallery.editor.releasePlatformImageBitmap
import io.github.vrcmteam.vrcm.presentation.screens.meetup.meetupCardProfileUrl
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings
import io.github.vrcmteam.vrcm.presentation.supports.AppIcons
import io.github.vrcmteam.vrcm.service.AuthService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject

/** Independent public-profile QR page; opening it does not create or edit a meetup card. */
@Serializable
data class MyQrCodeScreen(val ownerUserId: String) : AppRoute {
    @Composable
    override fun Content() {
        val navigator = currentNavigator
        val auth = koinInject<AuthService>()
        val currentUser by auth.currentUserState.collectAsState()
        val session by SharedFlowCentre.currentSession.collectAsState()
        val account = session?.account?.takeIf { it.userId == ownerUserId }
        val user = currentUser?.takeIf { it.id == ownerUserId }
        MyQrCodeContent(
            userId = account?.userId,
            displayName = user?.displayName ?: account?.username.orEmpty(),
            iconUrl = user?.iconUrl ?: account?.iconUrl,
            pronouns = user?.pronouns,
            onBack = { navigator.pop() },
            onScan = {
                if (navigator.items.getOrNull(navigator.size - 2) == QrScannerScreen) navigator.pop()
                else navigator replace QrScannerScreen
            },
        )
    }
}

@Composable
internal fun MyQrCodeContent(
    userId: String?,
    displayName: String,
    iconUrl: String?,
    pronouns: String?,
    onBack: () -> Unit,
    onScan: () -> Unit,
) {
    val locale = strings
    val scope = rememberCoroutineScope()
    val codec = koinInject<PlatformImageCodec>()
    val platform = getAppPlatform()
    var colored by rememberSaveable { mutableStateOf(true) }
    var sharing by remember { mutableStateOf(false) }
    val layer = rememberGraphicsLayer()
    AppScaffold(
        containerColor = AppTheme.colors.groupedBackground,
        topBar = {
            AppNavBar(
                title = { AppText(locale.scanMyQrCode) },
                edgeColor = AppTheme.colors.groupedBackground,
                navigationIcon = {
                    AppIconButton(onClick = onBack, containerColor = Color.Transparent) {
                        AppIcon(AppIcons.ArrowBackIosNew, locale.back)
                    }
                },
            )
        },
        bottomBar = {
            Row(
                Modifier.fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                    .padding(AppSpacing.l),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Top,
            ) {
                MyQrAction(if (sharing) locale.loading else locale.scanShare, AppIcons.Share, !sharing && userId != null, Modifier.weight(1f)) {
                    sharing = true
                    scope.launch {
                        try {
                            val bitmap = layer.toImageBitmap()
                            val bytes = try {
                                codec.encodePng(bitmap)
                            } finally {
                                releasePlatformImageBitmap(bitmap)
                            }
                            if (!platform.shareImageBytes(bytes, "VRCM-$userId.png")) {
                                SharedFlowCentre.toastText.emit(ToastText.Error(locale.imageShareFailed))
                            }
                        } catch (cause: CancellationException) {
                            throw cause
                        } catch (_: Exception) {
                            SharedFlowCentre.toastText.emit(ToastText.Error(locale.imageShareFailed))
                        } finally {
                            sharing = false
                        }
                    }
                }
                MyQrAction(locale.scanMode, AppIcons.QrCodeScanner, !sharing, Modifier.weight(1f), onScan)
                MyQrAction(locale.scanQrStyle, AppIcons.Refresh, !sharing && userId != null, Modifier.weight(1f)) { colored = !colored }
            }
        },
    ) { padding ->
        Box(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(AppSpacing.l),
            contentAlignment = Alignment.Center,
        ) {
            if (userId == null) {
                AppText(locale.deviceAuthSessionChanged, textAlign = TextAlign.Center)
            } else {
                // Keep the card readable on tablets while letting narrow windows and large fonts scroll.
                MyQrCodeCard(
                    userId, displayName, iconUrl, pronouns, colored,
                    Modifier.widthIn(max = 440.dp).fillMaxWidth().drawWithContent {
                        layer.record { this@drawWithContent.drawContent() }
                        drawLayer(layer)
                    },
                )
            }
        }
    }
}

@Composable
internal fun MyQrCodeCard(
    userId: String,
    displayName: String,
    iconUrl: String?,
    pronouns: String?,
    colored: Boolean,
    modifier: Modifier = Modifier,
) {
    val qrColors = AppTheme.colors
    val qrPainter = rememberQrCodePainter(meetupCardProfileUrl(userId), colored, qrColors.tint, qrColors.secondaryTint, qrColors.label) {
        shapes {
            darkPixel = if (colored) QrPixelShape.circle() else QrPixelShape.square()
            frame = QrFrameShape.roundCorners(.25f)
            ball = QrBallShape.roundCorners(.25f)
        }
        colors {
            dark = if (colored) {
                QrBrush.brush { Brush.linearGradient(listOf(qrColors.tint, qrColors.secondaryTint)) }
            } else {
                QrBrush.solid(qrColors.label)
            }
        }
    }
    AppSurface(modifier, shape = AppShapes.xl, color = AppTheme.colors.secondaryGroupedBackground) {
        Column(Modifier.padding(AppSpacing.l), verticalArrangement = Arrangement.spacedBy(AppSpacing.l)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppSpacing.m)) {
                // Stable avatar geometry matches the profile heading without constraining its text height.
                AImage(Modifier.size(64.dp).clip(CircleShape), iconUrl, contentDescription = null)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                    AppText(displayName, style = AppTheme.type.title2, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    pronouns?.takeIf { it.isNotBlank() }?.let {
                        AppText(it, style = AppTheme.type.subheadline, color = AppTheme.colors.secondaryLabel,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            // The quiet zone shares the card background; theme colors keep the modules legible in both modes.
            Box(Modifier.fillMaxWidth().padding(24.dp)) {
                Image(qrPainter, strings.meetupCardQrDescription, Modifier.fillMaxWidth().aspectRatio(1f))
            }
            AppText(
                strings.scanMyQrHint,
                Modifier.fillMaxWidth(),
                style = AppTheme.type.subheadline,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun MyQrAction(
    label: String,
    icon: ImageVector,
    enabled: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(AppSpacing.s)) {
        // Keep the three footer symbols aligned on a 48 dp touch target, independent of label wrapping.
        AppIconButton(onClick = onClick, enabled = enabled, size = 48.dp, containerColor = Color.Transparent) {
            AppIcon(icon, label)
        }
        AppText(label, style = AppTheme.type.footnote, color = AppTheme.colors.secondaryLabel, textAlign = TextAlign.Center)
    }
}
