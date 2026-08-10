package com.pzdd.mydia.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.pzdd.mydia.ui.prefs.LocalPrefs
import com.pzdd.mydia.ui.prefs.Pref
import com.pzdd.mydia.ui.prefs.PrefGroupedColumn
import com.pzdd.mydia.ui.prefs.rememberAppSp
import kotlinx.coroutines.launch

/**
 * 某 App 的功能列表页（从「应用」页点击某个 App 进来）。
 *
 * 顶层两类：
 *  1. 基础全局对话框取消（直接展开开关）
 *  2. 增强模式（入口，点击进入 [EnhanceScreen] 的 9 大分类）
 *
 * 右上角菜单（⋮）：启动 / 停止 / 重启目标 App，可在菜单里切换「使用 Root 执行」
 * （per-app 开关 app_control_root，停止/重启需要 Root）。
 *
 * 所有配置写在该 App 自己的 SP（文件名 = 包名），与注入侧 appPrefs 对齐。
 *
 * @param pkg      目标 App 包名
 * @param appLabel 顶部标题用
 * @param onOpenEnhance 点击「增强模式」回调
 */
@Composable
fun AppFunctionListScreen(
    pkg: String,
    appLabel: String,
    onBack: () -> Unit,
    onOpenEnhance: () -> Unit,
) {
    val sp = rememberAppSp(pkg)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var menuOpen by remember { mutableStateOf(false) }
    // 是否用 Root 执行（per-app 开关 app_control_root）；手动读写 SP（actions 在
    // CompositionLocalProvider 外层也能用，且 toggle 时同步 chmod + remote）
    var useRoot by remember { mutableStateOf(sp.getBoolean("app_control_root", false)) }

    // 执行应用控制动作（IO 线程，结果 Toast 提示）
    fun runAction(action: String) {
        menuOpen = false
        scope.launch {
            val msg = when (action) {
                // 启动用标准 API（无需 root）；停止/重启需要 root
                "start" -> AppController.launch(context, pkg)
                "stop" -> AppController.stop(pkg, useRoot)
                "restart" -> AppController.restart(context, pkg, useRoot)
                else -> ""
            }
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        }
    }

    // 包住整个页面：右上角菜单也要读 per-app SP（LocalPrefs）
    CompositionLocalProvider(LocalPrefs provides sp) {
        DiaScaffold(
            title = appLabel,
            onBack = onBack,
            actions = {
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "应用控制")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text("启动应用") }, onClick = { runAction("start") })
                        DropdownMenuItem(text = { Text("停止应用") }, onClick = { runAction("stop") })
                        DropdownMenuItem(text = { Text("重启应用") }, onClick = { runAction("restart") })
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text(if (useRoot) "使用 Root 执行 ✓" else "使用 Root 执行") },
                            onClick = {
                                useRoot = !useRoot
                                sp.edit().putBoolean("app_control_root", useRoot).commit()
                                com.pzdd.mydia.ui.prefs.chmodPref(sp)
                                com.pzdd.mydia.module.RemotePrefsSync.syncLocal(sp)
                            },
                        )
                    }
                }
            },
        ) { padding ->
            val items = listOf(
                Pref.Header("基础功能"),
                Pref.Switch(
                    "global_alert_close",
                    "全局对话框取消",
                    summary = "hook Dialog.show，反射强制改 mCancelable",
                    default = true,
                ),
                Pref.Switch("disable_exit", "禁止退出 App", summary = "拦截 finish / System.exit", default = false),
                Pref.Switch("disable_toast", "禁用 Toast", default = false),
                Pref.Header("高级功能"),
                Pref.Switch(
                    "method_rewrite",
                    "方法重写引擎",
                    summary = "按规则改写目标方法返回值/参数",
                    default = false,
                ),
                Pref.Switch(
                    "shell_monitor",
                    "Shell 命令监控",
                    summary = "hook Runtime.exec，记录执行的命令",
                    default = false,
                ),
                Pref.Switch(
                    "algorithm_monitor",
                    "算法监控",
                    summary = "hook MD5/AES/HMAC/Base64，记录输入输出",
                    default = false,
                ),
                Pref.Header("Frida 注入"),
                Pref.Switch(
                    "code_inject",
                    "启用 Frida 注入",
                    summary = "释放 frida-gadget 并配置加载",
                    default = false,
                ),
                Pref.Switch(
                    "frida_listen",
                    "监听模式",
                    summary = "以 listen 模式启动（交互式），否则纯脚本注入",
                    default = false,
                    dependency = "code_inject",
                ),
                Pref.EditText(
                    "select_active_process_listen_mode",
                    "监听进程名",
                    summary = "仅在该进程加载 gadget（留空 = 主进程）",
                    dependency = "code_inject",
                ),
                Pref.Header("增强模式"),
                Pref.Switch(
                    "mod_ex",
                    "增强模式总开关",
                    summary = "对话框/按钮/活动 + 六大扩展分类（模拟伪装/通知/反检测/大杂烩/高级/开发者）",
                    default = false,
                ),
                Pref.Action(
                    "open_enhance",
                    "配置增强模式",
                    summary = "进入 9 大分类详细配置",
                    icon = Icons.Filled.Refresh,
                    onClick = onOpenEnhance,
                ),
            )
            PrefGroupedColumn(items = items, contentPadding = padding)
        }
    }
}
