package io.github.vrcmteam.vrcm.presentation.screens.scan

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.presentation.compoments.AImage
import io.github.vrcmteam.vrcm.presentation.designsystem.*
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings

/** Fits both image and markers through the same transform, including letterboxing and rotation. */
@Composable
internal fun QrCodeSelection(batch: QrScanBatch, onSelect: (Int) -> Unit) {
    val selectLabel = strings.scanSelectCode
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f).clipToBounds()) {
            val scale = minOf(maxWidth / batch.width, maxHeight / batch.height)
            val imageWidth = scale * batch.width
            val imageHeight = scale * batch.height
            val left = (maxWidth - imageWidth) / 2
            val top = (maxHeight - imageHeight) / 2
            AImage(Modifier.fillMaxSize(), batch.preview, contentScale = ContentScale.Fit)
            batch.codes.forEachIndexed { index, code ->
                // A 48 dp hit target surrounds the 24 dp dot; its center stays on the detected code.
                AppIconButton(
                    onClick = { onSelect(index) },
                    modifier = Modifier.absoluteOffset(left + imageWidth * code.x - 24.dp, top + imageHeight * code.y - 24.dp)
                        .semantics { contentDescription = selectLabel.replace("%d", (index + 1).toString()) },
                    size = 48.dp,
                    containerColor = Color.Transparent,
                ) {
                    Box(
                        Modifier.size(24.dp).background(AppTheme.colors.tint, CircleShape)
                        .border(3.dp, AppTheme.colors.label, CircleShape),
                    )
                }
            }
        }
        AppText(
            strings.scanChooseCode,
            Modifier.padding(AppSpacing.m),
            textAlign = TextAlign.Center,
            style = AppTheme.type.subheadline,
        )
    }
}
