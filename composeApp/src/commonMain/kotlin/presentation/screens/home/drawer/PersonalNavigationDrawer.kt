package io.github.vrcmteam.vrcm.presentation.screens.home.drawer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.network.api.attributes.UserStatus
import io.github.vrcmteam.vrcm.presentation.animations.NoClip
import io.github.vrcmteam.vrcm.presentation.animations.TextBoundsTransform
import io.github.vrcmteam.vrcm.presentation.compoments.AImage
import io.github.vrcmteam.vrcm.presentation.compoments.LocalSharedTransitionDialogScope
import io.github.vrcmteam.vrcm.presentation.compoments.SharedTextBoundsResizeMode
import io.github.vrcmteam.vrcm.presentation.compoments.UserStateIcon
import io.github.vrcmteam.vrcm.presentation.compoments.UserStatusIndicator
import io.github.vrcmteam.vrcm.presentation.compoments.VrcPlusIcon
import io.github.vrcmteam.vrcm.presentation.compoments.sharedBoundsBy
import io.github.vrcmteam.vrcm.presentation.designsystem.*
import io.github.vrcmteam.vrcm.presentation.extensions.enableIf
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings
import io.github.vrcmteam.vrcm.presentation.supports.AppIcons

data class PersonalDrawerUser(
    val id: String,
    val avatarUrl: String?,
    val customBannerUrl: String?,
    val displayName: String,
    val pronouns: String?,
    val isSupporter: Boolean,
    val status: UserStatus,
    val statusDescription: String,
    val location: String,
)

/** 资料卡片顶部横幅的高度；头像压在它的下沿上。 */
private val ProfileBannerHeight = 80.dp
private val ProfileAvatarSize = 64.dp
/** 头像外圈的卡片色描边：把头像从横幅上"切"出来。 */
private val ProfileAvatarRing = 3.dp

/** Personal navigation drawer shell. Services and navigation remain owned by its caller. */
@Composable
fun PersonalNavigationDrawer(
    drawerState: AppDrawerState,
    gesturesEnabled: Boolean,
    user: PersonalDrawerUser?,
    profileSharedSuffixKey: String,
    statusVisible: Boolean,
    onProfileClick: () -> Unit,
    onStatusClick: () -> Unit,
    onFriendNetworkClick: () -> Unit,
    onGalleryClick: () -> Unit,
    onInviteMessagesClick: () -> Unit,
    onPlayerManagementClick: () -> Unit,
    onRecentWorldsClick: () -> Unit,
    onInventoryClick: () -> Unit,
    onNameplateClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onLogoutClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val drawerDescription = strings.personalDrawerTitle
    AppModalDrawer(
        drawerState = drawerState,
        gesturesEnabled = gesturesEnabled,
        drawerContent = {
            // 抽屉是盖在页面上的一层：深色下用抬升令牌，分组卡片才和抽屉底色分得开。
            val colors = AppTheme.colors.elevated()
            CompositionLocalProvider(LocalAppColors provides colors) {
                AppDrawerSheet(
                    modifier = Modifier
                        .fillMaxWidth(.82f)
                        .widthIn(max = 360.dp)
                        .semantics { contentDescription = drawerDescription },
                    containerColor = colors.groupedBackground,
                    windowInsets = WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Vertical + WindowInsetsSides.Start,
                    ),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = AppSpacing.page, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        ProfileCard(
                            user = user,
                            sharedSuffixKey = profileSharedSuffixKey,
                            sharedElementsEnabled = gesturesEnabled,
                            statusVisible = statusVisible,
                            onProfileClick = onProfileClick,
                            onStatusClick = onStatusClick,
                        )
                        // 社交
                        AppGroup {
                            DrawerRow(AppIcons.Person, AppRowIconColor.Blue, strings.drawerMyProfile, onProfileClick, enabled = user != null)
                            DrawerRowDivider()
                            DrawerRow(AppIcons.PersonSearch, AppRowIconColor.Indigo, strings.friendNetworkTitle, onFriendNetworkClick)
                            DrawerRowDivider()
                            DrawerRow(AppIcons.Envelope, AppRowIconColor.Green, strings.inviteMessageSlotsTitle, onInviteMessagesClick)
                            DrawerRowDivider()
                            DrawerRow(AppIcons.Shield, AppRowIconColor.Red, strings.playerModerationTitle, onPlayerManagementClick)
                        }
                        // 内容
                        AppGroup {
                            DrawerRow(AppIcons.Gallery, AppRowIconColor.Orange, strings.galleryScreenTitle, onGalleryClick)
                            DrawerRowDivider()
                            DrawerRow(AppIcons.Explore, AppRowIconColor.Teal, strings.recentWorldsTitle, onRecentWorldsClick)
                            DrawerRowDivider()
                            DrawerRow(AppIcons.Inventory, AppRowIconColor.Purple, strings.inventoryTitle, onInventoryClick)
                            DrawerRowDivider()
                            DrawerRow(AppIcons.AccountCircle, AppRowIconColor.Pink, strings.meetupCardTitle, onNameplateClick)
                        }
                        AppGroup {
                            DrawerRow(AppIcons.Settings, AppRowIconColor.Gray, strings.drawerSettings, onSettingsClick)
                        }
                        AppGroup {
                            LogoutRow(onLogoutClick)
                        }
                    }
                }
            }
        },
        content = content,
    )
}

/**
 * 资料卡片：横幅 + 压在横幅下沿的头像 + 名字，整块点进个人资料；下面一行是当前状态，点开状态编辑。
 * 没有自定义横幅时用主题的浅强调色垫一条，卡片结构保持一致。
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun ProfileCard(
    user: PersonalDrawerUser?,
    sharedSuffixKey: String,
    sharedElementsEnabled: Boolean,
    statusVisible: Boolean,
    onProfileClick: () -> Unit,
    onStatusClick: () -> Unit,
) {
    val loaded = user != null
    val userId = user?.id
    val cardColor = AppTheme.colors.secondaryGroupedBackground
    AppGroup(background = cardColor) {
        Column(
            Modifier
                .fillMaxWidth()
                .clickable(enabled = loaded, onClick = onProfileClick),
        ) {
            Box(Modifier.fillMaxWidth()) {
                val bannerModifier = Modifier.fillMaxWidth().height(ProfileBannerHeight)
                val bannerUrl = user?.customBannerUrl
                if (bannerUrl != null) {
                    AImage(
                        modifier = bannerModifier,
                        imageData = bannerUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Box(bannerModifier.background(AppTheme.colors.tintSoft))
                }
                Box(
                    Modifier
                        .padding(
                            start = AppSpacing.row - ProfileAvatarRing,
                            top = ProfileBannerHeight - ProfileAvatarSize / 2 - ProfileAvatarRing,
                        )
                        .background(cardColor, CircleShape)
                        .padding(ProfileAvatarRing),
                ) {
                    UserStateIcon(
                        modifier = Modifier
                            .enableIf(loaded && sharedElementsEnabled) {
                                sharedBoundsBy(
                                    key = "${userId}UserIcon",
                                    suffixKey = sharedSuffixKey,
                                )
                            }
                            .size(ProfileAvatarSize),
                        iconUrl = user?.avatarUrl,
                    )
                }
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppSpacing.row)
                    .padding(top = 6.dp, bottom = 12.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    AppText(
                        text = user?.displayName ?: strings.loading,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .enableIf(loaded && sharedElementsEnabled) {
                                sharedBoundsBy(
                                    key = "${userId}UserName",
                                    suffixKey = sharedSuffixKey,
                                    resizeMode = SharedTextBoundsResizeMode,
                                    boundsTransform = TextBoundsTransform,
                                    clipInOverlayDuringTransition = NoClip,
                                )
                            },
                        style = AppTheme.type.title3,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (user?.isSupporter == true) {
                        VrcPlusIcon(Modifier.size(16.dp))
                    }
                }
                user?.pronouns?.takeIf { it.isNotBlank() }?.let { pronouns ->
                    AppText(
                        pronouns,
                        style = AppTheme.type.subheadline,
                        color = AppTheme.colors.secondaryLabel,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        AnimatedVisibility(visible = statusVisible) {
            val visibilityScope = this
            val dialogSharedUserId = userId?.let(::drawerStatusSharedUserId).orEmpty()
            Column {
                AppDivider(Modifier.padding(start = AppSpacing.row))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(enabled = loaded, onClick = onStatusClick)
                        .defaultMinSize(minHeight = AppSize.rowMinHeight)
                        .padding(horizontal = AppSpacing.row, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    UserStatusIndicator(
                        modifier = Modifier
                            .size(10.dp)
                            .enableIf(loaded && sharedElementsEnabled) {
                                sharedBoundsBy(
                                    key = "${dialogSharedUserId}UserStatusIcon",
                                    sharedTransitionScope = LocalSharedTransitionDialogScope.current,
                                    animatedVisibilityScope = visibilityScope,
                                )
                            },
                        userStatus = user?.status,
                        location = user?.location,
                        backgroundColor = cardColor,
                    )
                    AppText(
                        text = user?.statusDescription.orEmpty().ifBlank {
                            user?.status?.localizedLabel() ?: strings.loading
                        },
                        modifier = Modifier
                            .enableIf(loaded && sharedElementsEnabled) {
                                sharedBoundsBy(
                                    key = "${userId}UserStatusRow",
                                    suffixKey = sharedSuffixKey,
                                    resizeMode = SharedTextBoundsResizeMode,
                                    boundsTransform = TextBoundsTransform,
                                    clipInOverlayDuringTransition = NoClip,
                                )
                            }
                            .enableIf(loaded && sharedElementsEnabled) {
                                sharedBoundsBy(
                                    key = "${dialogSharedUserId}UserStatusText",
                                    sharedTransitionScope = LocalSharedTransitionDialogScope.current,
                                    animatedVisibilityScope = visibilityScope,
                                )
                            }
                            .weight(1f),
                        style = AppTheme.type.subheadline,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    AppIcon(
                        AppChevronRight,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = AppTheme.colors.tertiaryLabel,
                    )
                }
            }
        }
    }
}

/** 抽屉里的导航行：彩色图标方块 + 标题 + chevron。 */
@Composable
private fun DrawerRow(
    icon: ImageVector,
    iconColor: Color,
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    AppRow(
        title = text,
        leading = { AppRowIcon(icon, iconColor, Modifier.alpha(if (enabled) 1f else .38f)) },
        chevron = true,
        enabled = enabled,
        onClick = onClick,
    )
}

@Composable
private fun DrawerRowDivider() {
    AppDivider(Modifier.padding(start = AppRowIconDividerInset))
}

/** 破坏性操作独占一组、红字居中（与设置页的退出登录一致）。 */
@Composable
private fun LogoutRow(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .defaultMinSize(minHeight = AppSize.rowMinHeight)
            .padding(horizontal = AppSpacing.row, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        AppText(
            text = strings.stettingLogout,
            style = AppTheme.type.body,
            color = AppTheme.colors.destructive,
        )
    }
}

@Composable
private fun UserStatus.localizedLabel(): String = when (this) {
    UserStatus.Active -> strings.editProfileStatusOnline
    UserStatus.JoinMe -> strings.editProfileStatusJoinMe
    UserStatus.AskMe -> strings.editProfileStatusAskMe
    UserStatus.Busy -> strings.editProfileStatusBusy
    UserStatus.Offline -> strings.friendDirectoryOffline
}

internal fun drawerStatusSharedUserId(userId: String): String = "${userId}Drawer"
