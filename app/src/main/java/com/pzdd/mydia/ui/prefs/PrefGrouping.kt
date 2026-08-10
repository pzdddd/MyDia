package com.pzdd.mydia.ui.prefs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 分组渲染模型。把扁平的 [Pref] 列表整理成「分区卡片」结构，实现 MIUI 风格的设置页：
 *
 *  - 每个 [Pref.Header] 开启一个新分区（小标题 + 一张圆角卡片）
 *  - 分区内的普通项（无 dependency）平铺在卡片里，用内嵌细线分隔
 *  - 连续且 dependency 相同的子项聚成一个 [Row.SubGroup]，
 *    渲染成「镶嵌」进卡片内的嵌套圆角容器（带浅色底）—— 开关一开，子项平滑滑出并嵌在卡片里
 *
 * 这样配置页不再是「一行一条粗分隔线」的旧 Preference 样式，而是 iOS/MIUI 的卡片组。
 */

/** 一个分区：小标题（可选）+ 若干行。 */
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
    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = contentPadding) {
        sections.forEach { section ->
            if (section.header != null) {
                item(key = "h::${section.header.key}") { SectionHeader(section.header) }
            }
            val cardKey = "card::${section.header?.key ?: section.rows.joinToString { rowKey(it) }}"
            item(key = cardKey) {
                SectionCard(section, modifier = Modifier.animateItem())
            }
        }
    }
}

private fun rowKey(row: Row): String = when (row) {
    is Row.Single -> row.pref.key
    is Row.SubGroup -> "sub::${row.depKey}"
}

/** 分区小标题（不带卡片，飘在卡片上方）。 */
@Composable
private fun SectionHeader(header: Pref.Header) {
    androidx.compose.material3.Text(
        text = header.title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 24.dp, top = 16.dp, bottom = 4.dp, end = 16.dp),
    )
}

/** 一张圆角卡片：包含分区里的所有行，行间用内嵌细线分隔；子组以镶嵌容器呈现。 */
@Composable
private fun SectionCard(section: Section, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
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
    AnimatedVisibility(
        visible = depOn,
        enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
        exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            shape = RoundedCornerShape(14.dp),
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

/** 卡片内行间分隔线：左缩进与文字对齐，右贴边。 */
@Composable
private fun InsetDivider(start: androidx.compose.ui.unit.Dp = 16.dp) {
    HorizontalDivider(
        modifier = Modifier.padding(start = start, end = 0.dp),
        thickness = 0.6.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    )
}
