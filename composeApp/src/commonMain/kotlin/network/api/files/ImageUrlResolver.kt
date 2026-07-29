package io.github.vrcmteam.vrcm.network.api.files

internal fun resolveOriginalImageUrl(
    imageUrl: String?,
    thumbnailImageUrl: String?,
): String? = (imageUrl?.takeIf { it.isNotBlank() }
    ?: thumbnailImageUrl?.takeIf { it.isNotBlank() })
    ?.let { url ->
        if (FileApi.findFileId(url).isEmpty()) url else FileApi.convertFileUrlToOriginal(url)
    }
