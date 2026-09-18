package io.github.vrcmteam.vrcm.presentation.screens.user

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.network.api.attributes.UserStatus
import io.github.vrcmteam.vrcm.presentation.designsystem.*
import io.github.vrcmteam.vrcm.presentation.screens.user.data.UserProfileVo
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings
import io.github.vrcmteam.vrcm.presentation.supports.AppIcons

private val LANGUAGE_OPTIONS = listOf(
    "eng" to "English", "kor" to "Korean", "rus" to "Russian",
    "spa" to "Spanish", "por" to "Portuguese", "zho" to "Chinese",
    "deu" to "German", "jpn" to "Japanese", "fra" to "French",
    "swe" to "Swedish", "ita" to "Italian", "hun" to "Hungarian",
    "ron" to "Romanian", "ara" to "Arabic", "tha" to "Thai",
    "vie" to "Vietnamese", "ind" to "Indonesian", "msa" to "Malay",
    "tur" to "Turkish", "pol" to "Polish", "nld" to "Dutch",
    "ukr" to "Ukrainian", "nor" to "Norwegian", "fin" to "Finnish",
    "dan" to "Danish", "ces" to "Czech", "ell" to "Greek",
    "heb" to "Hebrew", "hin" to "Hindi",
)

private fun extractLanguages(tags: List<String>): List<String> =
    tags.filter { it.startsWith("language_") }.map { it.removePrefix("language_") }

private fun UserStatus?.safeStatus(): UserStatus =
    if (this == null || this == UserStatus.Offline) UserStatus.Active else this

private enum class EditField { Status, Language, Pronouns, Bio, SocialLinks }

private val STATUS_OPTIONS = listOf(
    UserStatus.JoinMe, UserStatus.Active, UserStatus.AskMe, UserStatus.Busy,
)

@Composable
private fun UserStatus.toLocalizedString(): String = when (this) {
    UserStatus.Active -> strings.editProfileStatusOnline
    UserStatus.JoinMe -> strings.editProfileStatusJoinMe
    UserStatus.AskMe -> strings.editProfileStatusAskMe
    UserStatus.Busy -> strings.editProfileStatusBusy
    else -> value
}

@Composable
fun EditProfileSheet(
    isVisible: Boolean,
    currentUser: UserProfileVo,
    bioLinksUpdateState: BioLinksUpdateState,
    boopPrivacyState: BoopPrivacyUiState,
    avatarCopyingPrivacyState: AvatarCopyingPrivacyState,
    onDismiss: () -> Unit,
    onStatusSave: (status: UserStatus, statusDescription: String) -> Unit,
    onLanguageSave: (languages: List<String>) -> Unit,
    onPronounsSave: (pronouns: String) -> Unit,
    onBioSave: (bio: String) -> Unit,
    onBioLinksSave: (bioLinks: List<String>) -> Unit,
    onBoopPrivacyChange: (isEnabled: Boolean) -> Unit,
    onAvatarCopyingChange: (isAllowed: Boolean) -> Unit,
    onAvatarCopyingRetry: () -> Unit,
) {
    if (!isVisible) return

    var editingField by remember { mutableStateOf<EditField?>(null) }
    var status by remember { mutableStateOf(currentUser.status.safeStatus()) }
    var statusDescription by remember { mutableStateOf(currentUser.statusDescription) }
    var pronouns by remember { mutableStateOf(currentUser.pronouns) }
    var bio by remember { mutableStateOf(currentUser.bio) }
    var bioLinks by remember {
        mutableStateOf(currentUser.bioLinks.take(MAX_PROFILE_BIO_LINKS))
    }
    var languages by remember { mutableStateOf(extractLanguages(currentUser.tags)) }
    var editStatus by remember { mutableStateOf(status) }
    var editStatusDesc by remember { mutableStateOf(statusDescription) }
    var editPronouns by remember { mutableStateOf(pronouns) }
    var editBio by remember { mutableStateOf(bio) }
    var editBioLinks by remember { mutableStateOf(bioLinks) }
    var editLanguages by remember { mutableStateOf(languages) }
    var handledBioLinksRequestId by remember {
        mutableLongStateOf(bioLinksUpdateState.completedRequestId)
    }
    val latestBioLinksSaving = rememberUpdatedState(bioLinksUpdateState.isSaving)
    val profileSheetState = rememberAppSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { targetValue ->
            targetValue != AppSheetValue.Hidden || !latestBioLinksSaving.value
        },
    )

    LaunchedEffect(bioLinksUpdateState.completedRequestId) {
        if (bioLinksUpdateState.completedRequestId == handledBioLinksRequestId) return@LaunchedEffect
        handledBioLinksRequestId = bioLinksUpdateState.completedRequestId
        bioLinksUpdateState.savedLinks?.let { savedLinks ->
            val limitedLinks = savedLinks.take(MAX_PROFILE_BIO_LINKS)
            bioLinks = limitedLinks
            editBioLinks = limitedLinks
            if (editingField == EditField.SocialLinks) editingField = null
        }
    }

    AppSheet(
        onDismissRequest = { if (!bioLinksUpdateState.isSaving) onDismiss() },
        sheetState = profileSheetState,
        sheetGesturesEnabled = !bioLinksUpdateState.isSaving,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .then(
                    if (editingField == null || editingField == EditField.SocialLinks) {
                        Modifier.verticalScroll(rememberScrollState())
                    } else {
                        Modifier
                    }
                ),
        ) {
            if (editingField == null) {
                AppText(
                    text = strings.editProfileTitle,
                    style = AppTheme.type.title2,
                    color = AppTheme.colors.tint,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                ProfileFieldRow(strings.editProfileStatus, "${status.toLocalizedString()} $statusDescription".trim()) {
                    editStatus = status; editStatusDesc = statusDescription; editingField = EditField.Status
                }
                ProfileFieldRow(strings.editProfileLanguage, languages.mapNotNull { c -> LANGUAGE_OPTIONS.find { it.first == c }?.second }.ifEmpty { listOf("—") }.joinToString(", ")) {
                    editLanguages = languages; editingField = EditField.Language
                }
                ProfileFieldRow(strings.editProfilePronouns, pronouns.ifBlank { "—" }) {
                    editPronouns = pronouns; editingField = EditField.Pronouns
                }
                ProfileFieldRow(strings.editProfileBio, bio.ifBlank { "—" }.let { if (it.length > 60) it.take(60) + "…" else it }) {
                    editBio = bio; editingField = EditField.Bio
                }
                AvatarCopyingPrivacyRow(
                    state = avatarCopyingPrivacyState,
                    onCheckedChange = onAvatarCopyingChange,
                    onRetry = onAvatarCopyingRetry,
                )
                ProfileFieldRow(strings.editProfileSocialLinks, bioLinks.filter(String::isNotBlank).joinToString().ifBlank { "—" }) {
                    editBioLinks = bioLinks; editingField = EditField.SocialLinks
                }
                BoopPrivacyRow(
                    state = boopPrivacyState,
                    onCheckedChange = onBoopPrivacyChange,
                )
            } else {
                when (editingField) {
                    EditField.Status -> EditStatusField(editStatus, editStatusDesc, { editStatus = it }, { editStatusDesc = it }, {
                        status = editStatus; statusDescription = editStatusDesc; onStatusSave(editStatus, editStatusDesc); editingField = null
                    }) { editingField = null }
                    EditField.Language -> EditLanguageField(editLanguages, { editLanguages = it }, {
                        languages = editLanguages; onLanguageSave(editLanguages); editingField = null
                    }) { editingField = null }
                    EditField.Pronouns -> EditContentField(strings.editProfilePronouns, editPronouns, { editPronouns = it }, 32, 2, {
                        pronouns = editPronouns; onPronounsSave(editPronouns); editingField = null
                    }) { editingField = null }
                    EditField.Bio -> EditContentField(strings.editProfileBio, editBio, { editBio = it }, 512, 8, {
                        bio = editBio; onBioSave(editBio); editingField = null
                    }) { editingField = null }
                    EditField.SocialLinks -> EditSocialLinksField(
                        bioLinks = editBioLinks,
                        isSaving = bioLinksUpdateState.isSaving,
                        onBioLinksChange = {
                            editBioLinks = it.take(MAX_PROFILE_BIO_LINKS)
                        },
                        onSave = { onBioLinksSave(editBioLinks) },
                        onBack = { editingField = null },
                    )
                    null -> {}
                }
            }
        }
    }
}

@Composable
private fun BoopPrivacyRow(
    state: BoopPrivacyUiState,
    onCheckedChange: (Boolean) -> Unit,
) {
    val enabled = !state.isLoading && !state.isUpdating
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(
                value = state.isEnabled,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppText(
            text = strings.editProfileBoopPrivacy,
            style = AppTheme.type.body,
            color = AppTheme.colors.label,
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier = Modifier
                .width(52.dp)
                .heightIn(min = 48.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (state.isLoading || state.isUpdating) {
                AppActivityIndicator(
                    modifier = Modifier.size(24.dp),
                )
            } else {
                AppToggle(
                    checked = state.isEnabled,
                    onCheckedChange = null,
                )
            }
        }
    }
    AppDivider(color = AppTheme.colors.fill)
}

@Composable
private fun AvatarCopyingPrivacyRow(
    state: AvatarCopyingPrivacyState,
    onCheckedChange: (Boolean) -> Unit,
    onRetry: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            AppText(
                text = strings.editProfileAvatarCopying,
                style = AppTheme.type.body,
                color = AppTheme.colors.label,
            )
            AppText(
                text = strings.editProfileAvatarCopyingDescription,
                style = AppTheme.type.caption1,
                color = AppTheme.colors.secondaryLabel,
            )
            if (state.loadFailed || state.updateFailed) {
                AppText(
                    text = if (state.loadFailed) {
                        strings.editProfileAvatarCopyingLoadFailed
                    } else {
                        strings.editProfileAvatarCopyingUpdateFailed
                    },
                    style = AppTheme.type.caption1,
                    color = AppTheme.colors.destructive,
                )
            }
        }
        Box(
            modifier = Modifier
                .width(96.dp)
                .heightIn(min = 48.dp),
            contentAlignment = Alignment.CenterEnd,
        ) {
            when {
                state.isAllowed != null -> {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (state.isSaving) {
                            AppActivityIndicator(
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        AppToggle(
                            checked = state.isAllowed,
                            onCheckedChange = onCheckedChange,
                            enabled = !state.isSaving,
                        )
                    }
                }
                state.loadFailed -> AppButton(onClick = onRetry, style = AppButtonStyle.Plain) {
                    AppText(strings.retry)
                }
                else -> AppActivityIndicator(
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
    AppDivider(color = AppTheme.colors.fill)
}

@Composable
private fun EditSocialLinksField(
    bioLinks: List<String>,
    isSaving: Boolean,
    onBioLinksChange: (List<String>) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
) {
    EditHeader(strings.editProfileSocialLinks, onBack, enabled = !isSaving)
    bioLinks.forEachIndexed { index, link ->
        AppTextField(
            value = link,
            onValueChange = { value ->
                onBioLinksChange(bioLinks.toMutableList().also { it[index] = value })
            },
            label = {
                AppText(strings.editProfileSocialLink.replace("%s", (index + 1).toString()))
            },
            leadingIcon = { AppIcon(AppIcons.Link, contentDescription = null) },
            trailingIcon = {
                AppIconButton(
                    onClick = {
                        onBioLinksChange(bioLinks.filterIndexed { itemIndex, _ -> itemIndex != index })
                    },
                    enabled = !isSaving,
                ) {
                    AppIcon(AppIcons.Close, contentDescription = strings.editProfileRemoveSocialLink)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !isSaving,
        )
        Spacer(Modifier.height(8.dp))
    }
    AppButton(
        onClick = { onBioLinksChange(bioLinks + "") },
        modifier = Modifier.fillMaxWidth(),
        enabled = !isSaving && bioLinks.size < MAX_PROFILE_BIO_LINKS,
        style = AppButtonStyle.Gray,
    ) {
        AppIcon(AppIcons.Add, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        AppText(strings.editProfileAddSocialLink)
    }
    Spacer(Modifier.height(8.dp))
    AppText(
        strings.editProfileSocialLinksHint,
        style = AppTheme.type.caption1,
        color = AppTheme.colors.secondaryLabel,
    )
    Spacer(Modifier.height(16.dp))
    AppButton(
        onClick = onSave,
        modifier = Modifier.fillMaxWidth(),
        enabled = !isSaving,
        style = AppButtonStyle.Prominent,
    ) {
        if (isSaving) {
            AppActivityIndicator(
                modifier = Modifier.size(18.dp),
                color = LocalContentColor.current,
            )
            Spacer(Modifier.width(8.dp))
        }
        AppText(strings.editProfileSave)
    }
}

@Composable
private fun ProfileFieldRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            AppText(label, style = AppTheme.type.body, color = AppTheme.colors.label)
            AppText(value, style = AppTheme.type.subheadline, color = AppTheme.colors.secondaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        AppIcon(AppIcons.ExpandMore, null, tint = AppTheme.colors.secondaryLabel, modifier = Modifier.rotate(-90f))
    }
    AppDivider(color = AppTheme.colors.fill)
}

@Composable
internal fun EditHeader(title: String, onBack: () -> Unit, enabled: Boolean = true) {
    Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp), Alignment.CenterVertically) {
        AppIconButton(onClick = onBack, enabled = enabled) {
            AppIcon(AppIcons.ExpandMore, "back", modifier = Modifier.rotate(90f))
        }
        AppText(title, style = AppTheme.type.headline, color = AppTheme.colors.label)
    }
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun EditStatusField(
    status: UserStatus, statusDescription: String,
    onStatusChange: (UserStatus) -> Unit, onStatusDescChange: (String) -> Unit,
    onSave: () -> Unit, onBack: () -> Unit,
) {
    EditHeader(strings.editProfileStatus, onBack)
    AppTextField(
        value = statusDescription, onValueChange = { if (it.length <= 32) onStatusDescChange(it) },
        label = { AppText(strings.editProfileStatusDescription) },
        modifier = Modifier.fillMaxWidth(), singleLine = true,
        supportingText = { AppText("${statusDescription.length}/32") },
    )
    Spacer(Modifier.height(10.dp))
    STATUS_OPTIONS.forEach { option ->
        Row(Modifier.fillMaxWidth().clickable { onStatusChange(option) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppRadioButton(selected = status == option, onClick = { onStatusChange(option) })
            AppText(option.toLocalizedString(), style = AppTheme.type.subheadline)
        }
    }
    Spacer(Modifier.height(16.dp))
    AppButton(onClick = onSave, modifier = Modifier.fillMaxWidth(), style = AppButtonStyle.Prominent) { AppText(strings.editProfileSave) }
}

@Composable
private fun EditLanguageField(
    languages: List<String>, onLanguagesChange: (List<String>) -> Unit,
    onSave: () -> Unit, onBack: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    EditHeader(strings.editProfileLanguage, onBack)
    if (languages.isNotEmpty()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            languages.forEach { code ->
                val name = LANGUAGE_OPTIONS.find { it.first == code }?.second ?: code
                AppFilterChip(selected = false, onClick = { onLanguagesChange(languages - code) }, label = { AppText(name) },
                    trailingIcon = { AppIcon(AppIcons.Close, "remove", modifier = Modifier.size(16.dp)) })
            }
        }
        Spacer(Modifier.height(8.dp))
    }
    if (languages.size < 3) {
        Box {
            AppButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth(), style = AppButtonStyle.Gray) { AppText(strings.editProfileAddLanguage) }
            AppMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                LANGUAGE_OPTIONS.filter { it.first !in languages }.forEach { (code, name) ->
                    AppMenuItem(text = { AppText("$name (${code.uppercase()})") }, onClick = { onLanguagesChange(languages + code); expanded = false })
                }
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    AppText(strings.editProfileLanguageHint, style = AppTheme.type.caption1, color = AppTheme.colors.secondaryLabel)
    Spacer(Modifier.height(16.dp))
    AppButton(onClick = onSave, modifier = Modifier.fillMaxWidth(), style = AppButtonStyle.Prominent) { AppText(strings.editProfileSave) }
}

@Composable
private fun EditContentField(
    title: String, value: String, onValueChange: (String) -> Unit,
    maxLength: Int, maxLines: Int, onSave: () -> Unit, onBack: () -> Unit,
) {
    EditHeader(title, onBack)
    AppTextField(
        value = value, onValueChange = { if (it.length <= maxLength) onValueChange(it) },
        modifier = Modifier.fillMaxWidth(), maxLines = maxLines,
        supportingText = { AppText("${value.length}/$maxLength") },
    )
    Spacer(Modifier.height(16.dp))
    AppButton(onClick = onSave, modifier = Modifier.fillMaxWidth(), style = AppButtonStyle.Prominent) { AppText(strings.editProfileSave) }
}
