package presentation.screens.auth.data

data class VersionVo(
    val tagName: String = "",
    val htmlUrl: String = "",
    val body: String = "",
    val hasNewVersion: Boolean = false,
    val downloadUrl: List<String> = emptyList(),
    /** 弹窗里显示的版本号；Google Play 查不到版本名时为 null，不显示。 */
    val versionName: String? = tagName,
)