package io.github.vrcmteam.vrcm.presentation.compoments

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.painterResource
import vrcm.composeapp.generated.resources.Res
import vrcm.composeapp.generated.resources.logo

@OptIn(ExperimentalResourceApi::class)
@Composable
fun AuthFold(
    iconYOffset: Dp = (-180).dp,
    cardYOffset: Dp = 0.dp,
    cardAlpha: Float = 1.00f,
    cardHeightDp: Dp = 380.dp,
    shapeDp: Dp = 30.dp,
    context: @Composable BoxScope.() -> Unit = {},
    cardContext: @Composable () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(color = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Image(
            painter = painterResource(Res.drawable.logo),
            contentDescription = "logo",
            modifier = Modifier
                .width(128.dp)
                .align(Alignment.Center)
                .offset(y = iconYOffset)
        )
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = cardYOffset)
                .alpha(cardAlpha)
                .height(cardHeightDp)
                .align(Alignment.BottomCenter),
            color = MaterialTheme.colorScheme.onPrimary,
            shape = RoundedCornerShape(topStart = shapeDp, topEnd = shapeDp),
        ) {
            cardContext()
        }
        context()
    }
}