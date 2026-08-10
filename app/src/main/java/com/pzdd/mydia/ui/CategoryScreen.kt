package com.pzdd.mydia.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pzdd.mydia.ui.prefs.PrefRegistry
import com.pzdd.mydia.ui.prefs.PrefScreenView
import com.pzdd.mydia.ui.prefs.rememberAppSp

/**
 * 通用分类详情页（per-app）。按 [cat] 从 [PrefRegistry] 取出对应
 * [com.pzdd.mydia.ui.prefs.PrefScreen] 渲染，读写该 App 自己的 SP。
 *
 * @param pkg 目标 App 包名
 * @param cat 分类 key（dialog/button/activity/fake/notify/anti/misc/advanced/dev）
 */
@Composable
fun CategoryScreen(
    pkg: String,
    cat: String,
    onBack: () -> Unit,
) {
    val sp = rememberAppSp(pkg)
    val screen = PrefRegistry.byKey(cat)
    if (screen == null) {
        DiaScaffold(title = "未知分类", onBack = onBack) { padding ->
            androidx.compose.material3.Text(
                "未知分类：$cat",
                modifier = Modifier.padding(padding).padding(16.dp),
            )
        }
        return
    }
    DiaScaffold(title = screen.title, onBack = onBack) { padding ->
        PrefScreenView(screen, sp, Modifier.padding(padding))
    }
}
