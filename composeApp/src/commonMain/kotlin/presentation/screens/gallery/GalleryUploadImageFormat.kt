package io.github.vrcmteam.vrcm.presentation.screens.gallery

enum class GalleryUploadImageFormat(
    val extensions: List<String>,
    val mimeType: String,
) {
    Jpeg(listOf("jpg", "jpeg"), "image/jpeg"),
    Png(listOf("png"), "image/png"),
    Gif(listOf("gif"), "image/gif"),
    WebP(listOf("webp"), "image/webp");

    companion object {
        val allowedExtensions: List<String> = entries.flatMap(GalleryUploadImageFormat::extensions)

        fun fromFileName(fileName: String): GalleryUploadImageFormat? {
            val extension = fileName.substringAfterLast('.', missingDelimiterValue = "").lowercase()
            return entries.firstOrNull { extension in it.extensions }
        }
    }
}
