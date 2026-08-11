package com.pzdd.mydia.ui

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.pzdd.mydia.module.RemotePrefsSync
import com.pzdd.mydia.ui.prefs.chmodPref
import com.pzdd.mydia.ui.theme.MyDiaAppTheme
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import com.pzdd.mydia.module.hook.extras.ActivityEntryList
import com.pzdd.mydia.ui.miuix.MiuixSearchBar
import com.pzdd.mydia.ui.miuix.MiuixTextButton
import com.pzdd.mydia.ui.prefs.rememberAppSp

/**
 * Activity 列表选择器（per-app）。
 *
 * intent extra "pkg" = 目标 App 包名，"mode" = single(选一个写 app_activity_select) /
 * multi(多选写 disable_activity_select，逗号分隔)。
 * 用 PackageManager 列出目标 App 的全部 Activity，点击选中写回 SP（chmod + syncRemote）。
 *
 * 代替原来「手动输入 Activity 全类名」：从列表里挑，避免手打错。
 */
class ActivityListActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pkg = intent.getStringExtra("pkg") ?: ""
        val mode = intent.getStringExtra("mode") ?: "single"
        enableEdgeToEdge()
        setContent {
            MyDiaAppTheme {
                ActivityListScreen(pkg = pkg, mode = mode, onBack = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityListScreen(pkg: String, mode: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val sp = rememberAppSp(pkg)
    // 存 Activity 类名字符串（注入侧枚举 / UI 侧 PackageManager 都转成类名，统一类型）
    val activities = remember { mutableStateListOf<String>() }
    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }

    // 当前已选（single=单值；multi=逗号分隔集）
    val singleKey = "app_activity_select"
    val multiKey = "disable_activity_select"
    val selected = remember { mutableStateListOf<String>() }

    var defaultLauncher by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(pkg) {
        // 优先读注入侧枚举的 Activity 列表（ActivityListHook 写 remote：无需进软件、已就绪）
        val remoteList = runCatching {
            com.pzdd.mydia.module.ActivationManager.service?.getRemotePreferences(pkg)
                ?.getString("activity_list", null)
        }.getOrNull()
        val parsed = remoteList?.let { json ->
            runCatching {
                com.google.gson.Gson().fromJson(json, ActivityEntryList::class.java)
            }.getOrNull()
        }

        if (parsed != null && parsed.activities.isNotEmpty()) {
            // 注入侧枚举结果：launcher 置顶 + 其余按名排序
            defaultLauncher = parsed.launcher
            val launcher = parsed.activities.filter { it == defaultLauncher }
            val rest = parsed.activities.filterNot { it == defaultLauncher }
            activities.clear()
            activities.addAll(launcher + rest.sorted())
        } else {
            // 回退：UI 侧 PackageManager 枚举（remote 未就绪 / 未注入时）
            val info = runCatching {
                context.packageManager.getPackageInfo(pkg, PackageManager.GET_ACTIVITIES)
            }.getOrNull()
            val acts = info?.activities?.map { it.name }?.distinct()?.toMutableList() ?: mutableListOf()
            defaultLauncher = runCatching {
                context.packageManager.getLaunchIntentForPackage(pkg)?.component?.className
            }.getOrNull()
            val launcher = acts.filter { it == defaultLauncher }.toMutableList()
            val rest = acts.filterNot { it == defaultLauncher }
            activities.clear()
            activities.addAll(launcher + rest.sorted())
        }

        // 载入已选
        selected.clear()
        if (mode == "single") {
            sp.getString(singleKey, "")?.takeIf { it.isNotBlank() }?.let { selected.add(it) }
        } else {
            sp.getString(multiKey, "")?.split(",", "，")?.map { it.trim() }
                ?.filter { it.isNotEmpty() }?.let { selected.addAll(it) }
        }
        loading = false
    }

    fun save() {
        val key = if (mode == "single") singleKey else multiKey
        val value = if (mode == "single") selected.firstOrNull() ?: "" else selected.joinToString(",")
        sp.edit().putString(key, value).commit()
        chmodPref(sp)
        RemotePrefsSync.syncLocal(sp)
    }

    val filtered = activities.filter {
        query.isBlank() || it.contains(query.trim(), ignoreCase = true) ||
            (it.substringAfterLast('.').contains(query.trim(), ignoreCase = true))
    }

    DiaScaffold(
        title = if (mode == "single") "选择入口 Activity" else "选择禁用 Activity",
        onBack = onBack,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            MiuixSearchBar(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                placeholder = "搜索 Activity",
            )
            Text(
                text = if (mode == "single") "点击选中一个（当前：${selected.firstOrNull() ?: "未选"}）"
                else "已选 ${selected.size} 个，点确定保存",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            if (loading) {
                Text("加载中…", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(filtered, key = { it }) { name ->
                        val isSel = name in selected
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 3.dp)
                                .clickable {
                                    if (mode == "single") {
                                        selected.clear()
                                        selected.add(name)
                                        save()
                                    } else {
                                        if (isSel) selected.remove(name) else selected.add(name)
                                    }
                                },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSel) MaterialTheme.colorScheme.surfaceContainerHigh
                            else MaterialTheme.colorScheme.surface,
                            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                if (mode == "multi") {
                                    Checkbox(checked = isSel, onCheckedChange = {
                                        if (it) selected.add(name) else selected.remove(name)
                                    })
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        name.substringAfterLast('.'),
                                        style = MaterialTheme.typography.bodyLarge,
                                        maxLines = 1,
                                    )
                                    Text(
                                        name + if (name == defaultLauncher) " · launcher" else "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                    )
                                }
                                if (mode == "single" && isSel) {
                                    Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }
            if (mode == "multi") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    MiuixTextButton(text = "保存（${selected.size}）", onClick = { save(); onBack() })
                }
            }
        }
    }
}
