package io.github.vrcmteam.vrcm.core.shared

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 应用支持的外部深链目标。 */
sealed interface AppDeepLink {
    data class UserProfile(val userId: String) : AppDeepLink
}

/**
 * 外部深链的挂起分发点：平台入口（如 Android intent）解析后投递，
 * UI 侧在完成登录进入主页后消费并导航。始终只保留最新一条，
 * 冷启动时链接会一直等到可导航为止。
 */
object AppDeepLinks {
    private val _pending = MutableStateFlow<AppDeepLink?>(null)
    val pending: StateFlow<AppDeepLink?> = _pending.asStateFlow()

    /** 解析并投递外部 URL；无法识别的链接被忽略。 */
    fun offerUrl(url: String) {
        parse(url)?.let { _pending.value = it }
    }

    /** 消费已处理的链接；期间到达的新链接不会被误清。 */
    fun consume(link: AppDeepLink) {
        _pending.compareAndSet(link, null)
    }

    /**
     * 识别 VRChat 用户主页与 vrcm 自定义 scheme：
     * `https://vrchat.com/home/user/{usr_...}`（含 www）与 `vrcm://user/{usr_...}`。
     */
    fun parse(url: String): AppDeepLink? =
        USER_URL_PATTERN.find(url.trim())
            ?.groupValues?.get(1)
            ?.let(AppDeepLink::UserProfile)

    private val USER_URL_PATTERN = Regex(
        "^(?:https://(?:www\\.)?vrchat\\.com/home/user/|vrcm://user/)(usr_[A-Za-z0-9-]+)",
    )
}
