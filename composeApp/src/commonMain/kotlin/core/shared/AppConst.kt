package io.github.vrcmteam.vrcm.core.shared

object AppConst {
    const val APP_NAME = "VRCM"

    const val APP_VERSION = "1.1.4"

    const val APP_GITHUB_URL = "https://github.com/vrcm-team/VRCM"

    const val APP_GITHUB_LATEST_RELEASE_URL = "https://api.github.com/repos/vrcm-team/VRCM/releases/latest"

    const val APP_PRIVACY_POLICY_URL = "https://github.com/vrcm-team/VRCM/blob/main/privacy-policy.md"

    /** 公开联系邮箱：与隐私政策中的联系方式一致，设置页"联系我们"使用。 */
    const val APP_CONTACT_EMAIL = "kamosama.dev@gmail.com"

    /** VRChat 要求 API 调用方以"应用名/版本 联系方式"标识自己；联系方式用项目主页，不在每个请求里带邮箱。 */
    const val APP_USER_AGENT = "$APP_NAME/$APP_VERSION $APP_GITHUB_URL"

    const val VRCHAT_TERMS_URL = "https://hello.vrchat.com/legal"

    const val VRCHAT_COMMUNITY_GUIDELINES_URL = "https://hello.vrchat.com/community-guidelines"
}
