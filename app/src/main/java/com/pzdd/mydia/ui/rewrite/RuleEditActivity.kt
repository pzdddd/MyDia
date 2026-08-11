package com.pzdd.mydia.ui.rewrite

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.pzdd.mydia.ui.theme.MyDiaAppTheme

/**
 * 方法重写 · 单条规则编辑页（per-app）。
 *
 * intent extra "pkg"/"groupId"/"ruleId"。
 * 编辑 [com.pzdd.mydia.module.rewrite.Rule] 的目标方法、调试选项、以及挂在其下的
 * 全部 [com.pzdd.mydia.module.rewrite.Rewrite] 改写动作。
 */
class RuleEditActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pkg = intent.getStringExtra("pkg") ?: ""
        val groupId = intent.getStringExtra("groupId") ?: ""
        val ruleId = intent.getStringExtra("ruleId") ?: ""
        enableEdgeToEdge()
        setContent {
            MyDiaAppTheme {
                RuleEditScreen(
                    pkg = pkg,
                    groupId = groupId,
                    ruleId = ruleId,
                    onBack = { finish() },
                )
            }
        }
    }
}
