package com.pzdd.mydia.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import com.pzdd.mydia.ui.prefs.Pref
import com.pzdd.mydia.ui.prefs.PrefScreenView
import com.pzdd.mydia.ui.prefs.PrefScreen
import com.pzdd.mydia.ui.prefs.rememberGlobalSp

/**
 * 设置页：全局配置（模块总开关、显示、日志）。
 *
 * 只读写全局 SP `digXposed`，与注入侧 [com.pzdd.mydia.module.Module.globalPrefs] 对齐。
 *
 * 「显示」分类的开关（ui_blur / ui_theme / ui_dynamic_color）仅供 App 自身 UI 使用，
 * 注入侧不读，可随时改。
 */
@Composable
fun SettingsScreen(contentPadding: PaddingValues, onOpenConsole: () -> Unit = {}) {
    val sp = rememberGlobalSp()
    val screen = PrefScreen(
        key = "settings",
        title = "设置",
        items = listOf(
            Pref.Header("模块"),
            Pref.Switch(
                "switchModule",
                "启用模块",
                summary = "Xposed 模块总入口（关闭后所有 App 都不被 hook）",
                default = false,
            ),
            Pref.Header("日志"),
            Pref.Switch("log_console", "远程日志", summaryOn = "目标 App 里的模块日志广播回本控制台", default = false),
            Pref.Switch(
                "log_enable",
                "启用日志",
                summary = "logcat TAG=MyDia",
                default = true,
            ),
            Pref.Action("open_console", "查看日志控制台", summary = "实时查看被注入 App 的模块日志", onClick = onOpenConsole),
            Pref.Header("显示"),
            Pref.Switch(
                "ui_blur",
                "液态玻璃",
                summaryOn = "顶栏 / 底栏使用液态玻璃（模糊 + 折射）",
                summaryOff = "顶栏 / 底栏使用半透明纯色（更省电）",
                default = true,
            ),
            Pref.ListChoice(
                "ui_theme",
                "主题模式",
                entries = listOf(
                    "跟随系统" to "system",
                    "浅色" to "light",
                    "深色" to "dark",
                ),
                default = "system",
            ),
            Pref.Switch(
                "ui_dynamic_color",
                "动态取色",
                summary = "Android 12+ 跟随系统壁纸配色（关闭用纯 MIUI 配色）",
                default = false,
            ),
            Pref.Header("关于"),
            Pref.Action("about_version", "版本", summary = "MyDia 1.0.0（Dia 复刻骨架）", onClick = {}),
            Pref.Action("about_host", "模块包名", summary = "com.pzdd.mydia", onClick = {}),
        ),
    )
    PrefScreenView(screen, sp, Modifier.padding(contentPadding))
}
