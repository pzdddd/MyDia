package com.pzdd.mydia.ui.scope

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.pzdd.mydia.ui.theme.MyDiaTheme

/**
 * LSPosed 作用域管理页。
 *
 * 展示当前激活状态、模块作用域里的 App 列表（可移除），
 * 以及添加 App 入口（从已安装列表选，调用 requestScope 触发 LSPosed 确认）。
 *
 * 依赖 [com.pzdd.mydia.module.ActivationManager.service]；未激活时提示用户先启用模块。
 */
class ScopeActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyDiaTheme {
                ScopeScreen(onBack = { finish() })
            }
        }
    }
}
