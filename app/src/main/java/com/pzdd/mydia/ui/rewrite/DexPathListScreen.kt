package com.pzdd.mydia.ui.rewrite

import android.content.pm.PackageManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pzdd.mydia.module.RemotePrefsSync
import com.pzdd.mydia.module.rewrite.DexPath
import com.pzdd.mydia.module.rewrite.DexPathStore
import com.pzdd.mydia.ui.DiaScaffold
import com.pzdd.mydia.ui.miuix.MiuixSwitch
import com.pzdd.mydia.ui.miuix.MiuixTextButton
import com.pzdd.mydia.ui.prefs.chmodPref
import java.util.UUID

/**
 * dex 源管理页。
 *
 * 启动时自动注入两个默认源（若不存在）：
 *  - 目标 App 自身 APK（isSelf）
 *  - 捆绑 framework dex（isAndroidFramework，第 3 项实现后接入）
 * 用户可追加自定义 dex/apk 路径。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DexPathListScreen(pkg: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val sp = remember(pkg) { context.getSharedPreferences(pkg, android.content.Context.MODE_PRIVATE) }
    val paths = remember { mutableStateListOf<DexPath>() }

    fun save(list: List<DexPath>) {
        sp.edit().putString(DexPathStore.KEY, DexPathStore.toJson(list)).commit()
        chmodPref(sp)
        RemotePrefsSync.syncLocal(sp)
    }

    LaunchedEffect(pkg) {
        val loaded = DexPathStore.load(sp.getString(DexPathStore.KEY, null)).toMutableList()
        // 自动注入目标 App 自身 APK（若没有）
        if (loaded.none { it.isSelf }) {
            val apkPath = runCatching {
                context.packageManager.getApplicationInfo(pkg, 0).sourceDir
            }.getOrNull()
            if (apkPath != null) {
                loaded.add(DexPath(id = "self", alias = "目标 App", path = apkPath, isSelf = true))
            }
        }
        paths.clear()
        paths.addAll(loaded)
        // 持久化注入结果
        save(paths)
    }

    var showAddDialog by remember { mutableStateOf(false) }

    DiaScaffold(
        title = "dex 源管理",
        onBack = onBack,
        actions = {
            IconButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "新增 dex 源")
            }
        },
    ) { padding ->
        if (paths.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("暂无 dex 源", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    top = padding.calculateTopPadding(),
                    bottom = padding.calculateBottomPadding(),
                ),
            ) {
                items(paths, key = { it.id }) { dp ->
                    DexPathCard(
                        dexPath = dp,
                        onToggle = { enabled ->
                            dp.enabled = enabled
                            save(paths)
                        },
                        onDelete = if (dp.isSelf || dp.isAndroidFramework) null else {
                            { paths.remove(dp); save(paths) }
                        },
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        var alias by remember { mutableStateOf("") }
        var path by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("添加 dex/apk 源") },
            text = {
                Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = alias, onValueChange = { alias = it },
                        label = { Text("别名") }, singleLine = true,
                    )
                    OutlinedTextField(
                        value = path, onValueChange = { path = it },
                        label = { Text("文件绝对路径") },
                        placeholder = { Text("如 /sdcard/app.apk") }, singleLine = true,
                    )
                }
            },
            confirmButton = {
                MiuixTextButton(text = "确定", onClick = {
                    if (path.isNotBlank()) {
                        paths.add(DexPath(id = UUID.randomUUID().toString(), alias = alias.trim(), path = path.trim()))
                        save(paths)
                    }
                    showAddDialog = false
                })
            },
            dismissButton = { MiuixTextButton(text = "取消", onClick = { showAddDialog = false }) },
        )
    }
}

@Composable
private fun DexPathCard(
    dexPath: DexPath,
    onToggle: (Boolean) -> Unit,
    onDelete: (() -> Unit)?,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
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
                    text = dexPath.alias.ifEmpty { dexPath.path.substringAfterLast('/') },
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = dexPath.path,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val tag = when {
                    dexPath.isSelf -> "目标 App"
                    dexPath.isAndroidFramework -> "Framework"
                    else -> "自定义"
                }
                Text(tag, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
            MiuixSwitch(checked = dexPath.enabled, onCheckedChange = onToggle)
            if (onDelete != null) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
