package com.pzdd.mydia.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pzdd.mydia.monitor.ConsoleLogStore
import com.pzdd.mydia.monitor.LogCategory
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 远程日志控制台（Compose 版）。
 *
 * 注入侧 [com.pzdd.mydia.module.hook.RemoteConsoleLogHook] 把模块日志广播回
 * [com.pzdd.mydia.monitor.ConsoleLogReceiver] → [ConsoleLogStore]。
 * 本页每秒刷新，展示被注入 App 里模块的运行日志（无需 logcat）。
 *
 * 顶部分类 tab（全部/注入/对话框/按钮/反检测/模拟/监控/Frida/其它），
 * 按 [LogCategory] 过滤 + 计数；日志行左缘分类色条便于区分。
 */
@Composable
fun ConsoleLogScreen(onBack: () -> Unit) {
    val logs by produceState(ConsoleLogStore.snapshot()) {
        while (true) {
            value = ConsoleLogStore.snapshot()
            delay(1000)
        }
    }
    var selected by remember { mutableStateOf<LogCategory?>(null) }  // null = 全部
    val context = LocalContext.current

    // 保存日志：SAF 让用户选保存位置（txt）
    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null) {
            val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val text = buildString {
                logs.forEach { e ->
                    val t = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(e.time))
                    append("[$t] [${e.pkg}] [${e.category.label}] ${e.msg}\n")
                }
            }
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
                Toast.makeText(context, "日志已保存", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(context, "保存失败：${it.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // 分类计数
    val counts = remember(logs) {
        val map = LogCategory.entries.associateWith { 0 }.toMutableMap()
        logs.forEach { map[it.category] = (map[it.category] ?: 0) + 1 }
        map
    }
    val filtered = if (selected == null) logs else logs.filter { it.category == selected }

    DiaScaffold(
        title = "日志控制台（${logs.size}）",
        onBack = onBack,
        actions = {
            // 保存日志（SAF 选位置写 txt）
            IconButton(onClick = { saveLauncher.launch("mydia_log_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.txt") }) {
                Icon(Icons.Filled.Save, contentDescription = "保存日志")
            }
            // 清除日志
            IconButton(onClick = { ConsoleLogStore.clear() }) {
                Icon(Icons.Filled.Delete, contentDescription = "清除日志")
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // 分类 tab
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item {
                    CategoryChip(label = "全部", count = logs.size, selected = selected == null) { selected = null }
                }
                items(LogCategory.entries.toList()) { cat ->
                    CategoryChip(label = cat.label, count = counts[cat] ?: 0, selected = selected == cat) {
                        selected = cat
                    }
                }
            }

            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (logs.isEmpty()) {
                            "暂无日志。\n\n请在「设置 → 日志」打开「远程日志」，\n" +
                                "然后在目标 App 里操作（打开/关闭功能开关），\n" +
                                "这里的日志会实时滚动。"
                        } else {
                            "该分类暂无日志"
                        },
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp),
                ) {
                    items(filtered) { entry ->
                        val ts = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(entry.time))
                        val accent = categoryColor(entry.category)
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surface,
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                // 左缘分类色条
                                Box(
                                    modifier = Modifier
                                        .padding(end = 8.dp)
                                        .height(18.dp)
                                        .background(accent, RoundedCornerShape(3.dp)),
                                ) {}
                                Text(
                                    text = "[$ts] [${entry.pkg}] ${entry.msg}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryChip(label: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text("$label $count") },
    )
}

/** 分类 → 强调色（日志左缘色条）。 */
private fun categoryColor(cat: LogCategory): Color = when (cat) {
    LogCategory.Inject -> Color(0xFF3482FF)      // 蓝
    LogCategory.Dialog -> Color(0xFF7B61FF)      // 紫
    LogCategory.Button -> Color(0xFF00BFA5)      // 青
    LogCategory.AntiDetection -> Color(0xFFF4511E) // 橙红
    LogCategory.Fake -> Color(0xFFFFA726)        // 橙
    LogCategory.Monitor -> Color(0xFF66BB6A)     // 绿
    LogCategory.Frida -> Color(0xFFEC407A)       // 粉
    LogCategory.Other -> Color(0xFF9E9E9E)       // 灰
}
