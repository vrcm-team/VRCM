package io.github.vrcmteam.vrcm.presentation.screens.world.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.presentation.compoments.*
import io.github.vrcmteam.vrcm.presentation.extensions.enableIf
import io.github.vrcmteam.vrcm.presentation.screens.world.data.InstanceVo
import io.github.vrcmteam.vrcm.presentation.supports.AppIcons


@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun AnimatedVisibilityScope.InstanceCard(
    instance: InstanceVo,
    size: Int,
    index: Int,
    unfold: Boolean = false,
    verticalOffset: Dp = ((size - index) * 8).dp, // 新增参数，控制垂直偏移
    scaleEffect: Float = 1f - ((size - index) * 0.05f), // 新增参数，控制缩放
    alphaEffect: Float = 1f - ((size - index) * 0.6f), // 新增参数，控制透明度
    shape: Shape = RoundedCornerShape(32.dp),
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
        .sharedBoundsBy(
            key = "${instance.instanceId}:StackCardsContainer",
            sharedTransitionScope = LocalSharedTransitionDialogScope.current,
            animatedVisibilityScope = this,
            clipInOverlayDuringTransition = with(LocalSharedTransitionDialogScope.current) {
                OverlayClip(DialogShapeForSharedElement)
            }
        )
        .enableIf(!unfold) {
            offset(y = animatedOffset)
                .graphicsLayer(
                    alpha = alphaEffect,
                    clip = false,
                    shape = shape,
                    scaleX = animatedScale,
                )
        }
        .clip(MaterialTheme.shapes.medium)
        .background(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium)
        .enableIf(onClick != null) {
            clickable { onClick?.invoke() }
        }

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 10.dp
    ) {
        Column {
            InstanceCardContent(instance)
        }
    }
}

@Composable
fun InstanceCardContent(instance: InstanceVo) {
    // 提取常用尺寸常量
    val iconSize = 16.dp
    val smallIconSize = 14.dp
    val elementSpacing = 4.dp
    val cardPadding = 12.dp

    // 主内容区域
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(cardPadding),
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

    // 底部状态区域
    StatusSection(instance = instance, cardPadding = cardPadding)
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
        Text(
            text = "#${instance.instanceName}",
            style = MaterialTheme.typography.titleMedium,
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
            text = instance.regionName,
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
            icon = AppIcons.Person,
            text = "${instance.currentUsers ?: 0} 用户",
            iconSize = iconSize,
            spacing = elementSpacing,
            textStyle = MaterialTheme.typography.titleSmall
        )

        // 设备统计（复用组件）
        DeviceStatsRow(
            pcUsers = instance.pcUsers,
            androidUsers = instance.androidUsers,
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
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(spacing * 2),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconLabelRow(
            icon = AppIcons.Computer,
            text = "${pcUsers ?: 0} PC",
            iconSize = iconSize,
            spacing = spacing
        )
        IconLabelRow(
            icon = AppIcons.Smartphone,
            text = "${androidUsers ?: 0} Quest",
            iconSize = iconSize,
            spacing = spacing
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatusSection(instance: InstanceVo, cardPadding: Dp) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = cardPadding, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.Center
    ) {
        // 状态标签
        if (instance.isActive == true){
            StatusChip(
                text = "活跃",
                icon = AppIcons.Check,
                color = MaterialTheme.colorScheme.tertiary
            )
        }
        if(instance.queueEnabled == true && instance.queueSize != null){
            StatusChip(
                text = "队列: ${instance.queueSize}",
                icon = AppIcons.Queue,
                color = MaterialTheme.colorScheme.secondary
            )
        }

        if (instance.isFull == true){
            StatusChip(
                text = "已满",
                icon = AppIcons.Block,
                color = MaterialTheme.colorScheme.error
            )
        }

        if (instance.hasCapacity == true){
            StatusChip(
                text = "可加入",
                icon = AppIcons.Login,
                color = MaterialTheme.colorScheme.tertiary
            )
        }

        // 弹性空间
        Spacer(Modifier.weight(1f))

        // 所有者信息
        instance.ownerName?.let { name ->
            IconLabelRow(
                icon = AppIcons.Person,
                text = name,
                iconSize = 14.dp,
                spacing = 4.dp,
                textColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                iconAlpha = 0.7f
            )
        }
    }
}

@Composable
private fun StatusChip(
    text: String,
    icon: ImageVector,
    color: Color,
) {
    Surface(
        color = color.copy(alpha = 0.2f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = color
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = color
            )
        }
    }
}


