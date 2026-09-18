package io.github.vrcmteam.vrcm.presentation.compoments

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.presentation.designsystem.AppIcon
import io.github.vrcmteam.vrcm.presentation.designsystem.AppText
import io.github.vrcmteam.vrcm.presentation.designsystem.AppTheme
import io.github.vrcmteam.vrcm.presentation.supports.AppIcons

/**
 * 空内容状态组件
 * 
 * @param message 显示的消息文本
 * @param icon 显示的图标，默认为感叹号图标
 * @param actionContent 可选的操作按钮内容
 */
@Composable
fun EmptyContent(
    message: String,
    icon: ImageVector = AppIcons.QuestionMark,
    modifier: Modifier = Modifier,
    actionContent: @Composable (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AppIcon(
            painter = rememberVectorPainter(icon),
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = AppTheme.colors.secondaryLabel.copy(alpha = 0.6f)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        AppText(
            text = message,
            style = AppTheme.type.body,
            color = AppTheme.colors.secondaryLabel.copy(alpha = 0.8f),
            textAlign = TextAlign.Center
        )
        
        if (actionContent != null) {
            Spacer(modifier = Modifier.height(24.dp))
            actionContent()
        }
    }
} 