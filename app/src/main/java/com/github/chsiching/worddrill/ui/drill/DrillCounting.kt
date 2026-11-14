package com.github.chsiching.worddrill.ui.drill

internal fun shouldLogSwipe(previousPage: Int, currentPage: Int): Boolean =
    currentPage > previousPage
