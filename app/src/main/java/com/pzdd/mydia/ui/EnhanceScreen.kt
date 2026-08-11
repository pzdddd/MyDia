package com.pzdd.mydia.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.pzdd.mydia.ui.prefs.Pref
import com.pzdd.mydia.ui.prefs.PrefRegistry
import com.pzdd.mydia.ui.prefs.PrefScreen
import com.pzdd.mydia.ui.prefs.PrefScreenView
import com.pzdd.mydia.ui.prefs.rememberAppSp

/**
 * 增强模式目录页（per-app）。
 *
 * 仅列出六大扩展分类入口（对话框 / 按钮 / 活动界面已在功能列表页平铺，此处不再重复）：
 *  - 模拟伪装 / 通知提示 / 反检测 / 大杂烩 / 高级 / 开发者（extras 包）
 *
 * 所有配置写在该 App 自己的 SP（[rememberAppSp]），注入侧 [com.pzdd.mydia.module.Module.appPrefs] 读取。
 *
 * @param pkg 目标 App 包名（决定读写哪份 per-app SP）
 */
@Composable
fun EnhanceScreen(
    pkg: String,
    onBack: () -> Unit,
    onOpenCategory: (String) -> Unit,
) {
    val sp = rememberAppSp(pkg)
    DiaScaffold(title = "增强模式", onBack = onBack) { padding ->
        // 六大扩展分类（dialog/button/activity 三件套已在功能列表页平铺，这里只列扩展）
        val extras = PrefRegistry.enhanceCategories.drop(3)
        val screen = PrefScreen(
            key = "enhance_root_$pkg",
            title = "增强模式",
            items = buildList {
                add(Pref.Header("扩展功能"))
                extras.forEach { add(it.toAction(onOpenCategory)) }
            },
        )
        PrefScreenView(screen, sp, Modifier.padding(padding))
    }
}

/** 把一个详情 [PrefScreen] 转成入口条目（带图标 + 副标题）。 */
private fun PrefScreen.toAction(onOpenCategory: (String) -> Unit): Pref.Action = Pref.Action(
    key = "go_$key",
    title = title,
    summary = summary,
    icon = icon,
    onClick = { onOpenCategory(key) },
)
