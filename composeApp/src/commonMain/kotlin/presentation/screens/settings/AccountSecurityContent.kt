package io.github.vrcmteam.vrcm.presentation.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.presentation.compoments.UserStateIcon
import io.github.vrcmteam.vrcm.presentation.designsystem.AppIcon
import io.github.vrcmteam.vrcm.presentation.designsystem.AppRow
import io.github.vrcmteam.vrcm.presentation.designsystem.AppSectionFooter
import io.github.vrcmteam.vrcm.presentation.designsystem.AppTheme
import io.github.vrcmteam.vrcm.presentation.navigation.LocalNavigator
import io.github.vrcmteam.vrcm.presentation.navigation.currentOrThrow
import io.github.vrcmteam.vrcm.presentation.screens.auth.AccountLoginScreen
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings
import io.github.vrcmteam.vrcm.presentation.supports.AppIcons
import io.github.vrcmteam.vrcm.service.AuthService
import org.koin.compose.koinInject

@Composable
internal fun ColumnScope.AccountSecurityContent() {
    val authService = koinInject<AuthService>()
    val navigator = LocalNavigator.currentOrThrow
    val session by SharedFlowCentre.currentSession.collectAsState()
    val accounts = remember(session?.token) { authService.accountDtoList() }
        .sortedByDescending { it.userId == session?.account?.userId }

    Column {
        SettingsGroup(title = strings.settingsSavedAccounts) {
            accounts.forEachIndexed { index, account ->
                if (index > 0) SettingsRowDivider()
                val isCurrent = account.userId == session?.account?.userId
                AppRow(
                    title = account.username,
                    // 头像和选中标记保持固定几何，名称区域使用剩余宽度并允许换行。
                    leading = { UserStateIcon(Modifier.size(40.dp), iconUrl = account.iconUrl) },
                    value = if (isCurrent) strings.authCurrent else null,
                    trailing = if (isCurrent) {
                        { AppIcon(AppIcons.Check, contentDescription = null, tint = AppTheme.colors.tint) }
                    } else null,
                    chevron = !isCurrent,
                    onClick = if (isCurrent) null else {
                        { navigator replaceAll AccountLoginScreen(account.userId) }
                    },
                )
            }
            if (accounts.isNotEmpty()) SettingsRowDivider()
            AppRow(
                title = strings.settingsAddAccount,
                leading = { AppIcon(AppIcons.Add, contentDescription = null, tint = AppTheme.colors.tint) },
                titleColor = AppTheme.colors.tint,
                chevron = true,
                onClick = { navigator push AccountLoginScreen() },
            )
        }
        AppSectionFooter(if (accounts.isEmpty()) strings.settingsNoAccounts else strings.settingsAccountsHint)
    }
    LogoutButton()
}
