package com.pzdd.mydia.ui

import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pzdd.mydia.module.RemotePrefsSync
import com.pzdd.mydia.module.hook.FridaScriptStore
import com.pzdd.mydia.ui.prefs.chmodPref

/**
 * 脚本全屏查看/编辑页。
 *
 * 顶栏：返回 + 保存。内容区全屏等宽字体文本域（代码编辑器感）+ JS 语法高亮。
 * 保存：写回该 App 的 SP（frida_scripts），chmod + syncRemote 后返回。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScriptEditScreen(
    pkg: String,
    scriptId: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val sp = remember(pkg) { context.getSharedPreferences(pkg, android.content.Context.MODE_PRIVATE) }
    // 深色判断（与项目惯例一致）：surface 亮度 < 0.5 视为深色 → 高亮用深色配色
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    var name by remember { mutableStateOf("") }
    var source by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(pkg, scriptId) {
        val s = FridaScriptStore.load(sp.getString(FridaScriptStore.KEY, null))
            .firstOrNull { it.id == scriptId }
        if (s != null) {
            name = s.name
            source = s.source
        }
        loaded = true
    }

    fun save() {
        val list = FridaScriptStore.load(sp.getString(FridaScriptStore.KEY, null)).toMutableList()
        val idx = list.indexOfFirst { it.id == scriptId }
        if (idx >= 0) {
            list[idx] = list[idx].copy(name = name, source = source)
            sp.edit().putString(FridaScriptStore.KEY, FridaScriptStore.toJson(list)).commit()
            chmodPref(sp)
            RemotePrefsSync.syncLocal(sp)
            Toast.makeText(context, "脚本已保存", Toast.LENGTH_SHORT).show()
            onBack()
        }
    }

    DiaScaffold(
        title = name.ifEmpty { "脚本" },
        onBack = onBack,
        actions = {
            IconButton(onClick = { if (loaded) save() }) {
                Icon(Icons.Filled.Save, contentDescription = "保存")
            }
        },
    ) { padding ->
        OutlinedTextField(
            value = source,
            onValueChange = { source = it },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 4.dp, vertical = 4.dp),
            textStyle = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurface,
            ),
            // JS 语法高亮（深浅配色随主题）
            visualTransformation = remember(dark) { JsSyntaxHighlighter(dark) },
            // 全屏代码编辑：占满、可滚动、无边框感
            shape = MaterialTheme.shapes.extraSmall,
        )
    }
}
