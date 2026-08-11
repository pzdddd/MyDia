package com.pzdd.mydia.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Rule
import androidx.compose.material.icons.filled.Terminal
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
import com.pzdd.mydia.ui.prefs.PrefRegistry
import com.pzdd.mydia.ui.prefs.flattenWithoutLeadingHeader
import com.pzdd.mydia.ui.prefs.rememberAppSp
import kotlinx.coroutines.launch

/**
 * 某 App 的功能列表页（从「应用」页点击某个 App 进来）。
 *
 * 分类目录式：每个功能大类折叠成一个入口（点击进详情页配置），
 * 对话框/按钮/活动界面 + 六大扩展分类全部平铺在这一层。
 *
 * 右上角菜单（⋮）：启动 / 停止 / 重启目标 App，可在菜单里切换「使用 Root 执行」
 * （per-app 开关 app_control_root，停止/重启需要 Root）。
 *
 * 所有配置写在该 App 自己的 SP（文件名 = 包名），与注入侧 appPrefs 对齐。
 *
 * @param pkg           目标 App 包名
 * @param appLabel      顶部标题用
 * @param onOpenCategory 点击某个分类入口 → 打开该分类详情页（key）
 */
@Composable
fun AppFunctionListScreen(
    pkg: String,
    appLabel: String,
    onBack: () -> Unit,
    onOpenCategory: (String) -> Unit,
    onOpenRewriteRules: () -> Unit = {},
    onOpenDexPaths: () -> Unit = {},
    onOpenConsole: () -> Unit = {},
    onOpenFridaScripts: () -> Unit = {},
    onPickActivity: (String) -> Unit = {},
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
            // ===== 分类目录式功能列表 =====
            // 每个大类折叠成一个入口（点击进详情页）；六大扩展分类直接平铺在本层。
            // 详情页由 CategoryActivity 渲染（PrefRegistry.byKey 查 key）。
            val items = listOf(
                Pref.Header("功能"),
                Pref.Action("go_basic", "基础功能", summary = "全局对话框取消 / 禁止退出 / 禁用 Toast", icon = PrefRegistry.basic.icon, onClick = { onOpenCategory("basic") }),
                Pref.Action("go_dialog", "对话框", summary = "增强取消 / 禁用对话框 / 关键字拦截", icon = PrefRegistry.dialog.icon, onClick = { onOpenCategory("dialog") }),
                Pref.Action("go_button", "按钮", summary = "强制显示 / 强制启用 / 自动点击", icon = PrefRegistry.button.icon, onClick = { onOpenCategory("button") }),
                Pref.Action("go_activity", "活动界面", summary = "重定向入口 / 禁用 / 强制结束", icon = PrefRegistry.activity.icon, onClick = { onOpenCategory("activity") }),
                Pref.Action("go_rewrite_monitor", "高级功能", summary = "方法重写引擎 / 命令监控 / 算法监控", icon = PrefRegistry.rewriteMonitor.icon, onClick = { onOpenCategory("rewrite_monitor") }),
                Pref.Action("go_frida", "Frida 注入", summary = "注入脚本 / 监听模式 / 注入日志", icon = PrefRegistry.frida.icon, onClick = { onOpenCategory("frida") }),
                Pref.Header("扩展功能"),
                // 六大扩展分类平铺到本层
                *PrefRegistry.enhanceCategories.drop(3).map { cat ->
                    Pref.Action("go_ext_${cat.key}", cat.title, cat.summary, icon = cat.icon, onClick = { onOpenCategory(cat.key) })
                }.toTypedArray(),
            )
            PrefGroupedColumn(items = items, contentPadding = padding)
        }
    }
}
