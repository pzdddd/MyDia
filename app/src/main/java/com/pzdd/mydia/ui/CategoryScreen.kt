package com.pzdd.mydia.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pzdd.mydia.ui.prefs.Pref
import com.pzdd.mydia.ui.prefs.PrefRegistry
import com.pzdd.mydia.ui.prefs.PrefScreenView
import com.pzdd.mydia.ui.prefs.rememberAppSp

/**
 * 通用分类详情页（per-app）。按 [cat] 从 [PrefRegistry] 取出对应
 * [com.pzdd.mydia.ui.prefs.PrefScreen] 渲染，读写该 App 自己的 SP。
 *
 * 部分分类的 [Pref.Action] 在注册表里是占位空 onClick，这里注入真实路由：
 *  - activity：pick_activity_single/multi → Activity 列表选择器
 *  - rewrite_monitor：open_rewrite_rules/open_dex_paths → 规则编辑 / dex 源管理
 *  - frida：open_frida_scripts/open_console → 脚本管理 / 日志控制台
 *
 * @param pkg 目标 App 包名
 * @param cat 分类 key（basic/dialog/button/activity/fake/notify/anti/misc/advanced/dev/rewrite_monitor/frida）
 * @param onPickActivity 打开 Activity 列表选择器（mode = "single"/"multi"）
 */
@Composable
fun CategoryScreen(
    pkg: String,
    cat: String,
    onBack: () -> Unit,
    onPickActivity: (String) -> Unit = {},
    onOpenRewriteRules: () -> Unit = {},
    onOpenDexPaths: () -> Unit = {},
    onOpenFridaScripts: () -> Unit = {},
    onOpenConsole: () -> Unit = {},
    onOpenAlgorithmLog: () -> Unit = {},
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

    // 注入占位 Action 的真实路由 + 显示当前已选值
    val items = screen.items.map { item ->
        when (item) {
            is Pref.Action -> when (item.key) {
                "pick_activity_single" -> item.copy(
                    onClick = { onPickActivity("single") },
                    summary = "已选：${sp.getString("app_activity_select", "")?.ifBlank { "未选择" }}",
                )
                "pick_activity_multi" -> item.copy(
                    onClick = { onPickActivity("multi") },
                    summary = "已选 ${sp.getString("disable_activity_select", "")?.split(",", "，")?.filter { it.isNotBlank() }?.size ?: 0} 个",
                )
                "open_rewrite_rules" -> item.copy(onClick = onOpenRewriteRules)
                "open_dex_paths" -> item.copy(onClick = onOpenDexPaths)
                "open_frida_scripts" -> item.copy(onClick = onOpenFridaScripts)
                "open_console" -> item.copy(onClick = onOpenConsole)
                "open_algorithm_log" -> item.copy(onClick = onOpenAlgorithmLog)
                else -> item
            }
            else -> item
        }
    }

    DiaScaffold(title = screen.title, onBack = onBack) { padding ->
        PrefScreenView(screen.copy(items = items), sp, Modifier.padding(padding))
    }
}
