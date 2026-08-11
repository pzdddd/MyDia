package com.pzdd.mydia.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable

/**
 * 全 App 通用的 Scaffold：顶部一个 [TopAppBar]，可选返回按钮。
 *
 * 所有页面统一用它，保证 Material3 风格一致 + 状态栏 inset 正确。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiaScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        // 页面背景 background（浅色 #E8E8E8 / 深色黑）；卡片由列表组件用 surface（白/深灰）
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(title) },
                // 顶栏用背景色（浅色深灰 / 深色黑），与下方白色卡片区分——
                // 默认 surface(白) 会和白色卡片融为一体。
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    }
                },
                actions = actions,
            )
        },
    ) { padding ->
        content(padding)
    }
}
