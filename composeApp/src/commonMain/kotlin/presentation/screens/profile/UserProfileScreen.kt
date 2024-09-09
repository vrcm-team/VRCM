package io.github.vrcmteam.vrcm.presentation.screens.profile

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
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
import cafe.adriel.voyager.core.lifecycle.LifecycleEffect
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.getScreenModel
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.getAppPlatform
import io.github.vrcmteam.vrcm.presentation.compoments.ABottomSheet
import io.github.vrcmteam.vrcm.presentation.compoments.ProfileScaffold
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
    private val userProfileVO: UserProfileVo
) : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val currentNavigator = currentNavigator
        val userProfileScreenModel: UserProfileScreenModel = getScreenModel()
        LifecycleEffect(
            onStarted = { userProfileScreenModel.initUserState(userProfileVO) }
        )
        LaunchedEffect(Unit){
            SharedFlowCentre.logout.collect{
                currentNavigator replaceAll AuthAnimeScreen(false)
            }
        }
        LaunchedEffect(Unit) {
            userProfileScreenModel.refreshUser(userProfileVO.id)
        }
        val currentUser = userProfileScreenModel.userState
        var openAlertDialog by remember { mutableStateOf(false) }
        var bottomSheetIsVisible by remember { mutableStateOf(false) }
        val sheetState = rememberModalBottomSheetState()
        val scope = rememberCoroutineScope()
        val localeStrings = strings

        ProfileScaffold(
            profileImageUrl = currentUser?.profileImageUrl,
            iconUrl = currentUser?.iconUrl,
            onReturn = { currentNavigator.pop() },
            onMenu = { bottomSheetIsVisible = true },
        ) { ratio ->
            ProfileContent(currentUser, ratio)
        }
        if (currentUser == null) return
        ABottomSheet(
            isVisible = bottomSheetIsVisible,
            sheetState = sheetState,
            onDismissRequest = {  bottomSheetIsVisible = false }
        ){
            SheetButtonItem(localeStrings.profileSendFriendRequest) {
                scope.launch {
                    userProfileScreenModel.sendFriendRequest(currentUser.id, localeStrings)
                    sheetState.hide()
                }.invokeOnCompletion {
                    if (sheetState.isVisible) return@invokeOnCompletion
                    bottomSheetIsVisible = false
                }
            }
            SheetButtonItem(localeStrings.profileViewJsonData) {
                scope.launch { sheetState.hide() }.invokeOnCompletion {
                    if (sheetState.isVisible) return@invokeOnCompletion
                    bottomSheetIsVisible = false
                    openAlertDialog = true
                }
            }
        }
        JsonAlertDialog(
            openAlertDialog = openAlertDialog,
            onDismissRequest = { openAlertDialog = false }
        ){
            Text(text = userProfileScreenModel.userJson)
        }
    }



}

@Composable
private fun ColumnScope.SheetButtonItem(
    text: String,
    onClick: () -> Unit
) {
    TextButton(
        modifier = Modifier.Companion.align(Alignment.CenterHorizontally),
        onClick = onClick
    ) {
        Text(text = text)
    }
}

@Composable
private fun JsonAlertDialog(
    openAlertDialog: Boolean,
    onDismissRequest: () -> Unit,
    content: @Composable BoxScope.() -> Unit
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
    ratio: Float
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
    UserInfoRow(currentUser.displayName, currentUser.isSupporter, rankColor)
    // status
    StatusRow(statusColor, statusDescription)
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
    userProfileVO: UserProfileVo
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
                            Text(
                                text = userProfileVO.bio
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
            repeat(3 - speakLanguages.size){
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguagesRow(
    speakLanguages: List<String>,
    width: Dp = 32.dp
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
                if (imageVector == null){
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(width)
                            .padding(vertical = 3.dp)
                            .background(MaterialTheme.colorScheme.inversePrimary,MaterialTheme.shapes.extraSmall)
                    ){
                        Icon(
                            modifier = Modifier.align(Alignment.Center),
                            imageVector = Icons.Rounded.QuestionMark,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            contentDescription = "NotKnownLanguageIcon",
                        )
                    }
                }else{
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
    width: Dp = 32.dp
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
