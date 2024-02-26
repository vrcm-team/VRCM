package io.github.kamo.vrcm.ui.profile

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.request.ImageRequest
import coil3.request.crossfade
import io.github.kamo.vrcm.MainRouteEnum
import io.github.kamo.vrcm.data.api.users.UserData
import io.github.kamo.vrcm.ui.theme.GameColor
import io.github.kamo.vrcm.ui.theme.MediumRoundedShape
import io.github.kamo.vrcm.ui.theme.SmallRoundedShape
import io.github.kamo.vrcm.ui.util.AImage
import io.github.kamo.vrcm.ui.util.capitalizeFirst
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@Composable
fun Profile(
    profileViewModel: ProfileViewModel = koinViewModel(),
    userId: String,
    popBackStack: () -> Unit,
    onNavigate: (MainRouteEnum, Boolean, List<Any>) -> Unit
) {

    LaunchedEffect(Unit) {
        runCatching {
            profileViewModel.refreshUser(userId)
        }
    }

    val user = profileViewModel.userState

//    Crossfade(
//        targetState = user == null,
//        animationSpec = tween(1000),
//        label = ""
//    ) {
//        Box(modifier = Modifier.fillMaxSize()) {
//            if (it) {
//                CircularProgressIndicator(
//                    modifier = Modifier
//                        .size(60.dp)
//                        .align(Alignment.Center),
//                    color = MaterialTheme.colorScheme.primary,
//                    strokeWidth = 5.dp
//                )
//            } else {
//            }
//        }
//    }
    FriedScreen(user, popBackStack, onNavigate)


}

@Composable
fun FriedScreen(
    user: UserData?,
    popBackStack: () -> Unit,
    onNavigate: (MainRouteEnum, Boolean, List<Any>) -> Unit
) {
    val scrollState = rememberScrollState()
    val imageHeight = (LocalConfiguration.current.screenHeightDp / 3)
    val offsetDp = with(LocalDensity.current) { scrollState.value.toDp() }
    val ratio =
        (((imageHeight - offsetDp.value) / imageHeight.toFloat()).let { if (it >= 0) it else 0f }).let {
            FastOutSlowInEasing.transform(it)
        }
    val fl = scrollState.value / imageHeight.toFloat()
    val blurDp = with(LocalDensity.current) { (fl * 20).toDp() }
    val inverseRatio = 1 - ratio
    val topBarHeight = 70

    val initUserIconPadding = imageHeight.toFloat()
    val lastIconPadding = initUserIconPadding - (topBarHeight * ratio)
    val isHidden = initUserIconPadding == lastIconPadding
    Surface(
        Modifier
            .systemBarsPadding()
            .verticalScroll(scrollState)
            .height(2000.dp)
            .fillMaxWidth()
    ) {

        ProfileUserImage(
            imageHeight,
            offsetDp,
            ratio,
            blurDp,
            user?.imageUrl
        )


        BottomCard(
            initUserIconPadding,
            ratio,
            topBarHeight,
            inverseRatio,
            user
        )

        TopMenuBar(
            topBarHeight,
            offsetDp,
            ratio,
            inverseRatio,
            onReturn = popBackStack,
            onMenu = { }
        )

        ProfileUserIcon(
            isHidden,
            lastIconPadding,
            offsetDp,
            imageHeight,
            inverseRatio,
            user?.iconUrl,
        ) { scrollState.animateScrollTo(0, tween(600)) }
    }
}

@Composable
private fun ProfileUserImage(
    imageHeight: Int,
    offsetDp: Dp,
    ratio: Float,
    blurDp: Dp,
    imageUrl: String?
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        AImage(
            modifier = Modifier
                .height(imageHeight.dp)
                .fillMaxWidth()
                .padding(top = offsetDp)
                .clip(
                    RoundedCornerShape(
                        bottomStart = (30 * ratio).dp,
                        bottomEnd = (30 * ratio).dp
                    )
                )
                .blur(blurDp),
            imageData = imageUrl,
        )
    }
}

@Composable
private fun BottomCard(
    initUserIconPadding: Float,
    ratio: Float,
    topBarHeight: Int,
    inverseRatio: Float,
    user: UserData?
) {
    Card(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = initUserIconPadding.dp),
        shape = RoundedCornerShape(
            topStart = (30 * ratio).dp,
            topEnd = (30 * ratio).dp
        )
    ) {
        if (user == null) return@Card
        val statusColor = GameColor.Status.fromValue(user.status)
        val trustRank = user.trustRank
        val rankColor: Color = GameColor.Rank.fromValue(trustRank)
        Spacer(modifier = Modifier.height((topBarHeight * inverseRatio).dp))
        Row(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(horizontal = 10.dp, vertical = 10.dp),
        ) {
            Icon(
                modifier = Modifier
                    .size(26.dp)
                    .align(Alignment.CenterVertically),
                imageVector = Icons.Outlined.Shield,
                contentDescription = "TrustRankIcon",
                tint = rankColor
            )
            Text(
                modifier = Modifier
                    .padding(start = 6.dp),
                text = user.displayName,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
            )
            if (user.isSupporter) {
                Icon(
                    modifier = Modifier
                        .size(24.dp)
                        .align(Alignment.Top),
                    imageVector = Icons.Rounded.Add,
                    contentDescription = "SupporterIcon",
                    tint = GameColor.Supporter
                )
            }
        }
        Row(
            Modifier.align(Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Canvas(
                modifier = Modifier
                    .size(12.dp)
                    .align(Alignment.CenterVertically)
            ) {
                drawCircle(statusColor)
            }
            Text(
                modifier = Modifier
                    .align(Alignment.CenterVertically),
                text = when (user.statusDescription.isBlank()) {
                    true -> user.status.value
                    else -> user.statusDescription
                }
            )
        }

        Row(
            modifier = Modifier
                .padding(top = 10.dp)
                .align(Alignment.CenterHorizontally),
        ) {
            val speakLanguages = remember(user) { user.speakLanguages }
            speakLanguages.forEach { language ->
                Text(
                    modifier = Modifier
                        .align(Alignment.CenterVertically)
                        .padding(horizontal = 6.dp)
                        .background(MaterialTheme.colorScheme.primary, SmallRoundedShape)
                        .padding(horizontal = 6.dp)
                        .clip(MediumRoundedShape),
                    text = remember(language) { language.capitalizeFirst() },
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }

        }
    }
}

@Composable
private fun ProfileUserIcon(
    isHidden: Boolean,
    lastIconPadding: Float,
    offsetDp: Dp,
    imageHeight: Int,
    inverseRatio: Float,
    avatarThumbnailImageUrl: String?,
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
    onClickIcon: suspend () -> Unit = {}
) {
    val iconSize = (60 * inverseRatio).dp
    Box {
        Row(
            modifier = Modifier
                .run { if (isHidden) offset(y = (offsetDp - imageHeight.dp)) else this }
                .padding(top = (lastIconPadding + 5).dp)
                .alpha(inverseRatio),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(modifier = Modifier.weight(1f))
            AImage(
                modifier = Modifier
                    .align(Alignment.CenterVertically)
                    .clip(CircleShape)
                    .size(iconSize)
                    .clickable { coroutineScope.launch { onClickIcon() } },
                imageData = ImageRequest.Builder(LocalContext.current)
                    .data(avatarThumbnailImageUrl)
                    .crossfade(600)
                    .size(70, 70).build(),
                contentDescription = "UserIcon",
            )
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun TopMenuBar(
    topBarHeight: Int,
    offsetDp: Dp,
    ratio: Float,
    inverseRatio: Float,
    color: Color = Color.White,
    onReturn: () -> Unit,
    onMenu: () -> Unit
) {
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .height(topBarHeight.dp)
                .offset(y = offsetDp)
                .alpha(ratio),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                modifier = Modifier
                    .padding(horizontal = 10.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onReturn),
                imageVector = Icons.Rounded.ArrowBackIosNew,
                tint = Color.White,
                contentDescription = "WhiteReturnIcon"
            )
            Spacer(modifier = Modifier.weight(1f))
            Icon(
                modifier = Modifier
                    .padding(horizontal = 10.dp),
                imageVector = Icons.Rounded.Menu,
                tint = Color.White,
                contentDescription = "WhiteMenuIcon"
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .height(topBarHeight.dp)
                .offset(y = offsetDp)
                .alpha(inverseRatio)
                .background(
                    color, RoundedCornerShape(
                        bottomStart = 12.dp,
                        bottomEnd = 12.dp
                    )
                ),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                modifier = Modifier
                    .padding(horizontal = 10.dp),
                imageVector = Icons.Rounded.ArrowBackIosNew,
                contentDescription = "BlackReturnIcon"
            )
            Spacer(modifier = Modifier.weight(1f))
            Icon(
                modifier = Modifier
                    .padding(horizontal = 10.dp),
                imageVector = Icons.Rounded.Menu,
                contentDescription = "BlackMenuIcon"
            )
        }
    }
}

