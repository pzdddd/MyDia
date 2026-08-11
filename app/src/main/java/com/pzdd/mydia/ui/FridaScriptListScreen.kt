package com.pzdd.mydia.ui

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Description
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pzdd.mydia.module.RemotePrefsSync
import com.pzdd.mydia.module.hook.FridaScript
import com.pzdd.mydia.module.hook.FridaScriptStore
import com.pzdd.mydia.ui.miuix.MiuixSwitch
import com.pzdd.mydia.ui.prefs.chmodPref
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Frida 脚本管理页：文件选择器选 .js → 读内容存 SP；每条脚本可开关/删除。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FridaScriptListScreen(pkg: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val sp = remember(pkg) { context.getSharedPreferences(pkg, android.content.Context.MODE_PRIVATE) }
    val scripts = remember { mutableStateListOf<FridaScript>() }

    LaunchedEffect(pkg) {
        scripts.clear()
        scripts.addAll(FridaScriptStore.load(sp.getString(FridaScriptStore.KEY, null)))
    }

    fun save(list: List<FridaScript>) {
        sp.edit().putString(FridaScriptStore.KEY, FridaScriptStore.toJson(list)).commit()
        chmodPref(sp)
        RemotePrefsSync.syncLocal(sp)
    }

    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // SAF 文件选择器：选 .js 后读内容
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            loading = true
            scope.launch {
                val name = withContext(Dispatchers.IO) {
                    queryFileName(context, uri) ?: uri.lastPathSegment?.substringAfterLast('/') ?: "script.js"
                }
                val content = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                }
                if (content != null) {
                    scripts.add(FridaScript(id = java.util.UUID.randomUUID().toString(), name = name, source = content))
                    save(scripts)
                }
                loading = false
            }
        }
    }

    DiaScaffold(
        title = "注入脚本（${scripts.size}）",
        onBack = onBack,
        actions = {
            IconButton(onClick = { filePicker.launch(arrayOf("application/javascript", "text/javascript", "text/plain", "*/*")) }) {
                Icon(Icons.Filled.Add, contentDescription = "选择脚本文件")
            }
        },
    ) { padding ->
        if (loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("读取脚本中…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@DiaScaffold
        }
        if (scripts.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    "暂无注入脚本\n点右上角 + 从文件管理器选择 .js 文件",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@DiaScaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding(),
            ),
        ) {
            items(scripts, key = { it.id }) { script ->
                ScriptCard(
                    script = script,
                    onClick = {
                        // 全屏查看/编辑：启动 ScriptEditActivity
                        context.startActivity(
                            android.content.Intent(context, ScriptEditActivity::class.java)
                                .putExtra("pkg", pkg)
                                .putExtra("scriptId", script.id)
                        )
                    },
                    onToggle = { enabled ->
                        // 必须用 copy() 替换元素：mutableStateList 不感知元素内部字段变化，
                        // 直接改 script.enabled 不会触发重组（开关看起来没反应）。
                        val idx = scripts.indexOfFirst { it.id == script.id }
                        if (idx >= 0) {
                            scripts[idx] = script.copy(enabled = enabled)
                            save(scripts)
                        }
                    },
                    onDelete = { scripts.remove(script); save(scripts) },
                )
            }
        }
    }
}

@Composable
private fun ScriptCard(
    script: FridaScript,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Filled.Description, null, tint = MaterialTheme.colorScheme.primary)
            Column(modifier = Modifier.weight(1f)) {
                Text(script.name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    "JS · ${script.source.length} 字符 · 点击编辑",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            MiuixSwitch(checked = script.enabled, onCheckedChange = onToggle)
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

/** 从 SAF uri 查文件名（DISPLAY_NAME）。 */
private fun queryFileName(context: android.content.Context, uri: Uri): String? =
    runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
        }
    }.getOrNull()
