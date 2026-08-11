package com.pzdd.mydia.ui.rewrite

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.pzdd.mydia.module.rewrite.Rewrite
import com.pzdd.mydia.module.rewrite.Rule
import com.pzdd.mydia.ui.DiaScaffold
import com.pzdd.mydia.ui.miuix.MiuixSwitch
import com.pzdd.mydia.ui.miuix.MiuixTextButton

/**
 * 单条规则编辑页。
 *
 * 三大区块（对齐 Dia 的 RuleActivity PreferenceScreen）：
 *  1. 目标方法：className / methodName / signature / isConstructor
 *  2. 改写动作列表：每个 Rewrite 改一个入参（index>=0）或返回值（index=-1）
 *  3. 调试选项：bypass / printLog / trace / dumpHprof / simulationPackageName
 *
 * 所有改动实时保存（写后三连：commit/chmod/syncRemote）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleEditScreen(
    pkg: String,
    groupId: String,
    ruleId: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val repo = remember(pkg) { RewriteRuleRepository(context, pkg) }
    val rule = remember { mutableStateOf<Rule?>(null) }
    val rewrites = remember { mutableStateListOf<Rewrite>() }

    LaunchedEffect(pkg, groupId, ruleId) {
        val g = repo.findGroup(groupId)
        val r = g?.rules?.firstOrNull { it.id == ruleId }
        rule.value = r
        rewrites.clear()
        rewrites.addAll(r?.rewrites ?: emptyList())
    }

    /** 把当前编辑状态写回 Rule 并持久化。 */
    fun persist() {
        val r = rule.value ?: return
        r.rewrites.clear()
        r.rewrites.addAll(rewrites)
        val g = repo.findGroup(groupId) ?: return
        val idx = g.rules.indexOfFirst { it.id == ruleId }
        if (idx >= 0) g.rules[idx] = r
        repo.upsertGroup(g)
    }

    val current = rule.value
    DiaScaffold(
        title = "编辑规则",
        onBack = {
            persist()
            onBack()
        },
    ) { padding ->
        if (current == null) {
            Text("规则不存在", Modifier.padding(padding).padding(16.dp))
            return@DiaScaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding(),
            ),
        ) {
            // ===== 区块 1：目标方法 =====
            item {
                SectionLabel("目标方法")
                EditCard {
                    EditableFieldRow("类全限定名", current.className) {
                        current.className = it; rule.value = current.copy(); persist()
                    }
                    InsetDivider()
                    EditableFieldRow("方法名", current.methodName) {
                        current.methodName = it; rule.value = current.copy(); persist()
                    }
                    InsetDivider()
                    EditableFieldRow("签名（仅显示用）", current.signature) {
                        current.signature = it; rule.value = current.copy(); persist()
                    }
                    InsetDivider()
                    ToggleRow("构造方法", "hook 构造函数而非普通方法", current.isConstructor) {
                        current.isConstructor = it; rule.value = current.copy(); persist()
                    }
                    InsetDivider()
                    ToggleRow("启用", "", current.enabled) {
                        current.enabled = it; rule.value = current.copy(); persist()
                    }
                }
            }

            // ===== 区块 2：改写动作 =====
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 24.dp, top = 16.dp, end = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SectionLabel("改写动作（${rewrites.size}）", withPadding = false)
                    IconButton(onClick = {
                        rewrites.add(Rewrite(index = -1))  // 默认改返回值
                        persist()
                    }) { Icon(Icons.Filled.Add, contentDescription = "新增改写") }
                }
            }
            itemsIndexed(rewrites, key = { i, _ -> i }) { idx, rw ->
                RewriteCard(
                    rewrite = rw,
                    onRemove = { rewrites.removeAt(idx); persist() },
                )
            }

            // ===== 区块 3：调试选项 =====
            item {
                SectionLabel("调试选项")
                EditCard {
                    ToggleRow("Bypass", "直接拦截返回，不执行原方法", current.bypass) {
                        current.bypass = it; rule.value = current.copy(); persist()
                    }
                    InsetDivider()
                    ToggleRow("打印调用日志", "命中时输出参数/返回值", current.printLog) {
                        current.printLog = it; rule.value = current.copy(); persist()
                    }
                    InsetDivider()
                    ToggleRow("打印调用栈", "", current.printLogStackTrace) {
                        current.printLogStackTrace = it; rule.value = current.copy(); persist()
                    }
                    InsetDivider()
                    ToggleRow("方法追踪", "", current.trace) {
                        current.trace = it; rule.value = current.copy(); persist()
                    }
                }
            }
        }
    }
}

// ---------- 可复用的小组件 ----------

@Composable
private fun SectionLabel(text: String, withPadding: Boolean = true) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = if (withPadding) Modifier.padding(start = 24.dp, top = 16.dp, bottom = 4.dp) else Modifier,
    )
}

@Composable
private fun EditCard(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(vertical = 4.dp)) { content() }
    }
}

@Composable
private fun InsetDivider() {
    androidx.compose.material3.HorizontalDivider(
        modifier = Modifier.padding(start = 16.dp),
        thickness = 0.6.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    )
}

@Composable
private fun EditableFieldRow(label: String, value: String, onChange: (String) -> Unit) {
    var showDialog by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().clickable { showDialog = true }.padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(
                value.ifEmpty { "（未设置）" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (showDialog) {
        var text by remember { mutableStateOf(value) }
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(label) },
            text = { OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true) },
            confirmButton = {
                MiuixTextButton(text = "确定", onClick = { onChange(text); showDialog = false })
            },
            dismissButton = { MiuixTextButton(text = "取消", onClick = { showDialog = false }) },
        )
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        MiuixSwitch(checked = checked, onCheckedChange = onChange)
    }
}

// ---------- 单个 Rewrite 改写动作卡片 ----------

@Composable
private fun RewriteCard(
    rewrite: Rewrite,
    onRemove: () -> Unit,
) {
    var showEditDialog by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clickable { showEditDialog = true },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (rewrite.index < 0) "改返回值" else "改第 ${rewrite.index} 个参数",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = "${typeName(rewrite.type)} · ${rewrite.classType.ifEmpty { "无类型" }}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
                Text(
                    text = if (rewrite.replace == Rewrite.NAN) "（未启用）" else "→ ${truncate(rewrite.replace, 30)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (showEditDialog) {
        RewriteEditDialog(
            rewrite = rewrite,
            onConfirm = { showEditDialog = false },
            onDismiss = { showEditDialog = false },
        )
    }
}

@Composable
private fun RewriteEditDialog(
    rewrite: Rewrite,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    var indexText by remember { mutableStateOf(rewrite.index.toString()) }
    var selectedType by remember { mutableStateOf(rewrite.type) }
    var classType by remember { mutableStateOf(rewrite.classType) }
    var match by remember { mutableStateOf(if (rewrite.match == Rewrite.NAN) "" else rewrite.match) }
    var replace by remember { mutableStateOf(if (rewrite.replace == Rewrite.NAN) "" else rewrite.replace) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑改写动作") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = indexText,
                    onValueChange = { indexText = it },
                    label = { Text("参数序号（-1 = 返回值）") },
                    singleLine = true,
                )
                Text("类型", style = MaterialTheme.typography.labelLarge)
                TypeOptions(selectedType) { selectedType = it }
                OutlinedTextField(
                    value = classType,
                    onValueChange = { classType = it },
                    label = { Text("smali 类型（如 Ljava/lang/String; 或 I）") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = match,
                    onValueChange = { match = it },
                    label = { Text("匹配条件（留空 = 无条件替换）") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = replace,
                    onValueChange = { replace = it },
                    label = { Text("替换值") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            MiuixTextButton(text = "确定", onClick = {
                rewrite.index = indexText.toIntOrNull() ?: 0
                rewrite.type = selectedType
                rewrite.classType = classType.trim()
                rewrite.match = match.ifBlank { Rewrite.NAN }
                rewrite.replace = replace.ifBlank { Rewrite.NAN }
                onConfirm()
            })
        },
        dismissButton = { MiuixTextButton(text = "取消", onClick = onDismiss) },
    )
}

@Composable
private fun TypeOptions(selected: Int, onSelect: (Int) -> Unit) {
    val options = listOf(
        Rewrite.TYPE_STRING to "String",
        Rewrite.TYPE_NUMBER to "Number",
        Rewrite.TYPE_BOOLEAN to "Boolean",
        Rewrite.TYPE_OBJECT to "Object",
        Rewrite.TYPE_VOID to "Void",
        Rewrite.TYPE_BYTES to "Bytes",
    )
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            val isSel = value == selected
            Surface(
                shape = RoundedCornerShape(50),
                color = if (isSel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.clickable { onSelect(value) },
            ) {
                Text(
                    label,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

private fun typeName(type: Int): String = when (type) {
    Rewrite.TYPE_STRING -> "String"
    Rewrite.TYPE_NUMBER -> "Number"
    Rewrite.TYPE_BOOLEAN -> "Boolean"
    Rewrite.TYPE_OBJECT -> "Object"
    Rewrite.TYPE_VOID -> "Void"
    Rewrite.TYPE_BYTES -> "Bytes"
    else -> "?"
}

private fun truncate(s: String, max: Int): String =
    if (s.length <= max) s else s.take(max) + "…"
