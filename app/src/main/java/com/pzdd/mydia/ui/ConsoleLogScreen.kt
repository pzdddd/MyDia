package com.pzdd.mydia.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.pzdd.mydia.monitor.ConsoleLogStore
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 远程日志控制台（Compose 版）。
 *
 * 注入侧 [com.pzdd.mydia.module.hook.RemoteConsoleLogHook] 把模块日志广播回
 * [com.pzdd.mydia.monitor.ConsoleLogReceiver] → [ConsoleLogStore]。
 * 本页每秒刷新，展示被注入 App 里模块的运行日志（无需 logcat）。
 */
@Composable
fun ConsoleLogScreen(onBack: () -> Unit) {
    val logs by produceState(ConsoleLogStore.snapshot()) {
        while (true) {
            value = ConsoleLogStore.snapshot()
            delay(1000)
        }
    }

    DiaScaffold(title = "日志控制台", onBack = onBack) { padding ->
        if (logs.isEmpty()) {
            Text(
                text = "暂无日志。\n\n请在「增强模式 → 开发者」打开「远程日志」，\n" +
                    "然后在目标 App 里操作（打开/关闭功能开关），\n" +
                    "这里的日志会实时滚动。",
                modifier = Modifier.padding(padding).padding(16.dp),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 8.dp),
            ) {
                items(logs) { entry ->
                    val ts = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(entry.time))
                    Card(modifier = Modifier.padding(vertical = 4.dp)) {
                        Text(
                            text = "[$ts] [${entry.pkg}] ${entry.msg}",
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
        }
    }
}
