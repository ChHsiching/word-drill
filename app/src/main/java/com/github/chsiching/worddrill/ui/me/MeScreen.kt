package com.github.chsiching.worddrill.ui.me

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.github.chsiching.worddrill.R

/**
 * 「我的」Tab。骨架阶段为空白页面；后续 ticket 实现统计、主题切换与关于页。
 */
@Composable
fun MeScreen(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(stringResource(R.string.tab_me))
    }
}
