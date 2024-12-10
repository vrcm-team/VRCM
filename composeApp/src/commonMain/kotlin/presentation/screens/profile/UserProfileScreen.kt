package io.github.vrcmteam.vrcm.presentation.screens.profile

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person4
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.QuestionMark
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.getAppPlatform
import io.github.vrcmteam.vrcm.network.api.attributes.FriendRequestStatus.*
import io.github.vrcmteam.vrcm.presentation.compoments.ABottomSheet
import io.github.vrcmteam.vrcm.presentation.compoments.LocalSharedSuffixKey
import io.github.vrcmteam.vrcm.presentation.compoments.ProfileScaffold
import io.github.vrcmteam.vrcm.presentation.compoments.sharedBoundsBy
import io.github.vrcmteam.vrcm.presentation.extensions.currentNavigator
import io.github.vrcmteam.vrcm.presentation.extensions.enableIf
import io.github.vrcmteam.vrcm.presentation.extensions.openUrl
import io.github.vrcmteam.vrcm.presentation.screens.auth.AuthAnimeScreen
import io.github.vrcmteam.vrcm.presentation.screens.profile.data.UserProfileVo
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings
import io.github.vrcmteam.vrcm.presentation.supports.LanguageIcon
import io.github.vrcmteam.vrcm.presentation.supports.WebIcons
import io.github.vrcmteam.vrcm.presentation.theme.GameColor
import kotlinx.coroutines.launch

data class UserProfileScreen(
    private val sharedSuffixKey: String = "",
    private val userProfileVO: UserProfileVo,
) : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @ExperimentalSharedTransitionApi
    @Composable
    override fun Content() {
        val currentNavigator = currentNavigator
        val userProfileScreenModel: UserProfileScreenModel = koinScreenModel()

        LaunchedEffect(Unit){
            userProfileScreenModel.initUserState(userProfileVO)
        }
        LaunchedEffect(Unit) {
            SharedFlowCentre.logout.collect {
                currentNavigator replaceAll AuthAnimeScreen(false)
            }
        }
        LaunchedEffect(Unit) {
            userProfileScreenModel.refreshUser(userProfileVO.id)
        }
        val currentUser = userProfileScreenModel.userState
        var bottomSheetIsVisible by remember { mutableStateOf(false) }
        val sheetState = rememberModalBottomSheetState()
        var openAlertDialog by remember { mutableStateOf(false) }
        CompositionLocalProvider(LocalSharedSuffixKey provides sharedSuffixKey){
            ProfileScaffold(
                imageModifier = Modifier.sharedBoundsBy("${userProfileVO.id}UserIcon"),
                profileImageUrl = currentUser?.profileImageUrl,
                iconUrl = currentUser?.iconUrl,
                onReturn = { currentNavigator.pop() },
                onMenu = { bottomSheetIsVisible = true },
            ) { ratio ->
                ProfileContent(
                    currentUser = currentUser,
                    ratio = ratio
                )
            }
        }
        if (currentUser == null) return
        ABottomSheet(
            isVisible = bottomSheetIsVisible,
            sheetState = sheetState,
            onDismissRequest = { bottomSheetIsVisible = false }
        ) {
            SheetItems(
                currentUser = currentUser,
                userProfileScreenModel = userProfileScreenModel,
                hideSheet = { sheetState.hide() },
                onHideCompletion = {
                    if (!sheetState.isVisible) bottomSheetIsVisible = false
                },
                openAlertDialog = { openAlertDialog = true }
            )
        }
        JsonAlertDialog(
            openAlertDialog = openAlertDialog,
            onDismissRequest = { openAlertDialog = false }
        ) {
            Text(text = userProfileScreenModel.userJson)
        }
    }

}

@Composable
private fun ColumnScope.SheetItems(
    currentUser: UserProfileVo,
    userProfileScreenModel: UserProfileScreenModel,
    hideSheet: suspend () -> Unit,
    onHideCompletion: () -> Unit,
    openAlertDialog: () -> Unit,
) {
    FriendRequestSheetItem(
        currentUser,
        userProfileScreenModel,
        hideSheet,
        onHideCompletion,
    )
    val scope = rememberCoroutineScope()
    val localeStrings = strings
    SheetButtonItem(localeStrings.profileViewJsonData, onClick = {
        scope.launch { hideSheet() }.invokeOnCompletion {
            onHideCompletion()
            openAlertDialog()
        }
    })

}

@Composable
private fun ColumnScope.FriendRequestSheetItem(
    currentUser: UserProfileVo,
    userProfileScreenModel: UserProfileScreenModel,
    hideSheet: suspend () -> Unit,
    onHideCompletion: () -> Unit,
) {
    val localeStrings = strings
    val action: Pair<String, suspend () -> Boolean>? = when {
        // 当前用户不是朋友且不是自己
        !currentUser.isFriend && !currentUser.isSelf -> {
            when(currentUser.friendRequestStatus){
                // 状态为Null,则发送好友请求
                Null -> localeStrings.profileSendFriendRequest to {
                    userProfileScreenModel.sendFriendRequest(currentUser.id, localeStrings.profileFriendRequestSent)
                }
                // 状态为Outgoing,则取消发送好友请求
                Outgoing -> localeStrings.profileDeleteFriendRequest to {
                    userProfileScreenModel.deleteFriendRequest(currentUser.id, localeStrings.profileFriendRequestDeleted)
                }

                // 状态为Incoming,则接受好友请求
                Incoming -> localeStrings.profileAcceptFriendRequest to {
                    userProfileScreenModel.acceptFriendRequest(currentUser.id, localeStrings.profileFriendRequestAccepted)
                }
                else -> null
            }
        }
        // 状态为Completed,则删除好友
        // TODO: 加一个弹窗提示是否删除好友
        currentUser.isFriend && currentUser.friendRequestStatus == Completed  ->
            localeStrings.profileUnfriend to {
            userProfileScreenModel.unfriend(currentUser.id, localeStrings.profileUnfriended)
        }
        else -> null
    }

    if (action == null) return

    val scope = rememberCoroutineScope()
    var enabled by remember { mutableStateOf(true) }
    SheetButtonItem(action.first, onClick = {
        scope.launch { hideSheet() }.invokeOnCompletion {
            scope.launch {
                enabled = false
                when(action.second()){
                    true -> hideSheet()
                    false -> enabled = true
                }
            }.invokeOnCompletion {
                onHideCompletion()
            }
        }
    })
}

@Composable
private fun ColumnScope.SheetButtonItem(
    text: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
    content: @Composable RowScope.(String) -> Unit = { Text(text = it) },
) {
    TextButton(
        modifier = Modifier
            .align(Alignment.CenterHorizontally)
            .fillMaxWidth()
            .padding(vertical = 2.dp, horizontal = 24.dp),
        enabled = enabled,
        onClick = onClick
    ) {
        content(text.orEmpty())
    }
}

@Composable
private fun JsonAlertDialog(
    openAlertDialog: Boolean,
    onDismissRequest: () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    if (openAlertDialog) {
        AlertDialog(
            icon = {
                Icon(Icons.Filled.Person4, contentDescription = "AlertDialogIcon")
            },
            text = {
                Box(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .horizontalScroll(rememberScrollState())
                ) {
                    content()
                }
            },
            onDismissRequest = onDismissRequest,
            confirmButton = {
                TextButton(
                    onClick = onDismissRequest
                ) {
                    Text("Back")
                }
            }
        )
    }
}

@Composable
private fun ProfileContent(
    currentUser: UserProfileVo?,
    ratio: Float,
) {
    if (currentUser == null) return
    val inverseRatio = 1 - ratio
    val scrollState = rememberScrollState()
    // 当上方图片完整显示时子内容自动滚动到顶部
    if (inverseRatio == 0f) {
        LaunchedEffect(Unit) {
            scrollState.animateScrollTo(0)
        }
    }
    val rankColor = GameColor.Rank.fromValue(currentUser.trustRank)
    val statusColor = GameColor.Status.fromValue(currentUser.status)
    val statusDescription = currentUser.statusDescription.ifBlank { currentUser.status.value }
    // TrustRank + UserName + VRC+
    UserInfoRow(currentUser.id, currentUser.displayName, currentUser.isSupporter, rankColor)
    // status
    StatusRow(currentUser.id, statusColor, statusDescription)
    // LanguagesRow && LinksRow
    LangAndLinkRow(currentUser)
    Box(
        modifier = Modifier
            .padding(top = 12.dp)
    ) {
        BottomCardTab(scrollState, currentUser)
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun BottomCardTab(
    scrollState: ScrollState,
    userProfileVO: UserProfileVo,
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
                    enabled = index == 0,
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
                            SelectionContainer {
                                Text(
                                    text = userProfileVO.bio
                                )
                            }
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
private inline fun LangAndLinkRow(userProfileVO: UserProfileVo) {
    val speakLanguages = userProfileVO.speakLanguages
    val bioLinks = userProfileVO.bioLinks
    val width = 32.dp
    val rowSpaced = 6.dp
    if (speakLanguages.isNotEmpty() && bioLinks.isNotEmpty()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(rowSpaced),
        ) {
            // speakLanguages 和 bioLinks 最大大小为3，填充下让分割线居中
            repeat(3 - speakLanguages.size) {
                Spacer(modifier = Modifier.width((width)))
            }
            // speakLanguages
            LanguagesRow(speakLanguages, width)
            VerticalDivider(
                modifier = Modifier.height(width).padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.primary,
                thickness = 1.dp,
            )
            // bioLinks
            LinksRow(bioLinks, width)
            repeat(3 - bioLinks.size) {
                Spacer(modifier = Modifier.width((width)))
            }
        }
    } else if (speakLanguages.isNotEmpty()) {
        LanguagesRow(speakLanguages, width)
    } else if (bioLinks.isNotEmpty()) {
        LinksRow(bioLinks, width)
    }
}


@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun UserInfoRow(
    userId: String,
    userName: String,
    isSupporter: Boolean,
    rankColor: Color,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            modifier = Modifier
                .sharedBoundsBy("${userId}UserTrustRankIcon")
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
                modifier = Modifier.sharedBoundsBy("${userId}UserName"),
                text = userName,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1
            )
        }
        // 让名字居中
        Box(modifier = Modifier.size(24.dp).align(Alignment.Top))
    }
}


@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun StatusRow(
    userId: String,
    statusColor: Color,
    statusDescription: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Canvas(
            modifier = Modifier.size(12.dp)
        ) {
            drawCircle(color = Color.White, radius = size.minDimension / 1.6f)
            drawCircle(color = statusColor, radius = size.minDimension / 2f)
        }
        Text(
            modifier = Modifier.sharedBoundsBy("${userId}UserStatusDescription"),
            text = statusDescription,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguagesRow(
    speakLanguages: List<String>,
    width: Dp = 32.dp,
) {
    if (speakLanguages.isEmpty()) {
        return
    }
    Row(
        modifier = Modifier.height(width),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        speakLanguages.forEach { language ->
            val imageVector = LanguageIcon.getFlag(language)
            TooltipBox(
                positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                tooltip = {
                    PlainTooltip {
                        Text(text = language)
                    }
                },
                state = rememberTooltipState()
            ) {
                if (imageVector == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(width)
                            .padding(vertical = 3.dp)
                            .background(MaterialTheme.colorScheme.inversePrimary, MaterialTheme.shapes.extraSmall)
                    ) {
                        Icon(
                            modifier = Modifier.align(Alignment.Center),
                            imageVector = Icons.Rounded.QuestionMark,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            contentDescription = "NotKnownLanguageIcon",
                        )
                    }
                } else {
                    Image(
                        imageVector = imageVector,
                        contentDescription = "LanguageIcon",
                        modifier = Modifier
                            .fillMaxHeight()
                            .align(Alignment.CenterVertically)
                            .clip(MaterialTheme.shapes.extraSmall)
                            .width(width),
                        contentScale = ContentScale.FillWidth
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinksRow(
    bioLinks: List<String>,
    width: Dp = 32.dp,
) {
    if (bioLinks.isEmpty()) {
        return
    }
    Row(
        modifier = Modifier.height(width),
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
