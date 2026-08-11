package com.pzdd.mydia.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.pzdd.mydia.ui.theme.MyDiaAppTheme

/**
 * 某 App 的功能列表页（从「应用」Tab 点击某个 App 进入）。
 *
 * intent extra "pkg" = 目标 App 包名。
 * 分类目录式：每个分类入口点击 → CategoryActivity 渲染该分类详情页。
 */
class AppFunctionListActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pkg = intent.getStringExtra("pkg") ?: ""
        val label = runCatching { packageManager.getPackageInfo(pkg, 0).applicationInfo }
            .getOrNull()?.let { packageManager.getApplicationLabel(it).toString() } ?: pkg
        enableEdgeToEdge()
        setContent {
            MyDiaAppTheme {
                AppFunctionListScreen(
                    pkg = pkg,
                    appLabel = label,
                    onBack = { finish() },
                    onOpenCategory = { cat ->
                        startActivity(
                            Intent(this, CategoryActivity::class.java)
                                .putExtra("pkg", pkg)
                                .putExtra("cat", cat)
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
                    onOpenConsole = {
                        startActivity(Intent(this, ConsoleLogActivity::class.java))
                    },
                    onOpenFridaScripts = {
                        startActivity(
                            Intent(this, FridaScriptListActivity::class.java).putExtra("pkg", pkg)
                        )
                    },
                    onPickActivity = { mode ->
                        startActivity(
                            Intent(this, ActivityListActivity::class.java)
                                .putExtra("pkg", pkg)
                                .putExtra("mode", mode)
                        )
                    },
                )
            }
        }
    }
}
