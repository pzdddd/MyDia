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
import com.pzdd.mydia.module.rewrite.RuleGroup
import com.pzdd.mydia.ui.DiaScaffold
import com.pzdd.mydia.ui.miuix.MiuixSwitch
import com.pzdd.mydia.ui.miuix.MiuixTextButton
import java.util.UUID

/**
 * 规则组列表页。
 *
 * 顶部 FAB 新增组，每张卡片展示组名/描述/规则数/启停开关，
 * 点击卡片进入 [RuleListScreen] 编辑组内规则，右侧删除按钮删除组。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleGroupListScreen(
    pkg: String,
    onBack: () -> Unit,
    onOpenGroup: (String) -> Unit,
) {
    val context = LocalContext.current
    val repo = remember(pkg) { RewriteRuleRepository(context, pkg) }
    val groups = remember { mutableStateListOf<RuleGroup>() }
    // 每次进入页面重新加载（从 RuleListScreen 返回时能看到最新数据）
    LaunchedEffect(pkg) { groups.clear(); groups.addAll(repo.loadGroups()) }

    var showAddDialog by remember { mutableStateOf(false) }

    DiaScaffold(
        title = "方法重写规则",
        onBack = onBack,
        actions = {
            IconButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "新增规则组")
            }
        },
    ) { padding ->
        if (groups.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    "暂无规则组\n点击右上角 + 新建",
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
                items(groups, key = { it.id }) { group ->
                    RuleGroupCard(
                        group = group,
                        onClick = { onOpenGroup(group.id) },
                        onToggle = { enabled ->
                            // copy() 替换触发重组（mutableStateList 不感知元素内部字段变化）
                            val idx = groups.indexOfFirst { it.id == group.id }
                            if (idx >= 0) {
                                groups[idx] = group.copy(enabled = enabled)
                                repo.upsertGroup(groups[idx])
                            }
                        },
                        onDelete = { repo.deleteGroup(group.id); groups.remove(group) },
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        EditGroupDialog(
            title = "新建规则组",
            initialName = "",
            initialDesc = "",
            onConfirm = { name, desc ->
                val g = RuleGroup(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    desc = desc,
                    priority = groups.size,  // 默认按创建顺序排
                )
                repo.upsertGroup(g)
                groups.add(g)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false },
        )
    }
}

@Composable
private fun RuleGroupCard(
    group: RuleGroup,
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
                    text = group.name.ifEmpty { "未命名规则组" },
                    style = MaterialTheme.typography.titleMedium,
                )
                if (group.desc.isNotEmpty()) {
                    Text(
                        text = group.desc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = "${group.rules.count { it.enabled }}/${group.rules.size} 条规则生效 · 优先级 ${group.priority}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            MiuixSwitch(checked = group.enabled, onCheckedChange = onToggle)
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun EditGroupDialog(
    title: String,
    initialName: String,
    initialDesc: String,
    onConfirm: (name: String, desc: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var desc by remember { mutableStateOf(initialDesc) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("组名") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text("描述（可选）") },
                    minLines = 2,
                )
            }
        },
        confirmButton = {
            MiuixTextButton(
                text = "确定",
                onClick = { if (name.isNotBlank()) onConfirm(name.trim(), desc.trim()) },
            )
        },
        dismissButton = { MiuixTextButton(text = "取消", onClick = onDismiss) },
    )
}
