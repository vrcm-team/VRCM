package io.github.vrcmteam.vrcm.presentation.screens.avatar

import io.github.vrcmteam.vrcm.network.api.avatars.data.AvatarStyle
import io.github.vrcmteam.vrcm.network.api.avatars.data.AvatarUpdateData
import io.github.vrcmteam.vrcm.presentation.screens.avatar.data.AvatarProfileVo

private const val AuthorTagPrefix = "author_tag_"

internal enum class AvatarContentTag(val apiValue: String) {
    Horror("content_horror"),
    Gore("content_gore"),
    Violence("content_violence"),
    Adult("content_adult"),
    Sex("content_sex"),
}

internal sealed interface AvatarStyleChoice {
    data object Unchanged : AvatarStyleChoice
    data object Clear : AvatarStyleChoice
    data class Selected(val id: String) : AvatarStyleChoice
}

internal data class AvatarMetadataDraft(
    val name: String,
    val description: String,
    val contentTags: Set<String>,
    val authorTags: String,
    val primaryStyle: AvatarStyleChoice = AvatarStyleChoice.Unchanged,
    val secondaryStyle: AvatarStyleChoice = AvatarStyleChoice.Unchanged,
)

internal sealed interface AvatarMetadataChange {
    data object InvalidName : AvatarMetadataChange
    data object InvalidContentTags : AvatarMetadataChange
    data object InvalidPrimaryStyle : AvatarMetadataChange
    data object InvalidSecondaryStyle : AvatarMetadataChange
    data object NoChanges : AvatarMetadataChange
    data class Update(val data: AvatarUpdateData) : AvatarMetadataChange
}

internal fun normalizedAvatarStyles(styles: List<AvatarStyle>): List<AvatarStyle> =
    styles.asSequence()
        .filter { it.id.isNotBlank() && it.styleName.isNotBlank() }
        .distinctBy(AvatarStyle::id)
        .toList()

/** Builds the smallest update while retaining tags outside this editor's ownership. */
internal fun avatarMetadataChange(
    current: AvatarProfileVo,
    draft: AvatarMetadataDraft,
    allowedStyles: List<AvatarStyle>,
): AvatarMetadataChange {
    val normalizedName = draft.name.trim()
    if (normalizedName.isEmpty()) return AvatarMetadataChange.InvalidName

    val allowedContentTags = AvatarContentTag.entries.mapTo(mutableSetOf()) { it.apiValue }
    if (!allowedContentTags.containsAll(draft.contentTags)) {
        return AvatarMetadataChange.InvalidContentTags
    }

    val normalizedAuthorTags = draft.authorTags
        .split(',', '\n')
        .map { it.trim().removePrefix(AuthorTagPrefix).trim() }
        .filter(String::isNotEmpty)
        .distinct()
    val currentContentTags = current.tags.filterTo(mutableSetOf()) { it in allowedContentTags }
    val currentAuthorTags = current.tags.asSequence()
        .filter { it.startsWith(AuthorTagPrefix) }
        .map { it.removePrefix(AuthorTagPrefix) }
        .filter(String::isNotEmpty)
        .distinct()
        .toList()
    val tagsChanged = draft.contentTags != currentContentTags ||
        normalizedAuthorTags != currentAuthorTags
    val updatedTags = if (tagsChanged) {
        buildList {
            addAll(current.tags.filterNot {
                it in allowedContentTags || it.startsWith(AuthorTagPrefix)
            })
            AvatarContentTag.entries.forEach { tag ->
                if (tag.apiValue in draft.contentTags) add(tag.apiValue)
            }
            normalizedAuthorTags.forEach { add("$AuthorTagPrefix$it") }
        }.distinct()
    } else {
        null
    }

    val primaryStyle = resolveStyleUpdate(
        currentStyle = current.primaryStyle,
        choice = draft.primaryStyle,
        allowedStyles = allowedStyles,
    ) ?: return AvatarMetadataChange.InvalidPrimaryStyle
    val secondaryStyle = resolveStyleUpdate(
        currentStyle = current.secondaryStyle,
        choice = draft.secondaryStyle,
        allowedStyles = allowedStyles,
    ) ?: return AvatarMetadataChange.InvalidSecondaryStyle

    val update = AvatarUpdateData(
        name = normalizedName.takeIf { it != current.avatarName },
        description = draft.description.takeIf { it != current.avatarDescription },
        tags = updatedTags,
        primaryStyle = primaryStyle.value,
        secondaryStyle = secondaryStyle.value,
    )
    return if (update == AvatarUpdateData()) {
        AvatarMetadataChange.NoChanges
    } else {
        AvatarMetadataChange.Update(update)
    }
}

private data class StyleUpdate(val value: String?)

private fun resolveStyleUpdate(
    currentStyle: String?,
    choice: AvatarStyleChoice,
    allowedStyles: List<AvatarStyle>,
): StyleUpdate? = when (choice) {
    AvatarStyleChoice.Unchanged -> StyleUpdate(null)
    AvatarStyleChoice.Clear -> StyleUpdate("".takeIf { !currentStyle.isNullOrBlank() })
    is AvatarStyleChoice.Selected -> {
        val selected = allowedStyles.firstOrNull { it.id == choice.id } ?: return null
        StyleUpdate(
            selected.id.takeUnless {
                currentStyle == selected.id || currentStyle == selected.styleName
            }
        )
    }
}

internal fun AvatarProfileVo.contentTags(): Set<String> =
    tags.filterTo(linkedSetOf()) { tag ->
        AvatarContentTag.entries.any { it.apiValue == tag }
    }

internal fun AvatarProfileVo.authorTagsText(): String =
    tags.asSequence()
        .filter { it.startsWith(AuthorTagPrefix) }
        .map { it.removePrefix(AuthorTagPrefix) }
        .filter(String::isNotEmpty)
        .distinct()
        .joinToString(", ")
