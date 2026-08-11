package com.pzdd.mydia.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.pzdd.mydia.ui.theme.MyDiaAppTheme

/**
 * Frida 注入脚本管理页（per-app）。
 *
 * intent extra "pkg" = 目标 App 包名。
 * 通过系统文件选择器（SAF）选 .js 脚本，读内容存入该 App 的 SP（frida_scripts），
 * 每个脚本可开关控制（enabled），支持多条脚本。
 */
class FridaScriptListActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pkg = intent.getStringExtra("pkg") ?: ""
        enableEdgeToEdge()
        setContent {
            MyDiaAppTheme {
                FridaScriptListScreen(pkg = pkg, onBack = { finish() })
            }
        }
    }
}
