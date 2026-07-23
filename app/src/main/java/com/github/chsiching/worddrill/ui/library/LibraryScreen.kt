package com.github.chsiching.worddrill.ui.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.github.chsiching.worddrill.R

/**
 * 「库」Tab。骨架阶段为空白页面；后续 ticket 实现词书列表与当前词书切换。
 */
@Composable
fun LibraryScreen(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(stringResource(R.string.tab_library))
    }
}
