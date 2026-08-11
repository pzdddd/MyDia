package com.pzdd.mydia.ui.rewrite

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.pzdd.mydia.ui.theme.MyDiaAppTheme

/**
 * 方法重写 · 规则组列表页（per-app）。
 *
 * intent extra "pkg" = 目标 App 包名。
 * 列出该 App 的全部 [com.pzdd.mydia.module.rewrite.RuleGroup]，支持增/删/改/排序/启停。
 * 点击组 → [RuleListActivity] 编辑组内规则。
 */
class RuleGroupListActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pkg = intent.getStringExtra("pkg") ?: ""
        enableEdgeToEdge()
        setContent {
            MyDiaAppTheme {
                RuleGroupListScreen(
                    pkg = pkg,
                    onBack = { finish() },
                    onOpenGroup = { groupId ->
                        startActivity(
                            Intent(this, RuleListActivity::class.java)
                                .putExtra("pkg", pkg)
                                .putExtra("groupId", groupId)
                        )
                    },
                )
            }
        }
    }
}
