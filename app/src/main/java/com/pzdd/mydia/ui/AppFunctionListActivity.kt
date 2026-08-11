package com.pzdd.mydia.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.pzdd.mydia.ui.theme.MyDiaTheme

/**
 * 某 App 的功能列表页（从「应用」Tab 点击某个 App 进入）。
 *
 * intent extra "pkg" = 目标 App 包名。
 * 展示：基础全局对话框取消（直接开关） + 增强模式入口。
 */
class AppFunctionListActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pkg = intent.getStringExtra("pkg") ?: ""
        val label = runCatching { packageManager.getPackageInfo(pkg, 0).applicationInfo }
            .getOrNull()?.let { packageManager.getApplicationLabel(it).toString() } ?: pkg
        enableEdgeToEdge()
        setContent {
            MyDiaTheme {
                AppFunctionListScreen(
                    pkg = pkg,
                    appLabel = label,
                    onBack = { finish() },
                    onOpenEnhance = {
                        startActivity(
                            Intent(this, EnhanceActivity::class.java).putExtra("pkg", pkg)
                        )
                    },
                    onOpenRewriteRules = {
                        startActivity(
                            Intent(this, com.pzdd.mydia.ui.rewrite.RuleGroupListActivity::class.java)
                                .putExtra("pkg", pkg)
                        )
                    },
                    onOpenDexPaths = {
                        startActivity(
                            Intent(this, com.pzdd.mydia.ui.rewrite.DexPathListActivity::class.java)
                                .putExtra("pkg", pkg)
                        )
                    },
                )
            }
        }
    }
}
