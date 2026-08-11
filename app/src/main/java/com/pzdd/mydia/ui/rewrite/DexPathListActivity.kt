package com.pzdd.mydia.ui.rewrite

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.pzdd.mydia.ui.theme.MyDiaAppTheme

/**
 * 方法重写 · dex 源管理页（per-app）。
 *
 * intent extra "pkg" = 目标 App 包名。
 * 管理类树浏览器要解析的 dex/apk 源列表（SP key = [DexPathStore.KEY]）。
 * 默认含：捆绑 framework dex + 目标 App 自身 APK + 用户自定义。
 */
class DexPathListActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pkg = intent.getStringExtra("pkg") ?: ""
        enableEdgeToEdge()
        setContent {
            MyDiaAppTheme {
                DexPathListScreen(pkg = pkg, onBack = { finish() })
            }
        }
    }
}
