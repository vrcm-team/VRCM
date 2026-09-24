package io.github.vrcmteam.vrcm.presentation.compoments

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.presentation.designsystem.AppActivityIndicator
import io.github.vrcmteam.vrcm.presentation.designsystem.AppButton
import io.github.vrcmteam.vrcm.presentation.designsystem.AppButtonRole
import io.github.vrcmteam.vrcm.presentation.designsystem.AppButtonStyle
import io.github.vrcmteam.vrcm.presentation.designsystem.AppDivider
import io.github.vrcmteam.vrcm.presentation.designsystem.AppGroup
import io.github.vrcmteam.vrcm.presentation.designsystem.AppIcon
import io.github.vrcmteam.vrcm.presentation.designsystem.AppRow
import io.github.vrcmteam.vrcm.presentation.designsystem.AppSectionFooter
import io.github.vrcmteam.vrcm.presentation.designsystem.AppSectionHeader
import io.github.vrcmteam.vrcm.presentation.designsystem.AppSheet
import io.github.vrcmteam.vrcm.presentation.designsystem.AppSpacing
import io.github.vrcmteam.vrcm.presentation.designsystem.AppText
import io.github.vrcmteam.vrcm.presentation.designsystem.AppTextField
import io.github.vrcmteam.vrcm.presentation.designsystem.AppTheme
import io.github.vrcmteam.vrcm.presentation.designsystem.LocalContentColor
import io.github.vrcmteam.vrcm.presentation.settings.locale.LocaleStrings
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings
import io.github.vrcmteam.vrcm.presentation.supports.AppIcons
import io.github.vrcmteam.vrcm.presentation.supports.rememberConsumeRemainingUpwardScrollConnection
import io.github.vrcmteam.vrcm.service.ContentReportService
import io.github.vrcmteam.vrcm.service.data.ReportCategoryOption
import io.github.vrcmteam.vrcm.service.data.ReportTarget
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private const val ReportDescriptionMaxLength = 500

/**
 * 举报面板：先选分类、再选原因，可补充说明，提交给 VRChat 审核团队处理。
 * 分类与原因取自 VRChat 的 `/config`；已知的键用本地化文案，VRChat 新增的键回落到它返回的英文文案。
 */
@Composable
fun ContentReportSheet(
    target: ReportTarget,
    onDismiss: () -> Unit,
) {
    val service = koinInject<ContentReportService>()
    val locale = strings
    val scope = rememberCoroutineScope()
    var loadAttempt by remember(target) { mutableIntStateOf(0) }
    var categories by remember(target) { mutableStateOf<List<ReportCategoryOption>?>(null) }
    var loadFailed by remember(target) { mutableStateOf(false) }
    var selectedCategoryKey by remember(target) { mutableStateOf<String?>(null) }
    var selectedReasonKey by remember(target) { mutableStateOf<String?>(null) }
    var description by remember(target) { mutableStateOf("") }
    var isSubmitting by remember(target) { mutableStateOf(false) }

    LaunchedEffect(target, loadAttempt) {
        loadFailed = false
        service.reportCategories(target.type)
            .onSuccess { options ->
                categories = options
                // 只有一个分类（群组等）时直接进入原因选择
                if (options.size == 1) selectedCategoryKey = options.single().key
            }
            .onFailure { loadFailed = true }
    }
    val loadedCategories = categories
    val selectedCategory = loadedCategories?.firstOrNull { it.key == selectedCategoryKey }

    val submit: () -> Unit = submit@{
        val category = selectedCategory ?: return@submit
        val reason = selectedReasonKey ?: return@submit
        if (isSubmitting) return@submit
        isSubmitting = true
        scope.launch {
            service.submit(target, category.key, reason, description)
                .onSuccess {
                    SharedFlowCentre.toastText.emit(ToastText.Success(locale.reportSuccess))
                    onDismiss()
                }
                .onFailure {
                    SharedFlowCentre.toastText.emit(ToastText.Error(locale.reportFailed))
                }
            isSubmitting = false
        }
    }

    AppSheet(onDismissRequest = { if (!isSubmitting) onDismiss() }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 720.dp)
                .nestedScroll(rememberConsumeRemainingUpwardScrollConnection())
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                AppText(
                    text = locale.report,
                    style = AppTheme.type.title2,
                    color = AppTheme.colors.tint,
                )
                AppText(
                    text = target.displayName,
                    style = AppTheme.type.subheadline,
                    color = AppTheme.colors.secondaryLabel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            when {
                loadFailed -> Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AppText(locale.reportLoadFailed, color = AppTheme.colors.secondaryLabel)
                    AppButton(
                        text = locale.retry,
                        onClick = { loadAttempt++ },
                        style = AppButtonStyle.Tinted,
                    )
                }

                loadedCategories == null -> Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    AppActivityIndicator(modifier = Modifier.size(28.dp))
                }

                selectedCategory == null -> Column {
                    AppSectionHeader(locale.reportCategoryHeader)
                    AppGroup {
                        loadedCategories.forEachIndexed { index, category ->
                            if (index > 0) AppDivider(Modifier.padding(start = AppSpacing.row))
                            AppRow(
                                title = locale.reportCategoryLabel(category.key) ?: category.label,
                                chevron = true,
                                onClick = {
                                    selectedCategoryKey = category.key
                                    selectedReasonKey = null
                                },
                            )
                        }
                    }
                }

                else -> {
                    val canChangeCategory = loadedCategories.size > 1 && !isSubmitting
                    AppGroup {
                        AppRow(
                            title = locale.reportCategoryHeader,
                            value = locale.reportCategoryLabel(selectedCategory.key) ?: selectedCategory.label,
                            chevron = canChangeCategory,
                            onClick = if (canChangeCategory) {
                                {
                                    selectedCategoryKey = null
                                    selectedReasonKey = null
                                }
                            } else null,
                        )
                    }
                    Column {
                        AppSectionHeader(locale.reportReasonHeader)
                        AppGroup {
                            selectedCategory.reasons.forEachIndexed { index, reason ->
                                if (index > 0) AppDivider(Modifier.padding(start = AppSpacing.row))
                                AppRow(
                                    title = locale.reportReasonLabel(reason.key) ?: reason.label,
                                    enabled = !isSubmitting,
                                    trailing = if (reason.key == selectedReasonKey) {
                                        {
                                            AppIcon(
                                                imageVector = AppIcons.Check,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp),
                                                tint = AppTheme.colors.tint,
                                            )
                                        }
                                    } else null,
                                    onClick = { selectedReasonKey = reason.key },
                                )
                            }
                        }
                    }
                    AppTextField(
                        value = description,
                        onValueChange = { if (it.length <= ReportDescriptionMaxLength) description = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { AppText(locale.reportDescriptionLabel) },
                        supportingText = { AppText("${description.length}/$ReportDescriptionMaxLength") },
                        minLines = 3,
                        maxLines = 6,
                        enabled = !isSubmitting,
                    )
                    AppButton(
                        onClick = submit,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = selectedReasonKey != null && !isSubmitting,
                        style = AppButtonStyle.Prominent,
                        role = AppButtonRole.Destructive,
                    ) {
                        if (isSubmitting) {
                            AppActivityIndicator(
                                modifier = Modifier.size(18.dp),
                                color = LocalContentColor.current,
                            )
                        } else {
                            AppText(locale.reportSubmit)
                        }
                    }
                }
            }
            AppSectionFooter(locale.reportFooter)
        }
    }
}

/** VRChat `/config` 中已知举报分类键的本地化文案；未知键返回 null，由调用方回落到 VRChat 的文案。 */
private fun LocaleStrings.reportCategoryLabel(key: String): String? = when (key) {
    "behavior" -> reportCategoryBehavior
    "chat" -> reportCategoryChat
    "image" -> reportCategoryImage
    "profile" -> reportCategoryProfile
    "avatar" -> reportCategoryAvatar
    "avatarpage" -> reportCategoryAvatarPage
    "group" -> reportCategoryGroup
    "worldpage" -> reportCategoryWorldPage
    "worldimage" -> reportCategoryWorldImage
    "worldobject" -> reportCategoryWorldObject
    "worldaudio" -> reportCategoryWorldAudio
    "worldui" -> reportCategoryWorldUi
    else -> null
}

/** 已知举报原因键的本地化文案；注意 VRChat 的 `threatening` 实际含义是自杀与自残。 */
private fun LocaleStrings.reportReasonLabel(key: String): String? = when (key) {
    "sexual" -> reportReasonSexual
    "harassing" -> reportReasonHarassing
    "hateful" -> reportReasonHateful
    "gore" -> reportReasonGore
    "threatening" -> reportReasonSelfHarm
    "integrity" -> reportReasonIntegrity
    "hacking" -> reportReasonHacking
    "child" -> reportReasonChild
    "copyright" -> reportReasonCopyright
    "other" -> reportReasonOther
    else -> null
}
