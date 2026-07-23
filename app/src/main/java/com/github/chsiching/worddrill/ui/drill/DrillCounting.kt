package com.github.chsiching.worddrill.ui.drill

/**
 * 「刷」Tab 滑动计数决策（纯函数，无 Android/UI 依赖）。
 *
 * 规格语义（页码从 0 起，向右 = 前进 = page 递增）：
 * - 向右滑（前进，currentPage > previousPage）→ 计数 +1，写 swipe_log
 * - 向左滑（回看，currentPage < previousPage）→ 不计数
 * - 页码不变（到头被 Pager 阻挡 / settled 重复回调）→ 不计数
 *
 * 到头提示文案由 UI 根据 currentPage 是否在边界单独渲染，不经过本函数。
 *
 * @return true 表示应写一条 swipe_log；false 表示不计数。
 */
internal fun shouldLogSwipe(previousPage: Int, currentPage: Int): Boolean =
    currentPage > previousPage
