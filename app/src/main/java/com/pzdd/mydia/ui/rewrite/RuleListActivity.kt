package com.pzdd.mydia.ui.rewrite

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.pzdd.mydia.ui.theme.MyDiaAppTheme

/**
 * 方法重写 · 组内规则列表页（per-app）。
 *
 * intent extra "pkg" = 目标 App 包名，"groupId" = 规则组 id。
 * 列出该组内的全部 [com.pzdd.mydia.module.rewrite.Rule]，支持新增/删除/启停。
 * 点击规则 → [RuleEditActivity] 编辑该规则的改写动作。
 */
class RuleListActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pkg = intent.getStringExtra("pkg") ?: ""
        val groupId = intent.getStringExtra("groupId") ?: ""
        enableEdgeToEdge()
        setContent {
            MyDiaAppTheme {
                RuleListScreen(
                    pkg = pkg,
                    groupId = groupId,
                    onBack = { finish() },
                    onOpenRule = { ruleId ->
                        startActivity(
                            Intent(this, RuleEditActivity::class.java)
                                .putExtra("pkg", pkg)
                                .putExtra("groupId", groupId)
                                .putExtra("ruleId", ruleId)
                        )
                    },
                    onOpenClassTree = {
                        startActivity(
                            Intent(this, ClassTreeActivity::class.java)
                                .putExtra("pkg", pkg)
                                .putExtra("groupId", groupId)
                        )
                    },
                )
            }
        }
    }
}
