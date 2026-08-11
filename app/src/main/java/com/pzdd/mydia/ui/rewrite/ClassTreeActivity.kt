package com.pzdd.mydia.ui.rewrite

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.pzdd.mydia.ui.theme.MyDiaAppTheme

/**
 * 方法重写 · 类树浏览器（per-app）。
 *
 * intent extra "pkg"/"groupId"。
 * 解析该 App 的 dex 源，展示类树，用户选类 → 选方法 → 直接在该 [groupId] 下新建 Rule。
 * 选定后 finish 返回 [RuleListScreen]。
 */
class ClassTreeActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pkg = intent.getStringExtra("pkg") ?: ""
        val groupId = intent.getStringExtra("groupId") ?: ""
        enableEdgeToEdge()
        setContent {
            MyDiaAppTheme {
                ClassTreeScreen(
                    pkg = pkg,
                    groupId = groupId,
                    onBack = { finish() },
                    onMethodSelected = { finish() },
                )
            }
        }
    }
}
