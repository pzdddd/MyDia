package com.pzdd.mydia.ui

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.pzdd.mydia.ui.miuix.MiuixSearchBar
import com.pzdd.mydia.ui.miuix.MiuixSwitch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 应用页：加载全部已安装 App，每行一个开关（per-app 总开关），点击进入功能列表。
 *
 * 开关状态写在以包名命名的 SP 文件 `<pkg>` 的 `enabled` key 里，
 * 与注入侧 [com.pzdd.mydia.module.Module.appPrefs] 完全对齐。
 *
 * @param onOpenApp 点击 App 行的回调，参数 = 包名（进入功能列表）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsScreen(
    contentPadding: PaddingValues,
    onOpenApp: (String) -> Unit,
) {
    val context = LocalContext.current
    var includeSystem by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    val allApps = remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    // pkg -> enabled：内存中的开关状态（UI 响应式）
    val switchState = remember { mutableStateMapOf<String, Boolean>() }

    LaunchedEffect(includeSystem) {
        loading = true
        val list = loadInstalledApps(context, includeSystem)
        allApps.value = list
        list.forEach { switchState[it.pkg] = it.enabled }
        loading = false
    }

    // 过滤 + 排序：启用的应用始终排在列表最前（各自内部按字母序）
    val filtered by remember(allApps.value, query) {
        derivedStateOf {
            val q = query.trim().lowercase()
            val list = if (q.isEmpty()) allApps.value
            else allApps.value.filter { it.label.lowercase().contains(q) || it.pkg.lowercase().contains(q) }
            list.sortedWith(compareByDescending<AppInfo> { switchState[it.pkg] == true }.thenBy { it.label.lowercase() })
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
        // 搜索框（MIUI 圆角白底）
        MiuixSearchBar(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            placeholder = "搜索应用名 / 包名",
        )
        // 系统应用过滤
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = !includeSystem,
                onClick = { includeSystem = false },
                label = { Text("仅第三方", color = MaterialTheme.colorScheme.onSurface) },
            )
            FilterChip(
                selected = includeSystem,
                onClick = { includeSystem = true },
                label = { Text("含系统", color = MaterialTheme.colorScheme.onSurface) },
            )
            Spacer(Modifier.weight(1f))
            Text(
                "${switchState.count { it.value }} / ${allApps.value.size} 启用",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 12.dp),
            )
        }
        Spacer(Modifier.height(4.dp))

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text("正在加载应用列表…")
                }
            }
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(filtered, key = { it.pkg }) { app ->
                // 注：应用列表可能很长（几十上百项），每行都用液态玻璃（drawBackdrop）
                // 会在 RenderThread 上同时渲染大量 native RenderEffect → SIGSEGV 崩溃。
                // 所以这里用普通半透明卡片（视觉有层次，但不触发 native 模糊）。
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                    ),
                ) {
                    AppRow(
                        app = app,
                        enabled = switchState[app.pkg] ?: false,
                        onToggle = { v ->
                            val sp = context.getSharedPreferences(app.pkg, Context.MODE_PRIVATE)
                            // commit 同步落盘后 chmod 0644：注入侧 XSharedPreferences 才能读
                            sp.edit().putBoolean("enabled", v).commit()
                            com.pzdd.mydia.ui.prefs.chmodPref(sp)
                            // 同步到 LSPosed remote：注入侧常驻进程立刻读到（无需重启目标 App）
                            com.pzdd.mydia.module.RemotePrefsSync.syncLocal(sp)
                            switchState[app.pkg] = v
                        },
                        onClick = { onOpenApp(app.pkg) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppRow(
    app: AppInfo,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    var iconBmp by remember(app.pkg) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(app.pkg) {
        iconBmp = withContext(Dispatchers.IO) {
            loadAppIcon(context, app.pkg)?.let { drawableToBitmap(it).asImageBitmap() }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // 图标
        Box(
            modifier = Modifier.size(44.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (iconBmp != null) {
                Image(iconBmp!!, contentDescription = null, modifier = Modifier.size(44.dp))
            } else {
                Box(
                    Modifier.size(44.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp)
                        )
                )
            }
        }
        // 名称 + 包名
        Column(modifier = Modifier.weight(1f)) {
            Text(
                app.label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                app.pkg,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // per-app 开关
        MiuixSwitch(checked = enabled, onCheckedChange = onToggle)
    }
}
