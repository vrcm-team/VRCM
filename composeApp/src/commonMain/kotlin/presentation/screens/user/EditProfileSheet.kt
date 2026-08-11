package io.github.vrcmteam.vrcm.presentation.screens.user

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.network.api.attributes.UserStatus
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

private const val MAX_SOCIAL_LINKS = 3
private const val MAX_SOCIAL_LINK_LENGTH = 1000

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileSheet(
    isVisible: Boolean,
    currentUser: UserProfileVo,
    onDismiss: () -> Unit,
    onStatusSave: (status: UserStatus, statusDescription: String) -> Unit,
    onLanguageSave: (languages: List<String>) -> Unit,
    onPronounsSave: (pronouns: String) -> Unit,
    onBioSave: (bio: String) -> Unit,
    onBioLinksSave: (bioLinks: List<String>) -> Unit,
) {
    if (!isVisible) return

    var editingField by remember { mutableStateOf<EditField?>(null) }
    var status by remember { mutableStateOf(currentUser.status.safeStatus()) }
    var statusDescription by remember { mutableStateOf(currentUser.statusDescription) }
    var pronouns by remember { mutableStateOf(currentUser.pronouns) }
    var bio by remember { mutableStateOf(currentUser.bio) }
    var bioLinks by remember { mutableStateOf(currentUser.bioLinks) }
    var languages by remember { mutableStateOf(extractLanguages(currentUser.tags)) }
    var editStatus by remember { mutableStateOf(status) }
    var editStatusDesc by remember { mutableStateOf(statusDescription) }
    var editPronouns by remember { mutableStateOf(pronouns) }
    var editBio by remember { mutableStateOf(bio) }
    var editBioLinks by remember { mutableStateOf(bioLinks) }
    var editLanguages by remember { mutableStateOf(languages) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
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
                Text(
                    text = strings.editProfileTitle,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
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
                ProfileFieldRow(strings.editProfileSocialLinks, bioLinks.filter(String::isNotBlank).joinToString().ifBlank { "—" }) {
                    editBioLinks = bioLinks; editingField = EditField.SocialLinks
                }
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
                    EditField.SocialLinks -> EditSocialLinksField(editBioLinks, { editBioLinks = it }, {
                        bioLinks = editBioLinks; onBioLinksSave(editBioLinks); editingField = null
                    }) { editingField = null }
                    null -> {}
                }
            }
        }
    }
}

@Composable
private fun EditSocialLinksField(
    bioLinks: List<String>,
    onBioLinksChange: (List<String>) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
) {
    EditHeader(strings.editProfileSocialLinks, onBack)
    bioLinks.forEachIndexed { index, link ->
        OutlinedTextField(
            value = link,
            onValueChange = { value ->
                if (value.length <= MAX_SOCIAL_LINK_LENGTH) {
                    onBioLinksChange(bioLinks.toMutableList().also { it[index] = value })
                }
            },
            label = {
                Text(strings.editProfileSocialLink.replace("%s", (index + 1).toString()))
            },
            leadingIcon = { Icon(AppIcons.Link, contentDescription = null) },
            trailingIcon = {
                IconButton(onClick = { onBioLinksChange(bioLinks.filterIndexed { itemIndex, _ -> itemIndex != index }) }) {
                    Icon(AppIcons.Close, contentDescription = strings.editProfileRemoveSocialLink)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            supportingText = { Text("${link.length}/$MAX_SOCIAL_LINK_LENGTH") },
        )
        Spacer(Modifier.height(8.dp))
    }
    OutlinedButton(
        onClick = { onBioLinksChange(bioLinks + "") },
        modifier = Modifier.fillMaxWidth(),
        enabled = bioLinks.size < MAX_SOCIAL_LINKS,
    ) {
        Icon(AppIcons.Add, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(strings.editProfileAddSocialLink)
    }
    Spacer(Modifier.height(8.dp))
    Text(
        strings.editProfileSocialLinksHint,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(16.dp))
    Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) { Text(strings.editProfileSave) }
}

@Composable
private fun ProfileFieldRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(AppIcons.ExpandMore, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.rotate(-90f))
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
}

@Composable
internal fun EditHeader(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp), Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(AppIcons.ExpandMore, "back", modifier = Modifier.rotate(90f)) }
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
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
    OutlinedTextField(
        value = statusDescription, onValueChange = { if (it.length <= 32) onStatusDescChange(it) },
        label = { Text(strings.editProfileStatusDescription) },
        modifier = Modifier.fillMaxWidth(), singleLine = true,
        supportingText = { Text("${statusDescription.length}/32") },
    )
    Spacer(Modifier.height(10.dp))
    STATUS_OPTIONS.forEach { option ->
        Row(Modifier.fillMaxWidth().clickable { onStatusChange(option) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RadioButton(selected = status == option, onClick = { onStatusChange(option) })
            Text(option.toLocalizedString(), style = MaterialTheme.typography.bodyMedium)
        }
    }
    Spacer(Modifier.height(16.dp))
    Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) { Text(strings.editProfileSave) }
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
                InputChip(selected = false, onClick = { onLanguagesChange(languages - code) }, label = { Text(name) },
                    trailingIcon = { Icon(AppIcons.Close, "remove", modifier = Modifier.size(16.dp)) })
            }
        }
        Spacer(Modifier.height(8.dp))
    }
    if (languages.size < 3) {
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text(strings.editProfileAddLanguage) }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                LANGUAGE_OPTIONS.filter { it.first !in languages }.forEach { (code, name) ->
                    DropdownMenuItem(text = { Text("$name (${code.uppercase()})") }, onClick = { onLanguagesChange(languages + code); expanded = false })
                }
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    Text(strings.editProfileLanguageHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(16.dp))
    Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) { Text(strings.editProfileSave) }
}

@Composable
private fun EditContentField(
    title: String, value: String, onValueChange: (String) -> Unit,
    maxLength: Int, maxLines: Int, onSave: () -> Unit, onBack: () -> Unit,
) {
    EditHeader(title, onBack)
    OutlinedTextField(
        value = value, onValueChange = { if (it.length <= maxLength) onValueChange(it) },
        modifier = Modifier.fillMaxWidth(), maxLines = maxLines,
        supportingText = { Text("${value.length}/$maxLength") },
    )
    Spacer(Modifier.height(16.dp))
    Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) { Text(strings.editProfileSave) }
}
