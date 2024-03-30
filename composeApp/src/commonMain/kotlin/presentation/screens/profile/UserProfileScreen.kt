package io.github.vrcmteam.vrcm.presentation.screens.profile

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person4
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.lifecycle.LifecycleEffect
import cafe.adriel.voyager.core.screen.Screen
import io.github.vrcmteam.vrcm.getAppPlatform
import io.github.vrcmteam.vrcm.presentation.compoments.ProfileScaffold
import io.github.vrcmteam.vrcm.presentation.extensions.createFailureCallbackDoNavigation
import io.github.vrcmteam.vrcm.presentation.extensions.currentNavigator
import io.github.vrcmteam.vrcm.presentation.extensions.enableIf
import io.github.vrcmteam.vrcm.presentation.extensions.getCallbackScreenModel
import io.github.vrcmteam.vrcm.presentation.extensions.getInsetPadding
import io.github.vrcmteam.vrcm.presentation.extensions.openUrl
import io.github.vrcmteam.vrcm.presentation.screens.auth.AuthAnimeScreen
import io.github.vrcmteam.vrcm.presentation.screens.profile.data.UserProfileVO
import io.github.vrcmteam.vrcm.presentation.supports.LanguageIcon
import io.github.vrcmteam.vrcm.presentation.supports.WebIcons
import io.github.vrcmteam.vrcm.presentation.theme.GameColor
import kotlinx.coroutines.launch

data class UserProfileScreen(
    private val userProfileVO: UserProfileVO
) : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val currentNavigator = currentNavigator
        val userProfileScreenModel: UserProfileScreenModel = getCallbackScreenModel(
            createFailureCallbackDoNavigation { AuthAnimeScreen(false) }
        )
        LifecycleEffect(
            onStarted = { userProfileScreenModel.initUserState(userProfileVO) }
        )
        LaunchedEffect(Unit) {
            userProfileScreenModel.refreshUser(userProfileVO.id)
        }
        val currentUser = userProfileScreenModel.userState
        var openAlertDialog by remember { mutableStateOf(false) }
        var bottomSheetIsVisible by remember { mutableStateOf(false) }
        val sheetState = rememberModalBottomSheetState()
        val scope = rememberCoroutineScope()

        ProfileScaffold(
            profileImageUrl = currentUser?.profileImageUrl,
            iconUrl = currentUser?.iconUrl,
            onReturn = { currentNavigator.pop() },
            onMenu = { bottomSheetIsVisible = true },
        ) { ratio ->
            ProfileContent(currentUser, ratio)
        }

        MenuBottomSheet(
            isVisible = bottomSheetIsVisible,
            sheetState = sheetState,
            onDismissRequest = {  bottomSheetIsVisible = false }
        ){
            TextButton(
                modifier = Modifier.align(Alignment.CenterHorizontally),
                onClick = {
                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                        if (sheetState.isVisible) return@invokeOnCompletion
                        bottomSheetIsVisible = false
                        openAlertDialog = true
                    }
                }
            ) {
                Text(text = "Look JsonData")
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MenuBottomSheet(
    isVisible: Boolean,
    sheetState: SheetState,
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (isVisible) {
        val bottomInsetPadding = getInsetPadding(0, WindowInsets::getBottom)
        ModalBottomSheet(
            modifier = Modifier.offset(y = bottomInsetPadding),
            onDismissRequest = onDismissRequest,
            sheetState = sheetState
        ) {
            content()
            Spacer(modifier = Modifier.height(bottomInsetPadding))
        }
    }
}

@Composable
private fun ProfileContent(
    currentUser: UserProfileVO?,
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
    userProfileVO: UserProfileVO
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
private inline fun LangAndLinkRow(userProfileVO: UserProfileVO) {
    val speakLanguages = userProfileVO.speakLanguages
    val bioLinks = userProfileVO.bioLinks
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
