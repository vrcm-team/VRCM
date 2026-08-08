package io.github.vrcmteam.vrcm.presentation.screens.meetup.display

import androidx.compose.runtime.Composable

/**
 * 展示页平台沉浸/常亮效果。
 *
 * [enabled] 为 true 且页面处于前台（RESUMED）时获取沉浸式系统栏与屏幕常亮；
 * 进入后台（ON_STOP）或效果离开组合时幂等恢复进入前记录的系统状态，不修改屏幕亮度。
 */
@Composable
internal expect fun MeetupPresentationEffect(enabled: Boolean)
