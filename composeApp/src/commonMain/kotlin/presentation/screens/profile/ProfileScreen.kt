package io.github.vrcmteam.vrcm.presentation.screens.profile

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Link
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.lifecycle.LifecycleEffect
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.getScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil3.PlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import io.github.vrcmteam.vrcm.core.extensions.capitalizeFirst
import io.github.vrcmteam.vrcm.getAppPlatform
import io.github.vrcmteam.vrcm.network.api.attributes.IUser
import io.github.vrcmteam.vrcm.presentation.compoments.AImage
import io.github.vrcmteam.vrcm.presentation.extensions.openUrl
import io.github.vrcmteam.vrcm.presentation.screens.auth.AuthAnimeScreen
import io.github.vrcmteam.vrcm.presentation.theme.GameColor
import io.github.vrcmteam.vrcm.presentation.theme.MediumRoundedShape
import io.github.vrcmteam.vrcm.presentation.theme.SmallRoundedShape
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject

@Serializable
data class ProfileScreen(
    private val user: IUser
) : Screen {
    @Composable
    override fun Content() {
        val profileScreenModel: ProfileScreenModel = getScreenModel()
        val currentNavigator = LocalNavigator.currentOrThrow
        LifecycleEffect(
            onStarted = { profileScreenModel.initUserState(user) }
        )
        LaunchedEffect(Unit) {
            profileScreenModel.refreshUser(user.id) {
                // Token失效时返回重新登陆
                currentNavigator.replace(AuthAnimeScreen(false))
            }
        }
        val currentUser = profileScreenModel.userState
        FriedScreen(currentUser) { currentNavigator.pop() }
    }
}

@Composable
fun FriedScreen(
    user: IUser?,
    popBackStack: () -> Unit,
) {
    BoxWithConstraints {
        val scrollState = rememberScrollState()
        val imageHeight = remember { maxHeight / 2.5f }
        val offsetDp = with(LocalDensity.current) { scrollState.value.toDp() }
        // 上滑比例
        val ratio =
            (((imageHeight - offsetDp) / imageHeight).let { if (it >= 0) it else 0f }).let {
                FastOutSlowInEasing.transform(it)
            }
        // 上滑反比例
        val inverseRatio = 1 - ratio
        // 模糊程度随着上滑的距离增加
        val fl = scrollState.value / imageHeight.value
        val blurDp = with(LocalDensity.current) { (fl * 20).toDp() }
        // topBar高度
        val topBarHeight = 70.dp

        val lastIconPadding = imageHeight - (topBarHeight * ratio)
        val isHidden = imageHeight == lastIconPadding

        Surface(
            Modifier
                .verticalScroll(scrollState)
                .height(imageHeight + maxHeight)
                .fillMaxWidth()
        ) {

            ProfileUserImage(
                imageHeight,
                offsetDp,
                ratio,
                blurDp,
                user?.profileImageUrl
            )

            BottomCard(
                imageHeight,
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
                onMenu = { /*TODO*/ }
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

}

@Composable
private fun ProfileUserImage(
    imageHeight: Dp,
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
                .height(imageHeight)
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
    initUserIconPadding: Dp,
    ratio: Float,
    topBarHeight: Dp,
    inverseRatio: Float,
    user: IUser?
) {
    Card(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = initUserIconPadding),
        shape = RoundedCornerShape(
            topStart = (30 * ratio).dp,
            topEnd = (30 * ratio).dp
        )
    ) {
        if (user == null) return@Card
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Spacer(modifier = Modifier.height((topBarHeight * inverseRatio)))
            val statusColor = GameColor.Status.fromValue(user.status)
            val trustRank = remember(user) { user.trustRank }
            val rankColor: Color = GameColor.Rank.fromValue(trustRank)
            val speakLanguages = remember(user) { user.speakLanguages }
            val statusDescription = when (user.statusDescription.isBlank()) {
                true -> user.status.value
                else -> user.statusDescription
            }
            // TrustRank + UserName + VRC+
            UserInfoRow(user.displayName, user.isSupporter, rankColor)
            // status
            StatusRow(statusColor, statusDescription)
            // speakLanguages
            LanguagesRow(speakLanguages)
            // bioLinks
            LinksRow(user.bioLinks)

            HorizontalDivider(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 50.dp)
                    .align(Alignment.CenterHorizontally),
                color = Color.LightGray,
                thickness = 1.dp,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp)
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
            .padding(horizontal = 20.dp),
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
        BadgedBox(
            badge = {
                if (isSupporter) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = "UserPlusIcon",
                        tint = GameColor.Supporter
                    )
                }
            }
        ) {
            Text(
                text = userName,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
            )
        }
        // 让名字居中
        Box(modifier = Modifier.size(26.dp).align(Alignment.Top))
    }
}


@Composable
private fun ColumnScope.StatusRow(
    statusColor: Color,
    statusDescription: String
) {
    Row(
        modifier = Modifier
            .align(Alignment.CenterHorizontally)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
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
    if (speakLanguages.isEmpty()) {
        return
    }
    Row(
        modifier = Modifier
            .align(Alignment.CenterHorizontally)
            .padding(horizontal = 20.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        speakLanguages.forEach { language ->
            Text(
                modifier = Modifier
                    .align(Alignment.CenterVertically)
                    .border(1.dp, MaterialTheme.colorScheme.primary, SmallRoundedShape)
                    .padding(horizontal = 6.dp, vertical = 3.dp)
                    .clip(MediumRoundedShape),
                text = remember(language) { language.capitalizeFirst() },
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ColumnScope.LinksRow(bioLinks: List<String>) {
    if (bioLinks.isEmpty()) {
        return
    }
    Row(
        modifier = Modifier
            .align(Alignment.CenterHorizontally)
            .padding(horizontal = 20.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val appPlatform = getAppPlatform()
        bioLinks.forEach { link ->
            TooltipBox(
                positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                tooltip = {
                    PlainTooltip {
                        Text(text = link)
                    }
                },
                state = rememberTooltipState()
            ) {
                FilledIconButton(
                    modifier = Modifier.size(36.dp),
                    onClick = { appPlatform.openUrl(link) },
                ) {
                    Icon(
                        modifier = Modifier.rotate(-45F),
                        imageVector = Icons.Outlined.Link,
                        contentDescription = "BioLinkIcon"
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileUserIcon(
    isHidden: Boolean,
    lastIconPadding: Dp,
    offsetDp: Dp,
    imageHeight: Dp,
    inverseRatio: Float,
    avatarThumbnailImageUrl: String?,
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
    onClickIcon: suspend () -> Unit = {}
) {
    val iconSize = (60 * inverseRatio).dp
    Box {
        Row(
            modifier = Modifier
                .run { if (isHidden) offset(y = offsetDp - imageHeight) else this }
                .padding(top = lastIconPadding + 5.dp)
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
                imageData = ImageRequest.Builder(koinInject<PlatformContext>())
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
    topBarHeight: Dp,
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
                .height(topBarHeight)
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
                .height(topBarHeight)
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

