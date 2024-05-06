package io.github.vrcmteam.vrcm.presentation.supports

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import cafe.adriel.voyager.core.screen.Screen

/**
 * 用于保存刷新状态,和LazyColumn滑动距离状态的父类
 * 子类需要是单例，否则无法保存属性状态
 */
interface Pager:Screen  {

    val index: Int

    val title: String
        @Composable get

    val icon: Painter?
        @Composable get

    @Composable
    override fun Content()

}