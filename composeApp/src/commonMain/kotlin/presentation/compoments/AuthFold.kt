package io.github.vrcmteam.vrcm.presentation.compoments

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.presentation.animations.IconBoundsTransform
import io.github.vrcmteam.vrcm.presentation.extensions.enableIf
import io.github.vrcmteam.vrcm.presentation.extensions.getInsetPadding
import io.github.vrcmteam.vrcm.presentation.screens.auth.data.AuthUIState
import org.jetbrains.compose.resources.painterResource
import vrcm.composeapp.generated.resources.Res
import vrcm.composeapp.generated.resources.logo

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun AuthFold(
    iconYOffset: Dp,
    authUIState: AuthUIState,
    enabledIconAnime: Boolean = false,
    cardYOffset: Dp = 0.dp,
    cardAlpha: Float = 1.00f,
    cardHeightDp: Dp,
    shapeDp: Dp = 30.dp,
    cardContext: @Composable () -> Unit = {}
) {
    // 底部栏高度
    val bottomPadding = getInsetPadding(WindowInsets::getBottom)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(color = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = cardYOffset)
                .alpha(cardAlpha)
                .height(cardHeightDp + bottomPadding)
                .align(Alignment.BottomCenter),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.onPrimary,
                contentColor = MaterialTheme.colorScheme.primary
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            shape = RoundedCornerShape(topStart = shapeDp, topEnd = shapeDp),
        ) {
            cardContext()
        }
        AImage(
            modifier = Modifier
                .offset(y = iconYOffset)
                .size(128.dp)
                .align(Alignment.Center)
                .enableIf(enabledIconAnime) {
                    sharedBoundsBy(
                        key = "${authUIState.userId}UserIcon",
                        boundsTransform = IconBoundsTransform
                    )
                }
                .clip(CircleShape),
            imageData = authUIState.iconUrl,
            error = painterResource(Res.drawable.logo),
            contentDescription = "AuthFoldLogo"
        )
    }
}