package io.github.vrcmteam.vrcm.presentation.screens.meetup.display

import androidx.compose.runtime.Composable

/**
 * Desktop 无需沉浸/常亮控制：展示页填满应用内容区即可，
 * 不切换操作系统全屏，也没有系统栏可隐藏，保持幂等 no-op。
 */
@Composable
internal actual fun MeetupPresentationEffect(enabled: Boolean) = Unit
