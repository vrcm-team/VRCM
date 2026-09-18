package io.github.vrcmteam.vrcm.presentation.screens.world.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.network.api.attributes.BlueprintType
import io.github.vrcmteam.vrcm.network.api.attributes.IUser
import io.github.vrcmteam.vrcm.network.api.invite.InviteApi
import io.github.vrcmteam.vrcm.presentation.compoments.IconLabelRow
import io.github.vrcmteam.vrcm.presentation.compoments.LocalSharedSuffixKey
import io.github.vrcmteam.vrcm.presentation.compoments.RegionIcon
import io.github.vrcmteam.vrcm.presentation.designsystem.*
import io.github.vrcmteam.vrcm.presentation.extensions.currentNavigator
import io.github.vrcmteam.vrcm.presentation.extensions.enableIf
import io.github.vrcmteam.vrcm.presentation.screens.group.GroupProfileScreen
import io.github.vrcmteam.vrcm.presentation.screens.group.data.GroupProfileVo
import io.github.vrcmteam.vrcm.presentation.screens.user.UserProfileScreen
import io.github.vrcmteam.vrcm.presentation.screens.user.data.UserProfileVo
import io.github.vrcmteam.vrcm.presentation.screens.world.data.InstanceVo
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings
import io.github.vrcmteam.vrcm.presentation.supports.AppIcons
import io.github.vrcmteam.vrcm.service.AuthService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import org.koin.compose.koinInject


/** 面板里的房间卡片与空状态卡片共用的圆角。 */
private val InstanceCardShape = AppShapes.l

@Composable
fun EmptyInstanceCard(
    onCreateInstance: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val localeStrings = strings
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp)
            .clip(InstanceCardShape)
            .background(AppTheme.colors.tertiaryGroupedBackground)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onCreateInstance,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            AppSurface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = AppTheme.colors.tintSoft,
            ) {
                AppIcon(
                    imageVector = AppIcons.Add,
                    contentDescription = null,
                    modifier = Modifier.padding(9.dp),
                    tint = AppTheme.colors.tint,
                )
            }
            AppText(
                text = localeStrings.worldProfileCreateFirstInstance,
                style = AppTheme.type.subheadlineEmphasized,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            AppText(
                text = localeStrings.worldProfileCreateFirstInstanceHint,
                style = AppTheme.type.caption1,
                color = AppTheme.colors.secondaryLabel,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}


@Composable
fun InstanceCard(
    instance: InstanceVo,
    size: Int,
    index: Int,
    unfold: Boolean = false,
    verticalOffset: Dp = ((size - index) * 8).dp, // 新增参数，控制垂直偏移
    scaleEffect: Float = 1f - ((size - index) * 0.05f), // 新增参数，控制缩放
    alphaEffect: Float = 1f - ((size - index) * 0.6f), // 新增参数，控制透明度
    shape: Shape = InstanceCardShape,
    expandProgress: Float = 1f,
    onClick: (() -> Unit)? = null,
) {
    val animatedScale by animateFloatAsState(
        targetValue = if (unfold) 1f else scaleEffect,
        label = "scale"
    )

    val animatedOffset by animateDpAsState(
        targetValue = if (unfold) 0.dp else verticalOffset,
        label = "offset"
    )
    val modifier = Modifier.fillMaxWidth().height(120.dp)
        .enableIf(!unfold) {
            offset(y = animatedOffset)
                .graphicsLayer(
                    alpha = alphaEffect,
                    clip = false,
                    shape = shape,
                    scaleX = animatedScale,
                )
        }
        .clip(InstanceCardShape)
        .enableIf(onClick != null) {
            clickable { onClick?.invoke() }
        }

    AppSurface(
        modifier = modifier,
        shape = InstanceCardShape,
        color = AppTheme.colors.tertiaryGroupedBackground,
        // 堆叠时前后卡片同色，靠发丝线分出层次
        border = BorderStroke(0.5.dp, AppTheme.colors.separator),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            InstanceCardContent(instance)
            // 底部状态区域
            BottomActionSection(instance = instance, expandProgress = expandProgress)
        }
    }
}

@Composable
fun InstanceCardContent(instance: InstanceVo) {
    // 提取常用尺寸常量
    val iconSize = 16.dp
    val smallIconSize = 14.dp
    val elementSpacing = 4.dp

    // 主内容区域
    Row(
        modifier = Modifier
            .fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // 左侧信息区域（占剩余空间）
        InstanceInfoSection(
            instance = instance,
            iconSize = iconSize,
            elementSpacing = elementSpacing
        )

        // 右侧用户统计（固定最小宽度）
        UserStatsSection(
            instance = instance,
            iconSize = iconSize,
            smallIconSize = smallIconSize,
            elementSpacing = elementSpacing
        )
    }


}

@Composable
private fun RowScope.InstanceInfoSection(
    instance: InstanceVo,
    iconSize: Dp,
    elementSpacing: Dp,
) {
    Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(elementSpacing)
    ) {
        // 实例名称（带溢出处理）
        AppText(
            text = "#${instance.instanceName}",
            style = AppTheme.type.headline,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        // 区域信息
        IconLabelRow(
            iconScope = {
                RegionIcon(
                    size = iconSize,
                    region = instance.regionType
                )
            },
            text = instance.accessType.displayName,
            spacing = elementSpacing
        )
    }
}

@Composable
private fun UserStatsSection(
    instance: InstanceVo,
    iconSize: Dp,
    smallIconSize: Dp,
    elementSpacing: Dp,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(elementSpacing),
        horizontalAlignment = Alignment.End
    ) {
        // 总用户数
        IconLabelRow(
            icon = AppIcons.Group,
            text = "${instance.currentUsers ?: 0}",
            iconSize = iconSize,
            spacing = elementSpacing,
            textStyle = AppTheme.type.subheadlineEmphasized
        )

        // 设备统计（复用组件）
        DeviceStatsRow(
            pcUsers = instance.pcUsers,
            androidUsers = instance.androidUsers,
            iosUsers = instance.iosUsers,
            iconSize = smallIconSize,
            spacing = elementSpacing
        )
    }
}

@Composable
private fun DeviceStatsRow(
    pcUsers: Int?,
    androidUsers: Int?,
    iconSize: Dp,
    spacing: Dp,
    iosUsers: Int?,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(spacing * 2),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (pcUsers != null && pcUsers > 0) {
            IconLabelRow(
                icon = AppIcons.Windows,
                text = "$pcUsers",
                iconSize = iconSize,
                spacing = spacing
            )
        }
        if (androidUsers != null && androidUsers > 0) {
            IconLabelRow(
                icon = AppIcons.Android,
                text = "$androidUsers",
                iconSize = iconSize,
                spacing = spacing
            )
        }
        if (iosUsers != null && iosUsers > 0) {
            IconLabelRow(
                icon = AppIcons.Apple,
                text = "$iosUsers",
                iconSize = iconSize,
                spacing = spacing
            )
        }
    }
}

@Composable
private fun BottomActionSection(instance: InstanceVo, expandProgress: Float = 1f) {
    val currentNavigator = currentNavigator
    val sharedSuffixKey = LocalSharedSuffixKey.current
    val onClickUserIcon = { user: IUser ->
        currentNavigator push UserProfileScreen(
            userProfileVO = UserProfileVo(user)
        )
    }
    val onClickGroup = { groupId: String, groupName: String ->
        currentNavigator push GroupProfileScreen(
            groupProfileVo = GroupProfileVo(groupId = groupId, name = groupName),
            sharedSuffixKey = sharedSuffixKey
        )
    }
    val isExtended = expandProgress == 1f
    val inviteApi: InviteApi = koinInject()
    val authService: AuthService = koinInject()
    var isInvited by remember(instance.id) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val localeStrings = strings
    val onClickInvite = {
        scope.launch(Dispatchers.IO) {
            authService.reTryAuthCatching { inviteApi.inviteMyselfToInstance(instance.id) }.onSuccess {
                isInvited = true
            }
        }
    }
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Row(
            modifier = Modifier.align(Alignment.CenterStart),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val owner = instance.owner.collectAsState().value ?: return@Row
            AppIcon(
                modifier = Modifier.size(16.dp),
                imageVector = owner.iconVector,
                tint = AppTheme.colors.tint,
                contentDescription = "OwnerIcon"
            )
            AppText(
                modifier = Modifier.enableIf(isExtended) {
                    clickable {
                        if (owner.type == BlueprintType.User) {
                            onClickUserIcon(
                                UserProfileVo(id = owner.id, displayName = owner.displayName)
                            )
                        } else if (owner.type == BlueprintType.Group) {
                            onClickGroup(owner.id, owner.displayName)
                        }
                    }
                },
                text = owner.displayName,
                style = AppTheme.type.subheadline,
                color = if (isExtended && (owner.type == BlueprintType.User || owner.type == BlueprintType.Group)) {
                    AppTheme.colors.tint
                } else {
                    AppTheme.colors.secondaryLabel
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        val enabled = !isInvited && isExtended && instance.isActive != false && instance.hasCapacity != false
        if (expandProgress == 0f) return@Box
        AppButton(
            modifier = Modifier.align(Alignment.CenterEnd).alpha(expandProgress),
            enabled = enabled,
            onClick = { onClickInvite() },
            style = AppButtonStyle.Tinted,
            size = AppButtonSize.Small,
        ) {
            AppText(text = if (isInvited) localeStrings.locationInvited else localeStrings.locationInviteMe)
        }
    }


//    FlowRow(
//        modifier = Modifier
//            .fillMaxWidth()
//            .padding(horizontal = cardPadding, vertical = 8.dp),
//        horizontalArrangement = Arrangement.spacedBy(8.dp),
//        verticalArrangement = Arrangement.Center
//    ) {
//        // 状态标签
//        if (instance.isActive == true){
//            IconTextChip(
//                text = "活跃",
//                icon = AppIcons.Check,
//            )
//        }
//        if(instance.queueEnabled == true && instance.queueSize != null && instance.queueSize > 0){
//            IconTextChip(
//                text = "队列: ${instance.queueSize}",
//                icon = AppIcons.Duplicate,
//            )
//        }
//
//        if (instance.isFull == true){
//            IconTextChip(
//                text = "已满",
//                icon = AppIcons.Block,
//            )
//        }
//
//        if (instance.hasCapacity == true){
//            IconTextChip(
//                text = "可加入",
//                icon = AppIcons.Logout,
//            )
//        }
//
//        // 弹性空间
//        Spacer(Modifier.weight(1f))
//        // 所有者信息
//        instance.owner.collectAsState().value?.let { owner ->
//            IconLabelRow(
//                icon = owner.iconVector,
//                text = owner.displayName,
//                iconSize = 14.dp,
//                spacing = 4.dp,
//                textColor = AppTheme.colors.label.copy(alpha = 0.7f),
//                iconAlpha = 0.7f
//            )
//        }
//    }
}
