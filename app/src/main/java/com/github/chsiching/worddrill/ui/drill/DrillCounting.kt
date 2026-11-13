package com.github.chsiching.worddrill.ui.drill

/**
 * Swipe-counting decision for the "Drill" tab (pure function, no Android/UI dependency).
 *
 * Spec semantics (pages start at 0; right = forward = page increases):
 * - Right swipe (forward, currentPage > previousPage): count +1, write swipe_log
 * - Left swipe (look back, currentPage < previousPage): no count
 * - Unchanged page (blocked by the Pager at an edge / duplicate settled callbacks): no count
 *
 * The at-edge hint text is rendered separately by the UI based on whether currentPage is
 * at a boundary; it does not go through this function.
 *
 * @return true means write one swipe_log; false means no count.
 */
internal fun shouldLogSwipe(previousPage: Int, currentPage: Int): Boolean =
    currentPage > previousPage
