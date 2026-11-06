package io.github.vrcmteam.vrcm.presentation.screens.scan

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import io.github.vrcmteam.vrcm.presentation.designsystem.*
import io.github.vrcmteam.vrcm.presentation.settings.LocalSettingsState
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings
import io.github.vrcmteam.vrcm.presentation.supports.AppIcons

/** Camera content fills the window; the controls keep their own safe-area and touch-target space. */
@Composable
internal fun QrScannerViewfinder(
    scanning: Boolean,
    canGoBack: Boolean,
    onBack: () -> Unit,
    camera: @Composable () -> Unit,
) {
    val colors = AppColors.dark(LocalSettingsState.current.value.themeColor.accent)
    val locale = strings
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val animate = scanning && lifecycle.isAtLeast(Lifecycle.State.RESUMED) && !AppTheme.a11y.reduceMotion
    CompositionLocalProvider(LocalAppColors provides colors, LocalContentColor provides colors.label) {
        Box(Modifier.fillMaxSize().background(colors.systemBackground).semantics { paneTitle = locale.scanQrCode }) {
            camera()
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(colors.systemBackground.copy(alpha = 0.55f), Color.Transparent)))
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                        .padding(horizontal = AppSpacing.s, vertical = AppSpacing.s),
                ) {
                    AppIconButton(
                        onClick = onBack,
                        enabled = canGoBack,
                        size = AppSize.touchTarget,
                        containerColor = Color.Transparent,
                        contentColor = colors.label,
                    ) {
                        AppIcon(AppIcons.ArrowBackIosNew, locale.back)
                    }
                }
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    if (scanning) ScannerSweep(Modifier.fillMaxSize(), animate)
                    AppText(
                        locale.scanQrHint,
                        modifier = Modifier.align(Alignment.BottomCenter)
                            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                            .padding(horizontal = 24.dp, vertical = 36.dp),
                        style = AppTheme.type.subheadline,
                        textAlign = TextAlign.Center,
                    )
                }
                Box(
                    Modifier.fillMaxWidth().background(colors.systemBackground)
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                        .padding(horizontal = AppSpacing.page, vertical = 24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    AppText(locale.scanQrCode, style = AppTheme.type.headline, color = colors.tint)
                }
            }
        }
    }
}

@Composable
private fun ScannerSweep(modifier: Modifier, animate: Boolean) {
    val progress = if (animate) {
        val transition = rememberInfiniteTransition(label = "qr-scan")
        val value by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart),
            label = "qr-scan-position",
        )
        value
    } else {
        0.4f
    }
    val tint = AppTheme.colors.tint
    Canvas(modifier) {
        // The sweep stays inside the camera area, clear of the back button and bottom instruction.
        val y = size.height * (0.08f + progress * 0.68f)
        val trail = minOf(96.dp.toPx(), size.height * 0.16f)
        drawRect(
            brush = Brush.verticalGradient(listOf(Color.Transparent, tint.copy(alpha = 0.22f)), y - trail, y),
            topLeft = Offset(0f, y - trail),
            size = Size(size.width, trail),
        )
        drawLine(
            brush = Brush.horizontalGradient(listOf(Color.Transparent, tint, tint, Color.Transparent)),
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 1.5.dp.toPx(),
        )
    }
}
