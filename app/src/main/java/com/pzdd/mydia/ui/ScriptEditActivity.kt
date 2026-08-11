package com.pzdd.mydia.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.pzdd.mydia.ui.theme.MyDiaAppTheme

/**
 * Frida 脚本全屏查看/编辑页。
 *
 * intent extra "pkg" = 目标 App 包名，"scriptId" = 脚本 id。
 * 全屏展示脚本 JS 内容（等宽字体），顶栏保存写回 SP。
 */
class ScriptEditActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pkg = intent.getStringExtra("pkg") ?: ""
        val scriptId = intent.getStringExtra("scriptId") ?: ""
        enableEdgeToEdge()
        setContent {
            MyDiaAppTheme {
                ScriptEditScreen(pkg = pkg, scriptId = scriptId, onBack = { finish() })
            }
        }
    }
}
