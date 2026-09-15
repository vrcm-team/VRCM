package io.github.vrcmteam.vrcm.presentation.screens.home.drawer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.network.api.attributes.UserStatus
import io.github.vrcmteam.vrcm.presentation.animations.NoClip
import io.github.vrcmteam.vrcm.presentation.animations.TextBoundsTransform
import io.github.vrcmteam.vrcm.presentation.compoments.LocalSharedTransitionDialogScope
import io.github.vrcmteam.vrcm.presentation.compoments.SharedTextBoundsResizeMode
import io.github.vrcmteam.vrcm.presentation.compoments.UserStateIcon
import io.github.vrcmteam.vrcm.presentation.compoments.UserStatusIndicator
import io.github.vrcmteam.vrcm.presentation.compoments.VrcPlusIcon
import io.github.vrcmteam.vrcm.presentation.compoments.sharedBoundsBy
import io.github.vrcmteam.vrcm.presentation.extensions.enableIf
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings
import io.github.vrcmteam.vrcm.presentation.supports.AppIcons

data class PersonalDrawerUser(
    val id: String,
    val avatarUrl: String?,
    val displayName: String,
    val pronouns: String?,
    val isSupporter: Boolean,
    val status: UserStatus,
    val statusDescription: String,
    val location: String,
)

/** Personal navigation drawer shell. Services and navigation remain owned by its caller. */
@Composable
fun PersonalNavigationDrawer(
    drawerState: DrawerState,
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
    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = gesturesEnabled,
        scrimColor = DrawerDefaults.scrimColor,
        drawerContent = {
            ModalDrawerSheet(
                drawerState = drawerState,
                modifier = Modifier
                    .fillMaxWidth(.82f)
                    .widthIn(max = 360.dp)
                    .semantics { contentDescription = drawerDescription },
                windowInsets = WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Vertical + WindowInsetsSides.Start,
                ),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 12.dp),
                ) {
                    PersonalHeader(
                        user = user,
                        sharedSuffixKey = profileSharedSuffixKey,
                        sharedElementsEnabled = gesturesEnabled,
                        statusVisible = statusVisible,
                        onProfileClick = onProfileClick,
                        onStatusClick = onStatusClick,
                    )
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    DrawerItem(
                        AppIcons.Person,
                        strings.drawerMyProfile,
                        onProfileClick,
                        enabled = user != null,
                    )
                    DrawerItem(AppIcons.PersonSearch, strings.friendNetworkTitle, onFriendNetworkClick)
                    DrawerItem(AppIcons.Gallery, strings.galleryScreenTitle, onGalleryClick)
                    DrawerItem(AppIcons.Notifications, strings.inviteMessageSlotsTitle, onInviteMessagesClick)
                    DrawerItem(AppIcons.Shield, strings.playerModerationTitle, onPlayerManagementClick)
                    DrawerItem(AppIcons.Explore, strings.recentWorldsTitle, onRecentWorldsClick)
                    DrawerItem(AppIcons.Inventory, strings.inventoryTitle, onInventoryClick)
                    DrawerItem(AppIcons.AccountCircle, strings.meetupCardTitle, onNameplateClick)
                    DrawerItem(AppIcons.Settings, strings.drawerSettings, onSettingsClick)
                    Spacer(Modifier.height(20.dp))
                    HorizontalDivider()
                    DrawerItem(AppIcons.Login, strings.stettingLogout, onLogoutClick, error = true)
                }
            }
        },
        content = content,
    )
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun PersonalHeader(
    user: PersonalDrawerUser?,
    sharedSuffixKey: String,
    sharedElementsEnabled: Boolean,
    statusVisible: Boolean,
    onProfileClick: () -> Unit,
    onStatusClick: () -> Unit,
) {
    val loaded = user != null
    val userId = user?.id
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            UserStateIcon(
                modifier = Modifier
                    .enableIf(loaded && sharedElementsEnabled) {
                        sharedBoundsBy(
                            key = "${userId}UserIcon",
                            suffixKey = sharedSuffixKey,
                        )
                    }
                    .size(64.dp)
                    .clip(CircleShape)
                    .clickable(enabled = loaded, onClick = onProfileClick),
                iconUrl = user?.avatarUrl,
            )
            Column(Modifier.weight(1f).clickable(enabled = loaded, onClick = onProfileClick)) {
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
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
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (user?.isSupporter == true) {
                        VrcPlusIcon(Modifier.size(16.dp))
                    }
                }
                user?.pronouns?.takeIf { it.isNotBlank() }?.let { pronouns ->
                    Text(
                        pronouns,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        AnimatedVisibility(visible = statusVisible) {
            val visibilityScope = this
            val dialogSharedUserId = userId?.let(::drawerStatusSharedUserId).orEmpty()
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .clickable(enabled = loaded, onClick = onStatusClick)
                    .padding(vertical = 8.dp),
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
                    backgroundColor = MaterialTheme.colorScheme.surfaceContainerLow,
                )
                Text(
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
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun DrawerItem(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
    error: Boolean = false,
    enabled: Boolean = true,
) {
    val baseColor = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    val color = baseColor.copy(alpha = if (enabled) 1f else .38f)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
        Text(
            text,
            modifier = Modifier.weight(1f),
            color = color,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
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
