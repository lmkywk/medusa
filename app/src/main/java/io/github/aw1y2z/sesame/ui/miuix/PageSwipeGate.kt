package io.github.aw1y2z.sesame.ui.miuix

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 首页卡片 / 计划行的横向滑动优先于四页切页。
 * 子手势开始时锁住 Pager，避免“想关模块却翻到计划页”。
 */
object PageSwipeGate {
    var locked by mutableStateOf(false)
}
