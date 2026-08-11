package com.pzdd.mydia.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pzdd.mydia.CrashCatcher
import com.pzdd.mydia.ui.miuix.MiuixNavBar
import com.pzdd.mydia.ui.miuix.MiuixTextButton
import com.pzdd.mydia.ui.prefs.LocalPrefs
import com.pzdd.mydia.ui.prefs.rememberBoolPref
import com.pzdd.mydia.ui.prefs.rememberGlobalSp
import com.pzdd.mydia.ui.prefs.rememberStringPref
import com.pzdd.mydia.ui.theme.MyDiaTheme
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.blur.materials.HazeMaterials
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

/**
 * 主界面：底部 3 个 Tab（首页 / 应用 / 设置），顶栏 + 底栏均为液态玻璃。
 *
 * - **首页**：模块激活状态（[HomeScreen]）
 * - **应用**：列出全部已安装 App，每行 per-app 开关，点击进入功能列表（[AppsScreen]）
 * - **设置**：全局配置（[SettingsScreen]）
 *
 * 液态玻璃效果（blur + lens 折射）由 backdrop 库提供，可在「设置 → 显示 → 液态玻璃」开关。
 * 主题模式（跟随系统 / 浅色 / 深色）与动态取色同样在「设置 → 显示」。
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 读取上次崩溃栈（CrashCatcher 落盘），有则弹窗显示帮助定位闪退原因
        val lastCrash = CrashCatcher.consume(this)
        setContent {
            // 读全局 SP：UI 显示设置（ui_theme / ui_dynamic_color / ui_blur）
            val sp = rememberGlobalSp()
            CompositionLocalProvider(LocalPrefs provides sp) {
                val theme = rememberStringPref("ui_theme", "system").value
                val dynamic = rememberBoolPref("ui_dynamic_color", false).value
                val glass = rememberBoolPref("ui_blur", true).value
                val dark = when (theme) {
                    "light" -> false
                    "dark" -> true
                    else -> isSystemInDarkTheme() // system
                }
                CompositionLocalProvider(LocalGlassEnabled provides glass) {
                    MyDiaTheme(darkTheme = dark, dynamicColor = dynamic) {
                        MainRoot(
                            onOpenApp = { pkg ->
                                startActivity(
                                    Intent(this, AppFunctionListActivity::class.java)
                                        .putExtra("pkg", pkg)
                                )
                            },
                            onOpenConsole = {
                                startActivity(Intent(this, ConsoleLogActivity::class.java))
                            },
                            onOpenScope = {
                                startActivity(Intent(this, com.pzdd.mydia.ui.scope.ScopeActivity::class.java))
                            },
                            initialCrash = lastCrash,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MainRoot(
    onOpenApp: (String) -> Unit,
    onOpenConsole: () -> Unit,
    onOpenScope: () -> Unit,
    initialCrash: String? = null,
) {
    var showCrash by remember { mutableStateOf(initialCrash != null) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = remember {
        listOf(
            "首页" to Icons.Filled.Home,
            "应用" to Icons.AutoMirrored.Filled.List,
            "设置" to Icons.Filled.Settings,
        )
    }

    // 液态玻璃 backdrop：在最外层 remember 一次。
    // 内容层挂 layerBackdrop(backdrop) 捕获页面；底栏在内容外（兄弟节点），
    // 通过 drawBackdrop(backdrop) 引用同一个源 → 能透出页面内容且不增加渲染树深度（不崩）。
    // 顶栏仍用 Haze（独立需求）。结构参考 pznote 的 AppRoot。
    val cs = MaterialTheme.colorScheme
    val glassEnabled = LocalGlassEnabled.current
    val hazeState = rememberHazeState()
    val backdrop = rememberLayerBackdrop()

    Scaffold { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            // 内容层：layerBackdrop（backdrop 源）+ Haze 源（顶栏毛玻璃）
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .layerBackdrop(backdrop)
                    .hazeSource(hazeState),
            ) {
                when (tab) {
                    0 -> HomeScreen(padding)
                    1 -> AppsScreen(padding, onOpenApp = onOpenApp)
                    2 -> SettingsScreen(padding, onOpenConsole = onOpenConsole, onOpenScope = onOpenScope)
                }
            }

            // 顶栏：只在首页显示，毛玻璃（Haze）+ 底部圆角
            if (tab == 0) {
                val topShape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp)
                val topMod = if (glassEnabled) {
                    Modifier
                        .clip(topShape)
                        .hazeBlur(
                            input = HazeInput.Sources(hazeState),
                            style = HazeMaterials.ultraThin(),
                        )
                } else {
                    Modifier.background(cs.surface.copy(alpha = 0.7f), topShape)
                }
                Box(modifier = Modifier.fillMaxWidth().then(topMod)) {
                    Text(
                        text = "MyDia",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                    )
                }
            }

            // 底部导航栏：悬浮液态玻璃胶囊。在内容外（兄弟节点），
            // MiuixNavBar 内部用 drawBackdrop(backdrop) 透出页面内容（真液态玻璃，不崩）。
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 19.dp)
                    .padding(bottom = 12.dp),
            ) {
                MiuixNavBar(
                    items = tabs,
                    selected = tab,
                    onSelect = { tab = it },
                    backdrop = backdrop,
                )
            }
        }
    }

    // 崩溃报告弹窗（CrashCatcher 捕获的上次闪退栈）
    if (showCrash && initialCrash != null) {
        CrashReportDialog(
            trace = initialCrash,
            onDismiss = { showCrash = false },
        )
    }
}

/** 显示上次崩溃栈，帮助无 logcat 环境定位闪退原因。 */
@Composable
private fun CrashReportDialog(
    trace: String,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("上次崩溃报告") },
        text = {
            Column {
                Text(
                    "App 上次闪退。崩溃栈如下（也可到「设置」→ 应用详情查看 logcat）：",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.padding(4.dp))
                Text(
                    text = trace,
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = MaterialTheme.typography.bodySmall.fontSize,
                    ),
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(top = 4.dp),
                )
            }
        },
        confirmButton = {
            MiuixTextButton(text = "已阅", onClick = onDismiss)
        },
    )
}
