package com.pzdd.mydia.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.pzdd.mydia.ui.theme.MyDiaTheme

/**
 * 算法监控日志展示页（Compose 版）。每秒刷新 [com.pzdd.mydia.monitor.MonitorLogStore]。
 */
class AlgorithmLogActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyDiaTheme {
                AlgorithmLogScreen(onBack = { finish() })
            }
        }
    }
}
