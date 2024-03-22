package io.github.vrcmteam.vrcm.presentation.screens.profile

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
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
import io.github.vrcmteam.vrcm.getAppPlatform
import io.github.vrcmteam.vrcm.network.api.attributes.IUser
import io.github.vrcmteam.vrcm.presentation.compoments.AImage
import io.github.vrcmteam.vrcm.presentation.extensions.enableIf
import io.github.vrcmteam.vrcm.presentation.extensions.openUrl
import io.github.vrcmteam.vrcm.presentation.screens.auth.AuthAnimeScreen
import io.github.vrcmteam.vrcm.presentation.supports.LanguageIcon
import io.github.vrcmteam.vrcm.presentation.supports.WebIcons
import io.github.vrcmteam.vrcm.presentation.supports.thresholdNestedScrollConnection
import io.github.vrcmteam.vrcm.presentation.theme.GameColor
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import kotlin.math.roundToInt

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
                currentNavigator replace AuthAnimeScreen(false)
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
        // 位移的距离
        val offsetDp = with(LocalDensity.current) { scrollState.value.toDp() }
        // 剩下的距离
        val remainingDistance = imageHeight - offsetDp

        // image上滑比例
        val ratio = ((remainingDistance / imageHeight).coerceIn(0f, 1f)).let {
            FastOutSlowInEasing.transform(it)
        }
        // 模糊程度随着上滑的距离增加
        val fl = scrollState.value / imageHeight.value
        val blurDp = with(LocalDensity.current) { (fl * 20).toDp() }
        // topBar高度
        val topBarHeight = 64.dp
        // 系统栏高度
        val sysTopPadding = with(LocalDensity.current) {
            WindowInsets.systemBars.getTop(this).toDp()
        }
        // icon显示大小与透明度比例 单独一个比例是为了更加自然不会突然出现(因为icon只有当image隐藏时才开始显示)
        val topIconRatio =
            (remainingDistance / (topBarHeight + sysTopPadding)).coerceIn(0f, 1f).let {
                FastOutSlowInEasing.transform(1f - it)
        }
        val lastIconPadding = imageHeight - (topBarHeight * ratio)
        val scope = rememberCoroutineScope()
        val isHidden = topBarHeight + sysTopPadding < remainingDistance
        // 嵌套滑动,当父组件没有滑到maxValue时，父组件将消费滚动偏移量
        val nestedScrollConnection = thresholdNestedScrollConnection({ scrollState.value < scrollState.maxValue }) {
            scope.launch { scrollState.scrollTo((scrollState.value + -it).roundToInt()) }
        }
//        if (ratio in (0.5f..1f)) {
//            scope.launch {
//                scrollState.animateScrollTo(0,spring(stiffness = Spring.StiffnessHigh))}
//        }else  {
//            scope.launch {
//                scrollState.animateScrollTo(scrollState.maxValue,spring(stiffness = Spring.StiffnessHigh))}
//        }
        Surface(
            Modifier
                .verticalScroll(scrollState)
                .height(imageHeight + maxHeight)
                .fillMaxWidth()
        ) {

            // 用户Image
            ProfileUserImage(
                imageHeight,
                offsetDp,
                ratio,
                blurDp,
                user?.profileImageUrl
            )
            // 底部信息卡片
            BottomCard(
                imageHeight,
                ratio,
                topBarHeight,
                sysTopPadding,
                nestedScrollConnection,
                user
            )
            // 顶部导航栏
            TopMenuBar(
                topBarHeight,
                sysTopPadding,
                offsetDp,
                ratio,
                onReturn = popBackStack,
                onMenu = { /*TODO*/ }
            )
            // 用户icon
            ProfileUserIcon(
                isHidden,
                lastIconPadding,
                offsetDp,
                imageHeight,
                topIconRatio,
                user?.iconUrl,
            ) { scope.launch {scrollState.animateScrollTo(0, spring(stiffness = Spring.StiffnessVeryLow))} }
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

/**
 * 底部信息卡片
 */
@Composable
private fun BottomCard(
    initUserIconPadding: Dp,
    ratio: Float,
    topBarHeight: Dp,
    sysTopPadding: Dp,
    nestedScrollConnection: NestedScrollConnection,
    user: IUser?
) {
    // image上滑反比例
    val inverseRatio = 1 - ratio
    val scrollState = rememberScrollState()
    if (inverseRatio == 0f ) {
        LaunchedEffect(Unit){
            scrollState.animateScrollTo(0)
        }
    }
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
            modifier = Modifier.nestedScroll(nestedScrollConnection),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Spacer(modifier = Modifier.height((topBarHeight + sysTopPadding) * inverseRatio))
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
                    .verticalScroll(scrollState)
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
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        speakLanguages.forEach { language ->
            LanguageIcon.getFlag(language)?.let {
                Image(
                    imageVector = it,
                    contentDescription = "LanguageIcon",
                    modifier = Modifier
                        .align(Alignment.CenterVertically)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .width(28.dp),
                    contentScale = ContentScale.FillWidth
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColumnScope.LinksRow(bioLinks: List<String>) {
    if (bioLinks.isEmpty()) {
        return
    }
    Row(
        modifier = Modifier
            .align(Alignment.CenterHorizontally)
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val appPlatform = getAppPlatform()
        bioLinks.forEach { link ->
            val webIconVector = WebIcons.selectIcon(link)
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
                        modifier = Modifier
                            .padding(6.dp)
                            .enableIf(webIconVector == null) { rotate(-45F) },
                        imageVector = webIconVector ?: Icons.Outlined.Link,
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
    topIconRatio: Float,
    avatarThumbnailImageUrl: String?,
    onClickIcon:  () -> Unit = {}
) {
    val iconSize = (60 * topIconRatio).dp
    Box {
        Box(
            modifier = Modifier
                .systemBarsPadding()
                .fillMaxWidth()
                .enableIf(isHidden) { offset(y = offsetDp - imageHeight) }
                .padding(top = lastIconPadding)
                .alpha(topIconRatio),
        ) {
            AImage(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(CircleShape)
                    .size(iconSize)
                    .clickable(onClick = onClickIcon),
                imageData = ImageRequest.Builder(koinInject<PlatformContext>())
                    .data(avatarThumbnailImageUrl)
                    .crossfade(600)
                    .size(70, 70).build(),
                contentDescription = "UserIcon",
            )
        }
    }
}

@Composable
private fun TopMenuBar(
    topBarHeight: Dp,
    sysTopPadding: Dp,
    offsetDp: Dp,
    ratio: Float,
    color: Color = Color.White,
    onReturn: () -> Unit,
    onMenu: () -> Unit
) {
    // image上滑反比例
    val inverseRatio = 1 - ratio
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .height(topBarHeight + sysTopPadding)
                .offset(y = offsetDp)
                .background(
                    color.copy(alpha = inverseRatio), RoundedCornerShape(
                        bottomStart = 12.dp,
                        bottomEnd = 12.dp
                    )
                )
                .padding(top = sysTopPadding),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val iconColor = lerp(Color.White, Color.Black, inverseRatio)
            val actionColor = Color.Gray.copy(alpha = 0.3f * ratio)
            Icon(
                modifier = Modifier
                    .padding(horizontal = 10.dp)
                    .clip(CircleShape)
                    .background(actionColor)
                    .clickable(onClick = onReturn)
                    .padding(5.dp),
                imageVector = Icons.Rounded.ArrowBackIosNew,
                tint = iconColor,
                contentDescription = "WhiteReturnIcon"
            )
            Spacer(modifier = Modifier.weight(1f))
            Icon(
                modifier = Modifier
                    .padding(horizontal = 10.dp)
                    .clip(CircleShape)
                    .background(actionColor)
                    .clickable(onClick = onMenu)
                    .padding(5.dp),
                imageVector = Icons.Rounded.Menu,
                tint = iconColor,
                contentDescription = "WhiteMenuIcon"
            )
        }
    }
}

