package io.github.vrcmteam.vrcm.screens.profile

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.*
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
import io.github.vrcmteam.vrcm.MainRouteEnum
import io.github.vrcmteam.vrcm.data.api.users.UserData
import io.github.vrcmteam.vrcm.screens.theme.GameColor
import io.github.vrcmteam.vrcm.screens.theme.MediumRoundedShape
import io.github.vrcmteam.vrcm.screens.theme.SmallRoundedShape
import io.github.vrcmteam.vrcm.screens.util.AImage
import io.github.vrcmteam.vrcm.screens.util.capitalizeFirst
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
    // Token失效时返回重新登陆
    val onErrorToAuthPage = remember { { onNavigate(MainRouteEnum.AuthAnime, true, listOf(true)) } }
    LaunchedEffect(Unit) {
        profileViewModel.refreshUser(userId,onErrorToAuthPage)
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
        val trustRank = remember(user) { user.trustRank }
        val rankColor: Color = GameColor.Rank.fromValue(trustRank)
        val speakLanguages = remember(user) { user.speakLanguages }
        val statusDescription = when (user.statusDescription.isBlank()) {
            true -> user.status.value
            else -> user.statusDescription
        }
        Spacer(modifier = Modifier.height((topBarHeight * inverseRatio).dp))
        // TrustRank + UserName + VRC+
        UserInfoRow(user.displayName, user.isSupporter, rankColor)
        // status
        StatusRow(statusColor, statusDescription)
        // speakLanguages
        LanguagesRow(speakLanguages)

        HorizontalDivider(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, start = 50.dp, end = 50.dp)
                .align(Alignment.CenterHorizontally),
            color = Color.LightGray,
            thickness = 1.dp,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp)
                .background(CardDefaults.cardColors().containerColor)
                .align(Alignment.CenterHorizontally)
        ) {
            Text(
                modifier = Modifier.padding(10.dp),
                text = user.bio
            )
        }
    }
}

@Composable
private fun ColumnScope.UserInfoRow(
    userName: String,
    isSupporter: Boolean,
    rankColor: Color
) {
    Row(
        modifier = Modifier.Companion
            .align(Alignment.CenterHorizontally)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            modifier = Modifier
                .size(26.dp)
                .align(Alignment.CenterVertically),
            imageVector = Icons.Rounded.Shield,
            contentDescription = "TrustRankIcon",
            tint = rankColor
        )
        Text(
            text = userName,
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp,
        )

        Box(modifier = Modifier
            .size(26.dp)
            .align(Alignment.Top)
        ){
            Icon(
                modifier = Modifier
                    .size(20.dp)
                    .align(Alignment.TopStart),
                imageVector = Icons.Rounded.Add,
                contentDescription = "SupporterIcon",
                tint = if (isSupporter) GameColor.Supporter else Color.Transparent
            )
        }
    }
}

@Composable
private fun ColumnScope.StatusRow(
    statusColor: Color,
    statusDescription: String
) {
    Row(
        modifier = Modifier.align(Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Canvas(
            modifier = Modifier
                .size(12.dp)
        ) { drawCircle(statusColor) }
        Text(text = statusDescription)
    }
}

@Composable
private fun ColumnScope.LanguagesRow(speakLanguages: List<String>) {
    Row(
        modifier = Modifier
            .padding(top = 10.dp)
            .align(Alignment.CenterHorizontally),
    ) {
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

