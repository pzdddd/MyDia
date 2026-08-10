package com.pzdd.mydia.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.pzdd.mydia.ui.theme.MyDiaTheme

/**
 * 通用分类详情页（per-app）。9 个分类共用。
 *
 * intent extra "pkg" = 目标 App 包名，"cat" = 分类 key。
 */
class CategoryActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pkg = intent.getStringExtra("pkg") ?: ""
        val cat = intent.getStringExtra("cat") ?: "dialog"
        enableEdgeToEdge()
        setContent {
            MyDiaTheme {
                CategoryScreen(pkg = pkg, cat = cat, onBack = { finish() })
            }
        }
    }
}
