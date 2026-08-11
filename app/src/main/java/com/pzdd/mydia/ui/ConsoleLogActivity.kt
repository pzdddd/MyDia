package com.pzdd.mydia.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.pzdd.mydia.ui.theme.MyDiaAppTheme

/**
 * 远程日志控制台展示页。
 */
class ConsoleLogActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyDiaAppTheme {
                ConsoleLogScreen(onBack = { finish() })
            }
        }
    }
}
