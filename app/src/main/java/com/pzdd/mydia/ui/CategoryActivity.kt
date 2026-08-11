package com.pzdd.mydia.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.pzdd.mydia.ui.theme.MyDiaAppTheme

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
            MyDiaAppTheme {
                CategoryScreen(
                    pkg = pkg,
                    cat = cat,
                    onBack = { finish() },
                    onPickActivity = { mode ->
                        startActivity(
                            Intent(this, ActivityListActivity::class.java)
                                .putExtra("pkg", pkg)
                                .putExtra("mode", mode)
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
                    onOpenFridaScripts = {
                        startActivity(
                            Intent(this, FridaScriptListActivity::class.java).putExtra("pkg", pkg)
                        )
                    },
                    onOpenConsole = {
                        startActivity(Intent(this, ConsoleLogActivity::class.java))
                    },
                    onOpenAlgorithmLog = {
                        startActivity(Intent(this, AlgorithmLogActivity::class.java))
                    },
                )
            }
        }
    }
}
