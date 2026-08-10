package com.pzdd.mydia.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.pzdd.mydia.ui.theme.MyDiaTheme

/**
 * 增强模式目录页（per-app）。intent extra "pkg" = 目标 App 包名。
 *
 * 含 [mod_ex] 总开关 + 全部 9 个分类入口。点击分类跳 [CategoryActivity]。
 */
class EnhanceActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pkg = intent.getStringExtra("pkg") ?: ""
        enableEdgeToEdge()
        setContent {
            MyDiaTheme {
                EnhanceScreen(
                    pkg = pkg,
                    onBack = { finish() },
                    onOpenCategory = { cat ->
                        startActivity(
                            Intent(this, CategoryActivity::class.java)
                                .putExtra("pkg", pkg)
                                .putExtra("cat", cat)
                        )
                    },
                )
            }
        }
    }
}
