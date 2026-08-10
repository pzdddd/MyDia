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
import com.pzdd.mydia.monitor.MonitorLogStore
import kotlinx.coroutines.delay

/**
 * 算法监控日志展示页（Compose 版）。
 *
 * 注入侧 [com.pzdd.mydia.algorithm.AlgorithmHookManager] hook 到加解密调用后，
 * 广播回 [com.pzdd.mydia.monitor.AlgorithmMonitorReceiver] → [MonitorLogStore]。
 * 本页每秒刷新一次。
 */
@Composable
fun AlgorithmLogScreen(onBack: () -> Unit) {
    val logs by produceState(MonitorLogStore.snapshot()) {
        while (true) {
            value = MonitorLogStore.snapshot()
            delay(1000)
        }
    }

    DiaScaffold(title = "算法监控日志", onBack = onBack) { padding ->
        if (logs.isEmpty()) {
            Text(
                text = "暂无数据。\n\n请在「主界面 → 算法监控」打开开关，\n" +
                    "然后在目标 App 里触发一次加解密 / 签名 / Base64。",
                modifier = Modifier.padding(padding).padding(16.dp),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 8.dp),
            ) {
                items(logs) { rec ->
                    Card(modifier = Modifier.padding(vertical = 4.dp)) {
                        Text(
                            text = rec.format(),
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
