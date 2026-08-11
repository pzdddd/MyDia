package com.pzdd.mydia.ui.rewrite

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.pzdd.mydia.module.rewrite.DexPathStore
import com.pzdd.mydia.module.rewrite.DexParser
import com.pzdd.mydia.module.rewrite.Rule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 类树浏览器。
 *
 * 后台解析 dex 源（可能数万类，必须在 IO 线程），渲染成搜索 + 树状列表。
 * 点击类 → 展开方法列表；点击方法 → 在 [groupId] 下新建 Rule 并回调。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClassTreeScreen(
    pkg: String,
    groupId: String,
    onBack: () -> Unit,
    onMethodSelected: () -> Unit,
) {
    val context = LocalContext.current
    val repo = remember(pkg) { RewriteRuleRepository(context, pkg) }

    val allClasses = remember { mutableStateListOf<DexParser.ClassInfo>() }
    var loading by remember { mutableStateOf(true) }
    var query by remember { mutableStateOf("") }
    val expandedClasses = remember { mutableStateMapOf<String, Boolean>() }

    // 后台加载 dex 源
    LaunchedEffect(pkg) {
        loading = true
        val sp = context.getSharedPreferences(pkg, android.content.Context.MODE_PRIVATE)
        val dexPaths = DexPathStore.load(sp.getString(DexPathStore.KEY, null))
        val files = dexPaths.filter { it.enabled }.map { File(it.path) }.filter { it.isFile }
        val classes = withContext(Dispatchers.IO) { DexParser.parseAll(files) }
        allClasses.clear()
        allClasses.addAll(classes)
        loading = false
    }

    // 搜索过滤（输入时实时过滤，类名包含关键字）
    val filtered = remember(query, allClasses) {
        if (query.isBlank()) allClasses
        else allClasses.filter { it.className.contains(query.trim(), ignoreCase = true) }
    }

    com.pzdd.mydia.ui.DiaScaffold(title = "类树浏览器（${allClasses.size}）", onBack = onBack) { padding ->
        Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("搜索类名") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                singleLine = true,
            )

            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Text("解析 dex 中…", Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                filtered.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(if (query.isBlank()) "无可用 dex 源" else "无匹配类", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(filtered, key = { it.type }) { cls ->
                        val expanded = expandedClasses[cls.type] == true
                        ClassRow(
                            classInfo = cls,
                            expanded = expanded,
                            onClick = { expandedClasses[cls.type] = !expanded },
                            onMethodClick = { method ->
                                // 选定方法 → 新建 Rule 写入该组
                                val rule = Rule(
                                    id = java.util.UUID.randomUUID().toString(),
                                    className = cls.className,
                                    methodName = method.name,
                                    signature = method.signature,
                                    enabled = true,
                                )
                                val g = repo.findGroup(groupId)
                                if (g != null) {
                                    g.rules.add(rule)
                                    repo.upsertGroup(g)
                                }
                                onMethodSelected()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ClassRow(
    classInfo: DexParser.ClassInfo,
    expanded: Boolean,
    onClick: () -> Unit,
    onMethodClick: (DexParser.MethodInfo) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column {
            Text(
                text = classInfo.className,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
            )
            if (expanded) {
                classInfo.methods.forEach { m ->
                    Text(
                        text = "${m.name}${m.signature}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onMethodClick(m) }
                            .padding(start = 32.dp, top = 10.dp, end = 16.dp, bottom = 10.dp),
                    )
                }
                if (classInfo.methods.isEmpty()) {
                    Text(
                        "（无方法）",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(start = 32.dp, bottom = 8.dp),
                    )
                }
            }
        }
    }
}
