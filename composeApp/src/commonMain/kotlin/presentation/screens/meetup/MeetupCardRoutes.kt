package io.github.vrcmteam.vrcm.presentation.screens.meetup

import androidx.compose.runtime.Composable
import io.github.vrcmteam.vrcm.presentation.navigation.AppRoute
import io.github.vrcmteam.vrcm.presentation.navigation.LocalNavigator
import io.github.vrcmteam.vrcm.presentation.navigation.currentOrThrow
import io.github.vrcmteam.vrcm.presentation.screens.meetup.display.MeetupCardDisplayContent
import io.github.vrcmteam.vrcm.presentation.screens.meetup.editor.MeetupCardEditorContent
import kotlinx.serialization.Serializable
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** 身份牌全屏展示路由；payload 只有 owner ID。 */
@Serializable
data class MeetupCardDisplayRoute(val ownerUserId: String) : AppRoute {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val model: MeetupCardScreenModel = koinViewModel { parametersOf(ownerUserId) }
        MeetupCardDisplayContent(
            model = model,
            onBack = { navigator.pop() },
            onEdit = { navigator.push(MeetupCardEditorRoute(ownerUserId)) },
        )
    }
}

/** 身份牌编辑路由；payload 只有 owner ID。 */
@Serializable
data class MeetupCardEditorRoute(val ownerUserId: String) : AppRoute {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val model: MeetupCardScreenModel = koinViewModel { parametersOf(ownerUserId) }
        // 从展示页点编辑进来时"完成"退回展示页；首次配置从首页进来时用展示页
        // 顶替编辑页，让返回键直接回首页而不是又回到编辑页。
        val cameFromDisplay = navigator.items
            .getOrNull(navigator.size - 2)
            .let { it is MeetupCardDisplayRoute && it.ownerUserId == ownerUserId }
        MeetupCardEditorContent(
            model = model,
            onBack = { navigator.pop() },
            onDone = {
                if (cameFromDisplay) {
                    navigator.pop()
                } else {
                    navigator replace MeetupCardDisplayRoute(ownerUserId)
                }
            },
        )
    }
}
