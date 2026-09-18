package io.github.vrcmteam.vrcm.presentation.compoments

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.network.api.attributes.BlueprintType
import io.github.vrcmteam.vrcm.network.api.attributes.IUser
import io.github.vrcmteam.vrcm.network.api.invite.InviteApi
import io.github.vrcmteam.vrcm.presentation.designsystem.*
import io.github.vrcmteam.vrcm.presentation.extensions.currentNavigator
import io.github.vrcmteam.vrcm.presentation.extensions.glideBack
import io.github.vrcmteam.vrcm.presentation.screens.home.data.FriendLocation
import io.github.vrcmteam.vrcm.presentation.screens.group.GroupProfileScreen
import io.github.vrcmteam.vrcm.presentation.screens.group.data.GroupProfileVo
import io.github.vrcmteam.vrcm.presentation.screens.user.UserProfileScreen
import io.github.vrcmteam.vrcm.presentation.screens.user.data.UserProfileVo
import io.github.vrcmteam.vrcm.presentation.screens.world.WorldProfileScreen
import io.github.vrcmteam.vrcm.presentation.screens.world.data.WorldProfileVo
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings
import io.github.vrcmteam.vrcm.service.AuthService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

val DialogShapeForSharedElement = AppShapes.l

class LocationDialog(
    private val friendLocation: FriendLocation,
    private val sharedSuffixKey: String,
    private val onConfirmClick: () -> Unit,
) : SharedDialog {
    @OptIn(ExperimentalLayoutApi::class)
    @ExperimentalSharedTransitionApi
    @Composable
    override fun Content(
        animatedVisibilityScope: AnimatedVisibilityScope,
    ) {
        val currentInstants by friendLocation.instants
        // remember一下防止owner被刷新为null
        val owner = remember { currentInstants.owner }
        val currentNavigator = currentNavigator
        val onClickWorldImage = {
            // 创建临时的 WorldProfileVo
            val tempWorldProfileVo = WorldProfileVo(currentInstants)
            currentNavigator push WorldProfileScreen(
                worldProfileVO = tempWorldProfileVo,
                sharedSuffixKey = sharedSuffixKey,
                sharedImageCacheKey = currentInstants.worldImageUrl,
            )
        }
        val onClickUserIcon = { user: IUser ->
            currentNavigator push UserProfileScreen(
                userProfileVO = UserProfileVo(user),
                sharedSuffixKey = sharedSuffixKey
            )
        }
        val inviteApi: InviteApi = koinInject()
        val authService: AuthService = koinInject()
        var isInvited by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()
        val localeStrings = strings
        val onClickInvite = {
            scope.launch(Dispatchers.IO) {
                authService.reTryAuthCatching { inviteApi.inviteMyselfToInstance(friendLocation.location) }.onSuccess {
                    isInvited = true
                }
            }
        }
        CompositionLocalProvider(
            LocalSharedSuffixKey provides sharedSuffixKey,
        ) {
            SharedDialogContainer(
                key = friendLocation.location,
                animatedVisibilityScope = animatedVisibilityScope,
            ) {
                Column(
                    modifier = Modifier
                        .glideBack { close() }
                        .padding(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .sharedElementBy(
                                key = friendLocation.location + "WorldImage",
                                sharedTransitionScope = LocalSharedTransitionDialogScope.current,
                                animatedVisibilityScope = animatedVisibilityScope,
                            )
                            .clip(AppShapes.m)
                    ) {
                        // 添加世界详情页跳转功能
                        AImage(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .clickable { onClickWorldImage() }
                                .sharedBoundsBy( currentInstants.worldId + "WorldImage")
                                .clip(AppShapes.m),
                            imageData = friendLocation.instants.value.worldImageUrl,
                            contentDescription = "WorldImage"
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .background(
                                    AppTheme.colors.secondaryGroupedBackground.copy(alpha = 0.8f),
                                )
                                .padding(3.dp)
                        ) {
                            AppText(
                                modifier = Modifier.align(Alignment.Center),
                                text = currentInstants.worldName,
                                fontWeight = FontWeight.SemiBold,
                                style = AppTheme.type.subheadlineEmphasized,
                                color = AppTheme.colors.tint,
                                maxLines = 1
                            )
                        }
                    }
                    AppSurface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = AppShapes.m,
                    ) {

                        Column(
                            modifier = Modifier.padding(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (owner == null) return@Row
                                AppText(
                                    text = "${localeStrings.locationDialogOwner}:",
                                    fontWeight = FontWeight.Medium,
                                    style = AppTheme.type.subheadlineEmphasized,
                                )
                                AppIcon(modifier = Modifier.size(16.dp), imageVector = owner.iconVector, contentDescription = "OwnerIcon")
                                AppText(
                                    modifier = if (owner.type == BlueprintType.User || owner.type == BlueprintType.Group)
                                        Modifier.clickable {
                                            if (owner.type == BlueprintType.User) {
                                                onClickUserIcon(
                                                    UserProfileVo(id = owner.id, displayName = owner.displayName)
                                                )
                                            } else {
                                                currentNavigator push GroupProfileScreen(
                                                    groupProfileVo = GroupProfileVo(groupId = owner.id, name = owner.displayName),
                                                    sharedSuffixKey = sharedSuffixKey
                                                )
                                            }
                                        }
                                    else Modifier,
                                    textDecoration = if (owner.type == BlueprintType.User || owner.type == BlueprintType.Group) TextDecoration.Underline else null,
                                    text = owner.displayName,
                                    style = AppTheme.type.subheadline,
                                    color = AppTheme.colors.tertiaryLabel,
                                )
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                AppText(
                                    text = "${localeStrings.locationDialogAuthor}:",
                                    fontWeight = FontWeight.Medium,
                                    style = AppTheme.type.subheadlineEmphasized,
                                )
                                AppText(
                                    modifier = Modifier.clickable { onClickUserIcon(UserProfileVo(currentInstants.worldAuthorId)) },
                                    textDecoration = TextDecoration.Underline,
                                    text = currentInstants.worldAuthorName,
                                    color = AppTheme.colors.tertiaryLabel,
                                    style = AppTheme.type.subheadline,
                                )
                            }
                            AppText(
                                text = "${localeStrings.locationDialogDescription}:",
                                fontWeight = FontWeight.Medium,
                                style = AppTheme.type.subheadlineEmphasized,
                            )
                            SelectionContainer {
                                AppText(
                                    modifier = Modifier.heightIn(max = 80.dp).verticalScroll(rememberScrollState()),
                                    text = currentInstants.worldDescription,
                                    color = AppTheme.colors.tertiaryLabel,
                                    style = AppTheme.type.caption1,
                                )
                            }
                            if (currentInstants.worldAuthorTag.isNotEmpty()) {
                                AppText(
                                    text = "${localeStrings.locationDialogTags}:",
                                    fontWeight = FontWeight.Medium,
                                    style = AppTheme.type.subheadlineEmphasized,
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    currentInstants.worldAuthorTag.forEach { tag ->
                                        TextLabel(
                                            text = tag,
                                            backgroundColor = AppTheme.colors.fill,
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            UserIconsRow(friends = friendLocation.friendList) { user, transitionSuffixKey ->
                                currentNavigator push UserProfileScreen(
                                    userProfileVO = UserProfileVo(user),
                                    sharedSuffixKey = transitionSuffixKey,
                                )
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Spacer(modifier = Modifier.weight(1f))
                                RegionIcon(
                                    region = currentInstants.region
                                )
                                AppText(
                                    text = "${currentInstants.accessType}(${currentInstants.name})",
                                    style = AppTheme.type.subheadlineEmphasized,
                                    color = AppTheme.colors.tertiaryLabel
                                )
                                TextLabel(
                                    text = currentInstants.userCount,
                                )
                                AppButton(
                                    modifier = Modifier.animateContentSize(),
                                    enabled = !isInvited,
                                    onClick = { onClickInvite() },
                                    style = AppButtonStyle.Prominent,
                                ) {
                                    AppText(text = if (isInvited) localeStrings.locationInvited else localeStrings.locationInviteMe)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun close(): Unit = onConfirmClick()
}
