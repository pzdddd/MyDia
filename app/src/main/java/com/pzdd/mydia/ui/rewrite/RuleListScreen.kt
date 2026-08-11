package com.pzdd.mydia.ui.rewrite

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.pzdd.mydia.module.rewrite.Rule
import com.pzdd.mydia.ui.DiaScaffold
import com.pzdd.mydia.ui.miuix.MiuixSwitch
import com.pzdd.mydia.ui.miuix.MiuixTextButton
import java.util.UUID

/**
 * 组内规则列表页。
 *
 * 顶部 FAB 新增规则（手动填类名/方法名）。
 * 每张卡片展示 className#methodName + signature + 改写动作数 + 启停开关。
 * 点击卡片进入 [RuleEditScreen]。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleListScreen(
    pkg: String,
    groupId: String,
    onBack: () -> Unit,
    onOpenRule: (String) -> Unit,
    onOpenClassTree: () -> Unit = {},
) {
    val context = LocalContext.current
    val repo = remember(pkg) { RewriteRuleRepository(context, pkg) }
    val rules = remember { mutableStateListOf<Rule>() }
    var groupName by remember { mutableStateOf("") }

    // 每次进入（含从 RuleEditScreen 返回）重新加载
    LaunchedEffect(pkg, groupId) {
        val group = repo.findGroup(groupId)
        groupName = group?.name ?: ""
        rules.clear()
        rules.addAll(group?.rules ?: emptyList())
    }

    var showAddDialog by remember { mutableStateOf(false) }

    DiaScaffold(
        title = groupName.ifEmpty { "规则列表" },
        onBack = onBack,
        actions = {
            IconButton(onClick = onOpenClassTree) {
                Icon(Icons.Filled.Search, contentDescription = "类树选择方法")
            }
            IconButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "新增规则")
            }
        },
    ) { padding ->
        if (rules.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    "暂无规则\n点击右上角 + 新建（或用类树浏览器选择方法）",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    top = padding.calculateTopPadding(),
                    bottom = padding.calculateBottomPadding(),
                ),
            ) {
                items(rules, key = { it.id }) { rule ->
                    RuleCard(
                        rule = rule,
                        onClick = { onOpenRule(rule.id) },
                        onToggle = { enabled ->
                            // copy() 替换触发重组（mutableStateList 不感知元素内部字段变化）
                            val idx = rules.indexOfFirst { it.id == rule.id }
                            if (idx >= 0) {
                                rules[idx] = rule.copy(enabled = enabled)
                                saveRule(repo, groupId, rules[idx])
                            }
                        },
                        onDelete = {
                            val g = repo.findGroup(groupId) ?: return@RuleCard
                            g.rules.removeAll { it.id == rule.id }
                            repo.upsertGroup(g)
                            rules.remove(rule)
                        },
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddRuleDialog(
            onConfirm = { className, methodName, signature ->
                val rule = Rule(
                    id = UUID.randomUUID().toString(),
                    className = className,
                    methodName = methodName,
                    signature = signature,
                    enabled = true,
                )
                val g = repo.findGroup(groupId) ?: return@AddRuleDialog
                g.rules.add(rule)
                repo.upsertGroup(g)
                rules.add(rule)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false },
        )
    }
}

/** 把单条规则的改动写回所属规则组并持久化。 */
private fun saveRule(repo: RewriteRuleRepository, groupId: String, rule: Rule) {
    val g = repo.findGroup(groupId) ?: return
    val idx = g.rules.indexOfFirst { it.id == rule.id }
    if (idx >= 0) g.rules[idx] = rule
    repo.upsertGroup(g)
}

@Composable
private fun RuleCard(
    rule: Rule,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${rule.className.substringAfterLast('.').ifEmpty { "?" }}#${rule.methodName.ifEmpty { "?" }}",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = rule.className.ifEmpty { "（未设置类名）" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = buildString {
                        append(rule.signature.ifEmpty { "(无签名)" })
                        append(" · ${rule.rewrites.size} 个改写")
                        if (rule.bypass) append(" · bypass")
                        if (rule.isConstructor) append(" · 构造")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            MiuixSwitch(checked = rule.enabled, onCheckedChange = onToggle)
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun AddRuleDialog(
    onConfirm: (className: String, methodName: String, signature: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var className by remember { mutableStateOf("") }
    var methodName by remember { mutableStateOf("") }
    var signature by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建规则（手动）") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = className,
                    onValueChange = { className = it },
                    label = { Text("类全限定名") },
                    placeholder = { Text("如 com.foo.Bar") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = methodName,
                    onValueChange = { methodName = it },
                    label = { Text("方法名") },
                    placeholder = { Text("如 showAds") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = signature,
                    onValueChange = { signature = it },
                    label = { Text("签名（仅显示用，可留空）") },
                    placeholder = { Text("如 ()Z") },
                    singleLine = true,
                )
                Text(
                    "提示：更精准的方式是在规则编辑页用「类树浏览器」选择方法。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        },
        confirmButton = {
            MiuixTextButton(
                text = "确定",
                onClick = {
                    if (className.isNotBlank() && methodName.isNotBlank()) {
                        onConfirm(className.trim(), methodName.trim(), signature.trim())
                    }
                },
            )
        },
        dismissButton = { MiuixTextButton(text = "取消", onClick = onDismiss) },
    )
}
