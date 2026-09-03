package io.github.vrcmteam.vrcm.presentation.screens.avatar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import org.koin.compose.koinInject

@Composable
internal fun currentSessionDeletedAvatarIds(): Set<String> {
    val resultStore: AvatarDeletionResultStore = koinInject()
    val session by SharedFlowCentre.currentSession.collectAsState()
    val results by resultStore.results.collectAsState()
    return results.deletedAvatarIds(session?.token)
}
