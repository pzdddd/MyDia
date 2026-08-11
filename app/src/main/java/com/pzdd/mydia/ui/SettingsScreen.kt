package com.pzdd.mydia.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.pzdd.mydia.ui.prefs.Pref
import com.pzdd.mydia.ui.prefs.PrefScreenView
import com.pzdd.mydia.ui.prefs.PrefScreen
import com.pzdd.mydia.ui.prefs.rememberGlobalSp
import com.pzdd.mydia.ui.prefs.rememberBoolPref
import kotlinx.coroutines.delay

/** 获取本机局域网 IPv4 地址（非回环，取第一个）。 */
fun lanIp(): String = runCatching {
    java.net.NetworkInterface.getNetworkInterfaces()?.toList()
        ?.flatMap { it.inetAddresses.toList() }
        ?.firstOrNull { !it.isLoopbackAddress && it is java.net.Inet4Address }
        ?.hostAddress
}.getOrNull() ?: "127.0.0.1"

/**
 * 设置页：全局配置（模块总开关、显示、日志）。
 *
 * 只读写全局 SP `digXposed`，与注入侧 [com.pzdd.mydia.module.Module.globalPrefs] 对齐。
 *
 * 「显示」分类的开关（ui_blur / ui_theme / ui_dynamic_color）仅供 App 自身 UI 使用，
 * 注入侧不读，可随时改。
 */
@Composable
fun SettingsScreen(contentPadding: PaddingValues, onOpenConsole: () -> Unit = {}, onOpenScope: () -> Unit = {}) {
    val sp = rememberGlobalSp()
    val context = androidx.compose.ui.platform.LocalContext.current

    // MCP 开关状态：开→启动前台服务，关→停止（与设置项联动）
    val mcpEnabled = rememberBoolPref("mcp_enabled", false)
    LaunchedEffect(mcpEnabled.value) {
        if (mcpEnabled.value) com.pzdd.mydia.module.mcp.McpServerService.start(context)
        else com.pzdd.mydia.module.mcp.McpServerService.stop(context)
    }

    // 局域网 IP 每 5s 动态刷新（Wi-Fi 变化自动更新）
    val lanIpState by produceState(lanIp()) {
        while (true) {
            value = lanIp()
            delay(5000)
        }
    }
    val mcpLanOn = rememberBoolPref("mcp_bind_lan", false).value
    val mcpPort = sp.getString("mcp_port", "8090") ?: "8090"
    val mcpAddr = if (mcpLanOn) "http://$lanIpState:$mcpPort/mcp" else "adb forward tcp:$mcpPort tcp:$mcpPort → http://localhost:$mcpPort/mcp"

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
            Pref.Action(
                "open_scope",
                "作用域管理",
                summary = "查看/添加/移除被 hook 的 App（免手动去 LSPosed 勾选）",
                icon = Icons.Filled.AdminPanelSettings,
                onClick = onOpenScope,
            ),
            Pref.Header("日志"),
            Pref.Switch("log_console", "远程日志", summaryOn = "目标 App 里的模块日志广播回本控制台", default = false),
            Pref.Switch(
                "log_enable",
                "启用日志",
                summary = "logcat TAG=MyDia",
                default = true,
            ),
            Pref.Action(
                "open_console",
                "查看日志控制台",
                summary = "实时查看被注入 App 的模块日志",
                icon = Icons.Filled.Terminal,
                onClick = onOpenConsole,
            ),
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
            Pref.Header("MCP 连接"),
            Pref.Switch(
                "mcp_enabled",
                "启用 MCP 服务",
                summary = "暴露 MyDia 的 hook 分析能力为 MCP 工具，供桌面 AI 连接",
                summaryOn = "服务已开启",
                summaryOff = "服务已关闭",
                default = false,
            ),
            Pref.Switch(
                "mcp_bind_lan",
                "局域网直连",
                summary = "开启后同一 Wi-Fi 设备可连 ${lanIp()}，关闭需 adb forward",
                summaryOn = "监听局域网",
                summaryOff = "仅 adb forward",
                default = false,
                dependency = "mcp_enabled",
            ),
            Pref.EditText("mcp_port", "端口", summary = "默认 8090", default = "8090", numeric = true, dependency = "mcp_enabled"),
            // 动态连接地址（IP 每 5s 刷新；点击复制到剪贴板）
            Pref.Action(
                "mcp_addr",
                "连接地址",
                summary = mcpAddr,
                icon = Icons.Filled.Send,
                dependency = "mcp_enabled",
                onClick = {
                    runCatching {
                        val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        cm.setPrimaryClip(android.content.ClipData.newPlainText("mcp", mcpAddr))
                        android.widget.Toast.makeText(context, "已复制：$mcpAddr", android.widget.Toast.LENGTH_LONG).show()
                    }
                },
            ),
            Pref.Header("关于"),
            Pref.Action("about_version", "版本", summary = "MyDia 1.0.0（Dia 复刻骨架）", icon = Icons.Filled.HelpOutline, onClick = {}),
            Pref.Action("about_host", "模块包名", summary = "com.pzdd.mydia", onClick = {}),
        ),
    )
    PrefScreenView(screen, sp, Modifier.padding(contentPadding))
}
