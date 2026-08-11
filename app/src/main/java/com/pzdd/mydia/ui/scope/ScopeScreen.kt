package com.pzdd.mydia.ui.scope

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pzdd.mydia.module.ActivationManager
import com.pzdd.mydia.module.ScopeManager
import com.pzdd.mydia.ui.AppInfo
import com.pzdd.mydia.ui.DiaScaffold
import com.pzdd.mydia.ui.loadInstalledApps
import com.pzdd.mydia.ui.miuix.MiuixTextButton

/**
 * 作用域管理页。
 *
 * 三块内容：
 *  1. 激活状态（已激活显示框架信息；未激活提示去 LSPosed 启用）
 *  2. 当前作用域 App 列表（每项可移除）
 *  3. 添加 App（弹出已安装列表，选中后 requestScope，LSPosed 会弹确认）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScopeScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val inScope = remember { mutableStateListOf<String>() }
    var activated by remember { mutableStateOf(ActivationManager.service != null) }
    var showAddDialog by remember { mutableStateOf(false) }

    fun refresh() {
        activated = ActivationManager.service != null
        inScope.clear()
        inScope.addAll(ScopeManager.getScope())
    }

    LaunchedEffect(Unit) { refresh() }

    DiaScaffold(
        title = "作用域管理",
        onBack = onBack,
        actions = {
            if (activated) {
                IconButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Filled.Add, contentDescription = "添加 App")
                }
            }
        },
    ) { padding ->
        if (!activated) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    "模块未激活\n请在 LSPosed Manager 里启用 MyDia 模块",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@DiaScaffold
        }
        Column(Modifier.fillMaxSize().padding(padding)) {
            // 激活状态卡
            val svc = ActivationManager.service
            Surface(
                modifier = Modifier.fillMaxWidth().padding(12.dp, 4.dp),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Row(
                    Modifier.padding(16.dp, 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                    Column {
                        Text("已激活", style = MaterialTheme.typography.titleMedium)
                        svc?.let {
                            Text(
                                "${it.frameworkName} (${it.frameworkVersionCode}) · API ${it.apiVersion}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            Text(
                "作用域内的 App（${inScope.size}）",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 24.dp, top = 16.dp, bottom = 4.dp),
            )

            if (inScope.isEmpty()) {
                Text(
                    "暂无作用域 App\n点右上角 + 添加（会请求 LSPosed 确认）",
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(inScope, key = { it }) { pkg ->
                        Surface(
                            modifier = Modifier.fillMaxWidth().padding(12.dp, 2.dp),
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                        ) {
                            Row(
                                Modifier.padding(16.dp, 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(pkg, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                IconButton(onClick = {
                                    ScopeManager.removeScope(listOf(pkg))
                                    inScope.remove(pkg)
                                    Toast.makeText(context, "已移除 $pkg", Toast.LENGTH_SHORT).show()
                                }) {
                                    Icon(Icons.Filled.Delete, "移除", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddScopeDialog(
            currentScope = inScope.toList(),
            onDismiss = { showAddDialog = false },
            onConfirm = { selected ->
                showAddDialog = false
                ScopeManager.requestScope(
                    packages = selected,
                    onApproved = { approved ->
                        refresh()
                        Toast.makeText(context, "已批准 ${approved.size} 个", Toast.LENGTH_SHORT).show()
                    },
                    onFailed = { reason ->
                        Toast.makeText(context, "请求失败：$reason", Toast.LENGTH_LONG).show()
                    },
                )
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddScopeDialog(
    currentScope: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit,
) {
    val context = LocalContext.current
    var loading by remember { mutableStateOf(true) }
    val allApps = remember { mutableStateListOf<AppInfo>() }
    val selected = remember { mutableStateListOf<String>() }

    LaunchedEffect(Unit) {
        loading = true
        allApps.clear()
        allApps.addAll(loadInstalledApps(context, includeSystem = false))
        loading = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加到作用域") },
        text = {
            if (loading) {
                Text("加载已安装应用…")
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
                ) {
                    items(allApps, key = { it.pkg }) { app ->
                        val inScopeAlready = app.pkg in currentScope
                        val isSel = app.pkg in selected
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !inScopeAlready) {
                                    if (isSel) selected.remove(app.pkg) else selected.add(app.pkg)
                                }
                                .padding(horizontal = 4.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = isSel || inScopeAlready,
                                onCheckedChange = if (inScopeAlready) null else {
                                    { if (it) selected.add(app.pkg) else selected.remove(app.pkg) }
                                },
                            )
                            Column {
                                Text(app.label, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    app.pkg + if (inScopeAlready) "（已在作用域）" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            MiuixTextButton(
                text = "请求(${selected.size})",
                onClick = { if (selected.isNotEmpty()) onConfirm(selected.toList()) },
            )
        },
        dismissButton = { MiuixTextButton(text = "取消", onClick = onDismiss) },
    )
}
