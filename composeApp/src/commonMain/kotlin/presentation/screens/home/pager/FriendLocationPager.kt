package io.github.vrcmteam.vrcm.presentation.screens.home.pager

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.network.api.attributes.LocationType
import io.github.vrcmteam.vrcm.network.api.friends.date.FriendData
import io.github.vrcmteam.vrcm.presentation.compoments.ListStateOverlay
import io.github.vrcmteam.vrcm.presentation.compoments.LocalSharedSuffixKey
import io.github.vrcmteam.vrcm.presentation.compoments.LocationCard
import io.github.vrcmteam.vrcm.presentation.compoments.RefreshBox
import io.github.vrcmteam.vrcm.presentation.compoments.UserIconsGridRow
import io.github.vrcmteam.vrcm.presentation.compoments.UserIconsRow
import io.github.vrcmteam.vrcm.presentation.compoments.UserStateIcon
import io.github.vrcmteam.vrcm.presentation.compoments.userIconsGridColumns
import io.github.vrcmteam.vrcm.presentation.compoments.userIconsGridRowsToFill
import io.github.vrcmteam.vrcm.presentation.adaptive.AppWindowWidthClass
import io.github.vrcmteam.vrcm.presentation.adaptive.LocalAppWindowWidthClass
import io.github.vrcmteam.vrcm.presentation.compoments.withContentTopInset
import io.github.vrcmteam.vrcm.presentation.designsystem.AppDivider
import io.github.vrcmteam.vrcm.presentation.designsystem.AppGroupedItem
import io.github.vrcmteam.vrcm.presentation.designsystem.AppIcon
import io.github.vrcmteam.vrcm.presentation.designsystem.AppRow
import io.github.vrcmteam.vrcm.presentation.designsystem.AppRowIcon
import io.github.vrcmteam.vrcm.presentation.designsystem.AppRowIconColor
import io.github.vrcmteam.vrcm.presentation.designsystem.AppRowIconDividerInset
import io.github.vrcmteam.vrcm.presentation.designsystem.AppSpacing
import io.github.vrcmteam.vrcm.presentation.designsystem.AppText
import io.github.vrcmteam.vrcm.presentation.designsystem.AppTheme
import io.github.vrcmteam.vrcm.presentation.extensions.animateScrollToFirst
import io.github.vrcmteam.vrcm.presentation.extensions.currentNavigator
import io.github.vrcmteam.vrcm.presentation.extensions.getInsetPadding
import io.github.vrcmteam.vrcm.presentation.screens.home.data.FriendLocation
import io.github.vrcmteam.vrcm.presentation.screens.user.UserProfileScreen
import io.github.vrcmteam.vrcm.presentation.screens.user.data.UserProfileVo
import io.github.vrcmteam.vrcm.presentation.screens.world.WorldProfileScreen
import io.github.vrcmteam.vrcm.presentation.screens.world.data.WorldProfileVo
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings
import io.github.vrcmteam.vrcm.presentation.supports.AppIcons
import io.github.vrcmteam.vrcm.presentation.supports.Pager
import kotlinx.coroutines.launch
import org.koin.compose.koinInject


object FriendLocationPager : Pager {

    override val index: Int
        get() = 0
    override val title: String
        @Composable
        get() = "Location"

    override val icon: Painter
        @Composable get() = rememberVectorPainter(image = AppIcons.Explore)

    @ExperimentalSharedTransitionApi
    @Composable
    override fun Content() {
        ContentInternal(headerContent = null, isActive = { true })
    }

    @ExperimentalSharedTransitionApi
    @Composable
    fun Content(
        isActive: () -> Boolean,
    ) {
        ContentInternal(headerContent = null, isActive = isActive)
    }

    @ExperimentalSharedTransitionApi
    @Composable
    private fun ContentInternal(
        headerContent: (@Composable () -> Unit)?,
        isActive: () -> Boolean,
    ) {
        val friendLocationPagerModel: FriendLocationPagerModel = koinInject()
        val currentIsActive by rememberUpdatedState(isActive)
        val lazyListState = rememberLazyListState()
        val isRefreshing by friendLocationPagerModel.isRefreshing.collectAsState()
        LaunchedEffect(Unit) {
            SharedFlowCentre.toPagerTop.collect {
                // 子任务承接手势取消，避免终止长期 Flow 监听。
                if (currentIsActive()) launch { lazyListState.animateScrollToFirst() }
            }
        }

        FriendLocationPager(
            friendLocationMap = friendLocationPagerModel.friendLocationMap,
            currentUserId = friendLocationPagerModel.currentUserId,
            isRefreshing = isRefreshing,
            lazyListState = lazyListState,
            doRefresh = { friendLocationPagerModel.refreshFriendLocation() },
            headerContent = headerContent,
        )

    }

}



@Composable
fun Pager.FriendLocationPager(
    friendLocationMap: Map<LocationType, MutableList<FriendLocation>>,
    currentUserId: String,
    isRefreshing: Boolean,
    lazyListState: LazyListState = rememberLazyListState(),
    doRefresh: suspend () -> Unit,
    headerContent: (@Composable () -> Unit)? = null,
) {
    val navigator = currentNavigator
    // 只会有一个位置被展开：房间卡和"其他在线"里的私人世界 / 网页在线共用这一个选中项
    var selectLocation by rememberSaveable { mutableStateOf<String?>(null) }
    // 其他在线的头像网格随它展开 / 收起；它播完（currentState 追上目标）才接上超出一屏的懒加载行
    val selectTransition = updateTransition(targetState = selectLocation, label = "FriendLocationSelect")
    val onClickLocationCard: (FriendLocation) -> Unit = { friendLocation ->
        // 如果当前位置实例和点击的位置实例相同，则收起卡片，否则展开选中的，收起之前的
        selectLocation = if (selectLocation == friendLocation.location) null else friendLocation.location
    }
    val onClickWorldImage: (FriendLocation, String) -> Unit = { friendLocation, sharedSuffixKey ->
        val currentLocation = friendLocation.instants.value
        // 创建临时的 WorldProfileVo
        val tempWorldProfileVo = WorldProfileVo(
            worldId = currentLocation.worldId,
            worldName = currentLocation.worldName,
            worldImageUrl = currentLocation.worldImageUrl,
            worldDescription = currentLocation.worldDescription,
            authorID = currentLocation.worldAuthorId,
            authorName = currentLocation.worldAuthorName,
            tags = currentLocation.worldAuthorTag,
        )
        navigator push WorldProfileScreen(
            worldProfileVO = tempWorldProfileVo,
            location = friendLocation.location,
            sharedSuffixKey = sharedSuffixKey,
            sharedImageCacheKey = currentLocation.worldImageUrl,
        )
    }
    val topPadding = if (headerContent == null) 12.dp else 0.dp
    val onClickUserIcon = { user: FriendData, sharedSuffixKey: String ->
        navigator push UserProfileScreen(
            userProfileVO = UserProfileVo(user),
            sharedSuffixKey = sharedSuffixKey
        )
    }
    RefreshBox(
        refreshContainerOffsetY = topPadding,
        isRefreshing = isRefreshing,
        doRefresh = doRefresh
    ) {
        // 私人世界在前、网页在线在后，没人的组不显示
        val presenceLocations = listOfNotNull(
            friendLocationMap[LocationType.Private]?.get(0),
            friendLocationMap[LocationType.Web]?.get(0),
        ).filter { it.friends.isNotEmpty() }
        val instanceFriendLocations = friendLocationMap[LocationType.Instance]
            ?.sortedWith(compareByDescending<FriendLocation> { location ->
                location.friends.containsKey(currentUserId)
            }.thenByDescending { it.friendList.size })
        // 如果没有底部系统手势条，默认12dp
        val bottomNavigationPadding = if (
            LocalAppWindowWidthClass.current == AppWindowWidthClass.Compact
        ) 80.dp else 0.dp
        val bottomPadding = getInsetPadding(12, WindowInsets::getBottom) + bottomNavigationPadding
        val isEmpty = instanceFriendLocations.isNullOrEmpty() && presenceLocations.isEmpty()
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            // 头像网格排在卡片里：扣掉卡片外边距和网格内边距，再按头像宽度算列数
            val presenceColumns = userIconsGridColumns(maxWidth - (AppSpacing.page + PresenceGridPadding) * 2)
            // 展开动画只播铺满一屏的这几行，更多的行动画播完后再以懒加载 item 接上
            val presenceAnimatedRows = userIconsGridRowsToFill(maxHeight, PresenceGridPadding)
            // 不用 spacedBy：其他在线卡片由多个 item 拼成，行与行之间不能留缝，
            // 块间距改由每块（标题、每张房间卡、其他在线卡片）第一个 item 的上边距给出
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = lazyListState,
                contentPadding = PaddingValues(
                    top = topPadding,
                    bottom = bottomPadding
                ).withContentTopInset()
            ) {

                if (headerContent != null) {
                    item(key = "friend-location-header") {
                        headerContent()
                    }
                }

                if (!instanceFriendLocations.isNullOrEmpty()) {
                    item(key = LocationType.Instance) {
                        LocationTitle(
                            text = "${strings.fiendLocationPagerLocation}(${instanceFriendLocations.map { it.friends.size }.count()})",
                            modifier = Modifier.padding(
                                top = if (headerContent != null) LocationBlockSpacing else 0.dp,
                            ),
                        )
                    }

                    items(instanceFriendLocations, key = { it.location }) { location ->
                        LocationCard(
                            modifier = Modifier
                                .padding(top = LocationBlockSpacing)
                                .padding(horizontal = AppSpacing.page),
                            location = location,
                            isSelected = selectLocation == location.location,
                            onClickWorldImage = { sharedSuffixKey ->
                                onClickWorldImage(location, sharedSuffixKey)
                            },
                            onClickLocationCard = { onClickLocationCard(location) },
                            travelingIds = location.travelingIds.value,
                            isCurrentUserLocation = location.friends.containsKey(currentUserId),
                        ) {
                            UserIconsRow(
                                modifier = Modifier.fillMaxWidth(),
                                instanceId = location.location,
                                friends = it,
                                travelingIds = location.travelingIds.value,
                                onClickUserIcon = onClickUserIcon
                            )
                        }
                    }
                }

                presenceItems(
                    locations = presenceLocations,
                    selectTransition = selectTransition,
                    columns = presenceColumns,
                    animatedRowCount = presenceAnimatedRows,
                    titleTopSpacing = if (headerContent != null || !instanceFriendLocations.isNullOrEmpty()) {
                        LocationBlockSpacing
                    } else {
                        0.dp
                    },
                    onClickSummary = onClickLocationCard,
                    onClickUserIcon = onClickUserIcon,
                )

            }
        }
        ListStateOverlay(isEmpty = isEmpty, isLoading = isRefreshing)
    }


//    FriendLocationBottomSheet(bottomSheetIsVisible, sheetState, currentLocation) {
//        bottomSheetIsVisible = false
//    }
}

/** 位置页各块（标题、每张房间卡、其他在线卡片）之间的间距。 */
private val LocationBlockSpacing = 16.dp

/** 其他在线卡片里头像网格的内边距，也是网格的行距。 */
private val PresenceGridPadding = AppSpacing.row

/** 摘要行叠放的头像个数：窄屏上标题也要能一行放下。 */
private const val PresenceFacepileSize = 3

private class PresenceSection(
    val location: FriendLocation,
    val friends: List<State<FriendData>>,
    val expanded: Boolean,
    /** 跟摘要行放在同一个 item、随展开 / 收起动画出现和消失的网格行（最多一屏）。 */
    val animatedRows: List<List<State<FriendData>>>,
    /** 超出一屏的网格行：展开动画播完才接成懒加载 item，收起时立刻去掉（这时它们都在屏幕外）。 */
    val lazyRows: List<List<State<FriendData>>>,
)

/**
 * 其他在线：私人世界、网页在线两组拼成一张分组卡片，每组一行摘要（叠放头像 + 人数），
 * 点行在卡片里展开这组的头像网格，展开 / 收起的动画和房间卡一样由 AnimatedVisibility 播。
 * 播动画的只有铺满一屏的前 [animatedRowCount] 行，更多的行按行拆成懒加载 item，人再多也只组合看得见的部分。
 */
private fun LazyListScope.presenceItems(
    locations: List<FriendLocation>,
    selectTransition: Transition<String?>,
    columns: Int,
    animatedRowCount: Int,
    titleTopSpacing: Dp,
    onClickSummary: (FriendLocation) -> Unit,
    onClickUserIcon: (FriendData, String) -> Unit,
) {
    if (locations.isEmpty()) return
    item(key = "friend-location-presence-title") {
        LocationTitle(
            text = strings.fiendLocationPagerOtherOnline,
            modifier = Modifier.padding(top = titleTopSpacing),
        )
    }
    val sections = locations.map { location ->
        val key = location.location
        val friends = location.friendList
        val expanded = selectTransition.targetState == key
        // 收起动画播放期间 currentState 仍是它，网格要留着给动画收
        val gridRows = if (expanded || selectTransition.currentState == key) friends.chunked(columns) else emptyList()
        val settled = expanded && selectTransition.currentState == key
        PresenceSection(
            location = location,
            friends = friends,
            expanded = expanded,
            animatedRows = gridRows.take(animatedRowCount),
            lazyRows = if (settled) gridRows.drop(animatedRowCount) else emptyList(),
        )
    }
    // 卡片的圆角按整张卡片里的位置取：首个 item 圆上角、最后一个圆下角
    val cardItemCount = sections.sumOf { 1 + it.lazyRows.size }
    var cardItemIndex = 0
    sections.forEach { section ->
        val key = section.location.location
        val summaryIndex = cardItemIndex++
        item(key = key, contentType = "presence-summary") {
            AppGroupedItem(
                index = summaryIndex,
                count = cardItemCount,
                // 只淡入不做位移动画：上方房间卡展开时本行要跟着立即下移，不能慢半拍
                modifier = Modifier
                    .animateItem(placementSpec = null, fadeOutSpec = null)
                    .padding(top = if (summaryIndex == 0) LocationBlockSpacing else 0.dp)
                    .padding(horizontal = AppSpacing.page),
                dividerInset = AppRowIconDividerInset,
            ) {
                PresenceSummaryRow(
                    locationType = LocationType.fromValue(key),
                    friends = section.friends,
                    expanded = section.expanded,
                    onClick = { onClickSummary(section.location) },
                )
                selectTransition.AnimatedVisibility(
                    visible = { it == key },
                    enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
                    exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top),
                ) {
                    Column {
                        AppDivider(Modifier.padding(start = AppSpacing.row))
                        section.animatedRows.forEachIndexed { rowIndex, rowFriends ->
                            PresenceGridRow(
                                friends = rowFriends,
                                columns = columns,
                                isFirst = rowIndex == 0,
                                onClickUserIcon = onClickUserIcon,
                            )
                        }
                    }
                }
            }
        }
        section.lazyRows.forEachIndexed { rowIndex, rowFriends ->
            val gridIndex = cardItemIndex++
            item(key = "$key:${animatedRowCount + rowIndex}", contentType = "presence-grid-row") {
                AppGroupedItem(
                    index = gridIndex,
                    count = cardItemCount,
                    modifier = Modifier
                        .animateItem(placementSpec = null, fadeOutSpec = null)
                        .padding(horizontal = AppSpacing.page),
                    showDivider = false,
                ) {
                    PresenceGridRow(
                        friends = rowFriends,
                        columns = columns,
                        isFirst = false,
                        onClickUserIcon = onClickUserIcon,
                    )
                }
            }
        }
    }
}

/** 网格的一行：每行只在下方留行距，第一行另在上方留出与分隔线的间距，末行下方的行距就是网格的底边距。 */
@Composable
private fun PresenceGridRow(
    friends: List<State<FriendData>>,
    columns: Int,
    isFirst: Boolean,
    onClickUserIcon: (FriendData, String) -> Unit,
) {
    UserIconsGridRow(
        modifier = Modifier.padding(
            start = PresenceGridPadding,
            top = if (isFirst) PresenceGridPadding else 0.dp,
            end = PresenceGridPadding,
            bottom = PresenceGridPadding,
        ),
        friends = friends,
        columns = columns,
        onClickUserIcon = onClickUserIcon,
    )
}

@Composable
private fun PresenceSummaryRow(
    locationType: LocationType,
    friends: List<State<FriendData>>,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    val isPrivate = locationType == LocationType.Private
    val chevronRotation by animateFloatAsState(if (expanded) 180f else 0f)
    AppRow(
        title = if (isPrivate) strings.fiendLocationPagerPrivate else strings.fiendLocationPagerWebsite,
        subtitle = if (isPrivate) strings.fiendLocationPagerPrivateHint else strings.fiendLocationPagerWebsiteHint,
        leading = {
            AppRowIcon(
                icon = if (isPrivate) AppIcons.Lock else AppIcons.Globe,
                background = if (isPrivate) AppRowIconColor.Indigo else AppRowIconColor.Teal,
            )
        },
        trailing = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // 展开后网格里就是全部头像，叠放的这几个收起来
                AnimatedVisibility(visible = !expanded) {
                    PresenceFacepile(friends)
                }
                AppText(
                    text = friends.size.toString(),
                    style = AppTheme.type.body,
                    color = AppTheme.colors.secondaryLabel,
                )
                AppIcon(
                    imageVector = AppIcons.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp).rotate(chevronRotation),
                    tint = AppTheme.colors.tertiaryLabel,
                )
            }
        },
        onClick = onClick,
    )
}

/** 叠放头像，尺寸与描边同房间卡里的在场好友头像。 */
@Composable
private fun PresenceFacepile(friends: List<State<FriendData>>) {
    Row(horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
        friends.take(PresenceFacepileSize).forEach { friendState ->
            UserStateIcon(
                modifier = Modifier
                    .size(24.dp)
                    .border(1.dp, AppTheme.colors.secondaryGroupedBackground, CircleShape),
                iconUrl = friendState.value.iconUrl,
            )
        }
    }
}

@Composable
internal fun LocationTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    AppText(
        modifier = modifier.padding(horizontal = 16.dp),
        text = text,
        style = AppTheme.type.subheadlineEmphasized,
        color = AppTheme.colors.secondaryLabel,
    )
}
