package com.pzdd.mydia.ui.prefs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 分组渲染模型。把扁平的 [Pref] 列表整理成「分区卡片」结构（对齐 pznote 设置页）：
 *
 *  - 每个 [Pref.Header] 开启一个新分区，标题【内嵌在卡片顶部】（titleSmall SemiBold primary）
 *  - 卡片 = surface 底 + 16dp 圆角 + 无边框（pznote 的 SettingsSectionCard 风格）
 *  - 分区内的普通项（无 dependency）平铺在卡片里，用内嵌细线分隔
 *  - 连续且 dependency 相同的子项聚成一个 [Row.SubGroup]，渲染成卡片内的嵌套容器
 *
 * 【关键】整体用 Column + verticalScroll（对齐 pznote），**不用 LazyColumn**：
 * 展开/收回时高度变化自然流动，没有 LazyColumn item 级 re-layout 推挤下方配置项
 * （这是之前展开动画一直「影响到下边」的根源）。
 * 配置页项数有限（几十项），Column + verticalScroll 性能无碍。
 */

/** 一个分区：标题（可选）+ 若干行。 */
internal data class Section(
    val header: Pref.Header?,
    val rows: List<Row>,
)

/** 分区内的一行：要么是单个普通项，要么是一组依赖同一开关的子项。 */
internal sealed class Row {
    data class Single(val pref: Pref) : Row()
    data class SubGroup(val depKey: String, val items: List<Pref>) : Row()
}

/** 把扁平 [Pref] 列表按 Header 切分成 [Section] 列表。 */
internal fun buildSections(items: List<Pref>): List<Section> {
    val result = mutableListOf<Section>()
    var currentHeader: Pref.Header? = null
    val current = mutableListOf<Pref>()

    fun flush() {
        if (currentHeader != null || current.isNotEmpty()) {
            result.add(Section(currentHeader, groupRows(current)))
        }
        current.clear()
    }

    for (item in items) {
        if (item is Pref.Header) {
            flush()
            currentHeader = item
        } else {
            current.add(item)
        }
    }
    flush()
    return result
}

/** 把同一分区里的项聚成 [Row]：dependency 相同的连续项归一个 [Row.SubGroup]。 */
private fun groupRows(items: List<Pref>): List<Row> {
    val rows = mutableListOf<Row>()
    var i = 0
    while (i < items.size) {
        val dep = items[i].dependencyKey
        if (dep != null) {
            val group = mutableListOf<Pref>()
            while (i < items.size && items[i].dependencyKey == dep) {
                group.add(items[i]); i++
            }
            rows.add(Row.SubGroup(dep, group))
        } else {
            rows.add(Row.Single(items[i])); i++
        }
    }
    return rows
}

/**
 * 分组卡片列表。供 [PrefScreenView] 与 [AppFunctionListScreen] 共用。
 *
 * 调用方需在外层用 `CompositionLocalProvider(LocalPrefs provides sp)` 注入 SP。
 */
@Composable
fun PrefGroupedColumn(
    items: List<Pref>,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val sections = remember(items) { buildSections(items) }
    // Column + verticalScroll（对齐 pznote 设置页）：展开动画时高度自然流动，无 LazyColumn 推挤
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        sections.forEach { section ->
            SectionCard(section)
        }
    }
}

/** 一张圆角卡片（对齐 pznote SettingsSectionCard）：标题内嵌顶部，surface 底 + 16dp 圆角。 */
@Composable
private fun SectionCard(section: Section) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 标题内嵌卡片顶部（pznote 风格：titleSmall SemiBold primary）
            if (section.header != null) {
                Text(
                    text = section.header.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            section.rows.forEachIndexed { idx, row ->
                when (row) {
                    is Row.Single -> PrefItemView(row.pref)
                    is Row.SubGroup -> SubGroupContainer(row)
                }
                if (idx != section.rows.lastIndex) {
                    InsetDivider()
                }
            }
        }
    }
}

/** 镶嵌进卡片内的嵌套容器：依赖开关开启时整体带动画滑出，视觉上「嵌」在父卡片里。 */
@Composable
private fun SubGroupContainer(group: Row.SubGroup) {
    val depOn = rememberBoolPref(group.depKey, false).value
    // 显式「从上到下平铺」：expandVertically(Alignment.Top) 让内容从顶部向下铺开。
    // 注意不能用默认 enter（expandIn 会水平+垂直同时展开 + scale，在窄卡片里表现为
    // 「从左到右」）。外层是 Column + verticalScroll，垂直展开时下方内容自然让位。
    AnimatedVisibility(
        visible = depOn,
        enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
        exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            shape = RoundedCornerShape(12.dp),
            // 嵌套容器用 surfaceContainerHigh（浅色浅灰 #E8E8E8 / 深色 #2D2D2D）：
            // 卡片已改为白色（浅色下），surfaceVariant 也是白色会重叠看不清。
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(modifier = Modifier.padding(vertical = 2.dp)) {
                group.items.forEachIndexed { idx, pref ->
                    PrefItemView(pref, nested = true)
                    if (idx != group.items.lastIndex) {
                        InsetDivider(start = 28.dp)
                    }
                }
            }
        }
    }
}

/** 卡片内行间分隔线（对齐 pznote：onSurface 8% 透明细线）。 */
@Composable
private fun InsetDivider(start: androidx.compose.ui.unit.Dp = 16.dp) {
    HorizontalDivider(
        modifier = Modifier.padding(start = start, end = 0.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
    )
}
