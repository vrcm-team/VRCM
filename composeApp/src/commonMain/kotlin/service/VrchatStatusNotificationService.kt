package io.github.vrcmteam.vrcm.service

import io.github.vrcmteam.vrcm.network.api.status.VrchatStatusApi
import io.github.vrcmteam.vrcm.presentation.notifications.FriendOnlineNotifier
import io.github.vrcmteam.vrcm.storage.SettingsDao
import org.koin.core.logger.Logger

internal sealed interface VrchatStatusTransition {
    data class Incident(val indicator: String, val description: String) : VrchatStatusTransition
    data object Restored : VrchatStatusTransition
}

internal fun vrchatStatusTransition(
    previousIndicator: String?,
    currentIndicator: String,
    description: String,
): VrchatStatusTransition? {
    val previous = previousIndicator?.normalizedStatusIndicator()
    val current = currentIndicator.normalizedStatusIndicator()
    if (previous == null || previous == current) return null
    return if (current == OPERATIONAL_STATUS) {
        if (previous == OPERATIONAL_STATUS) null else VrchatStatusTransition.Restored
    } else {
        VrchatStatusTransition.Incident(current, description.trim())
    }
}

internal fun String.normalizedStatusIndicator(): String =
    trim().lowercase().ifEmpty { OPERATIONAL_STATUS }

/** Checks VRChat's public Statuspage while the user-owned Android monitor is alive. */
class VrchatStatusNotificationService(
    private val api: VrchatStatusApi,
    private val settingsDao: SettingsDao,
    private val notifier: FriendOnlineNotifier,
    private val logger: Logger,
) {
    suspend fun checkOnce() {
        if (!settingsDao.vrchatStatusNotificationsEnabled) return
        val status = api.fetchStatus()
            .onFailure { logger.warn("Unable to refresh VRChat service status: ${it.message.orEmpty()}") }
            .getOrNull()
            ?.status
            ?: return
        val current = status.indicator.normalizedStatusIndicator()
        val transition = vrchatStatusTransition(
            previousIndicator = settingsDao.lastVrchatStatusIndicator,
            currentIndicator = current,
            description = status.description,
        )
        settingsDao.lastVrchatStatusIndicator = current
        when (transition) {
            is VrchatStatusTransition.Incident -> notifier.notifyVrchatServiceIncident(
                indicator = transition.indicator,
                description = transition.description,
            )
            VrchatStatusTransition.Restored -> notifier.notifyVrchatServiceRestored()
            null -> Unit
        }
    }
}

private const val OPERATIONAL_STATUS = "none"
