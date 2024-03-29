package io.github.vrcmteam.vrcm.presentation.screens.profile

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CornerSize
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.lifecycle.LifecycleEffect
import cafe.adriel.voyager.core.screen.Screen
import coil3.PlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import io.github.vrcmteam.vrcm.getAppPlatform
import io.github.vrcmteam.vrcm.presentation.compoments.AImage
import io.github.vrcmteam.vrcm.presentation.extensions.createFailureCallbackDoNavigation
import io.github.vrcmteam.vrcm.presentation.extensions.currentNavigator
import io.github.vrcmteam.vrcm.presentation.extensions.enableIf
import io.github.vrcmteam.vrcm.presentation.extensions.getCallbackScreenModel
import io.github.vrcmteam.vrcm.presentation.extensions.getInsetPadding
import io.github.vrcmteam.vrcm.presentation.extensions.openUrl
import io.github.vrcmteam.vrcm.presentation.screens.auth.AuthAnimeScreen
import io.github.vrcmteam.vrcm.presentation.screens.profile.data.ProfileUserVO
import io.github.vrcmteam.vrcm.presentation.supports.LanguageIcon
import io.github.vrcmteam.vrcm.presentation.supports.WebIcons
import io.github.vrcmteam.vrcm.presentation.supports.thresholdNestedScrollConnection
import io.github.vrcmteam.vrcm.presentation.theme.GameColor
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import kotlin.math.roundToInt

/**
 * BottomCard和ProfileUserImage接触点的圆角大小
 */
private const val ContactPointShape = 36

data class ProfileScreen(
    private val profileUserVO: ProfileUserVO
) : Screen {
    @Composable
    override fun Content() {
        val currentNavigator = currentNavigator
        val profileScreenModel: ProfileScreenModel = getCallbackScreenModel(
            createFailureCallbackDoNavigation { AuthAnimeScreen(false) }
        )
        LifecycleEffect(
            onStarted = { profileScreenModel.initUserState(profileUserVO) }
        )
        LaunchedEffect(Unit) {
            profileScreenModel.refreshUser(profileUserVO.id)
        }
        val currentUser = profileScreenModel.userState
        FriedScreen(currentUser) { currentNavigator.pop() }
    }
}

@Composable
fun FriedScreen(
    profileUserVO: ProfileUserVO?,
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
        val nestedScrollConnection =
            thresholdNestedScrollConnection({ scrollState.value < scrollState.maxValue }) {
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
                .fillMaxWidth(),
            contentColor = MaterialTheme.colorScheme.primary
        ) {

            // 用户Image
            ProfileUserImage(
                imageHeight,
                offsetDp,
                ratio,
                blurDp,
                profileUserVO?.profileImageUrl
            )
            // 底部信息卡片
            BottomCard(
                imageHeight,
                ratio,
                topBarHeight,
                sysTopPadding,
                nestedScrollConnection,
                profileUserVO
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
                profileUserVO?.iconUrl,
            ) {
                scope.launch {
                    scrollState.animateScrollTo(
                        0,
                        spring(stiffness = Spring.StiffnessLow)
                    )
                }
            }
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
                        bottomStart = (ContactPointShape * ratio).dp,
                        bottomEnd = (ContactPointShape * ratio).dp
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
    profileUserVO: ProfileUserVO?
) {
    // image上滑反比例
    val inverseRatio = 1 - ratio
    val scrollState = rememberScrollState()
    if (inverseRatio == 0f) {
        LaunchedEffect(Unit) {
            scrollState.animateScrollTo(0)
        }
    }
    Card(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = initUserIconPadding),
        shape = RoundedCornerShape(
            topStart = (ContactPointShape * ratio).dp,
            topEnd = (ContactPointShape * ratio).dp
        ),
        colors = CardDefaults.cardColors(contentColor = MaterialTheme.colorScheme.primary)
    ) {
        if (profileUserVO == null) return@Card
        Column(
            modifier = Modifier
                .nestedScroll(nestedScrollConnection)
                .padding(
                    start = 12.dp,
                    end = 12.dp,
                    top = 12.dp,
                    bottom = getInsetPadding(12, WindowInsets::getBottom)
                ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height((topBarHeight + sysTopPadding) * inverseRatio))
            val rankColor = GameColor.Rank.fromValue(profileUserVO.trustRank)
            val statusColor = GameColor.Status.fromValue(profileUserVO.status)
            val statusDescription =
                profileUserVO.statusDescription.ifBlank { profileUserVO.status.value }
            // TrustRank + UserName + VRC+
            UserInfoRow(profileUserVO.displayName, profileUserVO.isSupporter, rankColor)
            // status
            StatusRow(statusColor, statusDescription)
            // LanguagesRow && LinksRow
            LangAndLinkRow(profileUserVO)
            Box(
                modifier = Modifier
                    .padding(top = 12.dp)
            ) {
                BottomCardTab(scrollState, profileUserVO)
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun BottomCardTab(
    scrollState: ScrollState,
    profileUserVO: ProfileUserVO
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        var state by remember { mutableStateOf(0) }
        val titles = listOf("Bio", "Worlds", "Groups")
        PrimaryTabRow(
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .clip(MaterialTheme.shapes.extraLarge),
            selectedTabIndex = state
        ) {
            titles.forEachIndexed { index, title ->
                Tab(
                    selected = state == index,
                    onClick = { state = index },
                    text = { Text(text = title, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                )
            }
        }
        AnimatedContent(targetState = state) {
            when (it) {
                0 -> {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        contentColor = MaterialTheme.colorScheme.primary,
                        shape = MaterialTheme.shapes.extraLarge
                    ) {
                        // 加个内边距
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp)
                                .verticalScroll(scrollState),
                        ) {
                            Text(
                                text = profileUserVO.bio
                            )
                        }
                    }
                }

                else -> {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        contentColor = MaterialTheme.colorScheme.primary,
                        shape = MaterialTheme.shapes.extraLarge
                    ) {
                        // 加个内边距
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp)
                                .verticalScroll(scrollState),
                        ) {
                            Text(
                                text = titles[it]
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private inline fun LangAndLinkRow(profileUserVO: ProfileUserVO) {
    val speakLanguages = profileUserVO.speakLanguages
    val bioLinks = profileUserVO.bioLinks
    val width = 32.dp
    val rowSpaced = 6.dp
    if (speakLanguages.isNotEmpty() && bioLinks.isNotEmpty()) {
        Row(
            modifier = Modifier.height(width),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(rowSpaced),
        ) {
            // speakLanguages 和 bioLinks 最大大小为3，填充下让分割线居中
            repeat(3 - speakLanguages.size){
                Spacer(modifier = Modifier.width((width)))
            }
            // speakLanguages
            LanguagesRow(speakLanguages, width)
            VerticalDivider(
                modifier = Modifier.padding(vertical = 2.dp),
                color = MaterialTheme.colorScheme.inversePrimary,
                thickness = 1.dp,
            )
            // bioLinks
            LinksRow(bioLinks, width)
            repeat(3 - bioLinks.size){
                Spacer(modifier = Modifier.width((width)))
            }
        }
    } else if (speakLanguages.isNotEmpty()) {
        LanguagesRow(speakLanguages, width)
    } else if (bioLinks.isNotEmpty()) {
        LinksRow(bioLinks, width)
    }
}


@Composable
private fun UserInfoRow(
    userName: String,
    isSupporter: Boolean,
    rankColor: Color
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            modifier = Modifier
                .size(24.dp)
                .align(Alignment.CenterVertically),
            imageVector = Icons.Rounded.Shield,
            contentDescription = "TrustRankIcon",
            tint = rankColor
        )
        // vrc+ 标志绘制在名字右上角
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
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1
            )
        }
        // 让名字居中
        Box(modifier = Modifier.size(24.dp).align(Alignment.Top))
    }
}


@Composable
private fun StatusRow(
    statusColor: Color,
    statusDescription: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Canvas(
            modifier = Modifier.size(12.dp)
        ) {
            drawCircle(statusColor)
        }
        Text(
            text = statusDescription,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1
        )
    }
}

@Composable
private fun LanguagesRow(
    speakLanguages: List<String>,
    width: Dp = 32.dp
) {
    if (speakLanguages.isEmpty()) {
        return
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        speakLanguages.forEach { language ->
            LanguageIcon.getFlag(language)?.let {
                Image(
                    imageVector = it,
                    contentDescription = "LanguageIcon",
                    modifier = Modifier
                        .align(Alignment.CenterVertically)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .width(width),
                    contentScale = ContentScale.FillWidth
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinksRow(
    bioLinks: List<String>,
    width: Dp = 32.dp
) {
    if (bioLinks.isEmpty()) {
        return
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
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
                    modifier = Modifier.size(width),
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
    onClickIcon: () -> Unit = {}
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
    color: Color = MaterialTheme.colorScheme.onPrimary,
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
                    color.copy(alpha = inverseRatio), MaterialTheme.shapes.medium.copy(
                        topStart = CornerSize(0.dp),
                        topEnd = CornerSize(0.dp)
                    )
                )
                .padding(top = sysTopPadding),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val iconColor = lerp(
                MaterialTheme.colorScheme.onPrimary,
                MaterialTheme.colorScheme.primary,
                inverseRatio
            )
            val actionColor = Color.Gray.copy(alpha = 0.3f * ratio)
            val iconButtonColors = IconButtonColors(
                containerColor = actionColor,
                contentColor = iconColor,
                disabledContainerColor = Color.Unspecified,
                disabledContentColor = Color.Unspecified,
            )
            IconButton(
                modifier = Modifier
                    .padding(horizontal = 10.dp),
                colors = iconButtonColors,
                onClick = onReturn
            ) {
                Icon(
                    imageVector = Icons.Rounded.ArrowBackIosNew,
                    tint = iconColor,
                    contentDescription = "ReturnIcon"
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            IconButton(
                modifier = Modifier
                    .padding(horizontal = 10.dp),
                colors = iconButtonColors,
                onClick = onMenu
            ) {
                Icon(
                    imageVector = Icons.Rounded.Menu,
                    tint = iconColor,
                    contentDescription = "MenuIcon"
                )
            }
        }
    }
}

