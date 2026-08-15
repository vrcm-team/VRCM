package io.github.vrcmteam.vrcm.presentation.screens.gallery

enum class GalleryUploadImageFormat(
    val extensions: List<String>,
    val mimeType: String,
    val preserveOriginal: Boolean,
) {
    Jpeg(listOf("jpg", "jpeg"), "image/jpeg", preserveOriginal = false),
    Png(listOf("png"), "image/png", preserveOriginal = false),
    Gif(listOf("gif"), "image/gif", preserveOriginal = true),
    WebP(listOf("webp"), "image/webp", preserveOriginal = true);

    companion object {
        val allowedExtensions: List<String> = entries.flatMap(GalleryUploadImageFormat::extensions)

        fun fromFileName(fileName: String): GalleryUploadImageFormat? {
            val extension = fileName.substringAfterLast('.', missingDelimiterValue = "").lowercase()
            return entries.firstOrNull { extension in it.extensions }
        }
    }
}

internal val GalleryEditorImageExtensions = listOf("jpg", "jpeg", "png", "heic", "heif")
internal val GalleryPickerImageExtensions =
    (GalleryUploadImageFormat.allowedExtensions + GalleryEditorImageExtensions).distinct()
