package com.pzdd.mydia.ui.prefs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import com.pzdd.mydia.ui.miuix.MiuixSwitch
import com.pzdd.mydia.ui.miuix.MiuixTextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/**
 * 渲染单个 [Pref] 的行内容（不再自行处理 dependency，由 [PrefGroupedColumn] 的分组层统一控制显隐）。
 *
 * @param nested 是否处在镶嵌嵌套容器内（true 时去掉行内的额外圆角，由外层容器统一样式）
 */
@Composable
fun PrefItemView(pref: Pref, modifier: Modifier = Modifier, nested: Boolean = false) {
    when (pref) {
        is Pref.Header -> HeaderRow(pref)
        is Pref.Switch -> SwitchRow(pref, modifier)
        is Pref.EditText -> EditTextRow(pref, modifier)
        is Pref.ListChoice -> ListRow(pref, modifier)
        is Pref.Action -> ActionRow(pref, modifier)
    }
}

@Composable
private fun HeaderRow(pref: Pref.Header) {
    // Header 由 PrefGroupedColumn 的 SectionHeader 统一渲染；保留以防直接调用 PrefItemView。
    androidx.compose.material3.Text(
        text = pref.title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 24.dp, top = 16.dp, bottom = 4.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwitchRow(pref: Pref.Switch, modifier: Modifier = Modifier) {
    val state = rememberBoolPref(pref.key, pref.default)
    val summary = when {
        state.value && pref.summaryOn != null -> pref.summaryOn
        !state.value && pref.summaryOff != null -> pref.summaryOff
        else -> pref.summary
    }
    ListItem(
        headlineContent = { Text(pref.title) },
        supportingContent = summary?.let { { Text(it) } },
        trailingContent = {
            MiuixSwitch(checked = state.value, onCheckedChange = { state.value = it })
        },
        // 整行点击翻转开关：让用户点标题/说明也能切换。
        // 开关本身有自己的 clickable（子级先消费事件），不会双重翻转。
        modifier = modifier.clickable { state.value = !state.value },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditTextRow(pref: Pref.EditText, modifier: Modifier = Modifier) {
    val state = rememberStringPref(pref.key, pref.default)
    var showDialog by remember { mutableStateOf(false) }
    val display = pref.summary ?: state.value.ifEmpty { pref.hint ?: "" }

    ListItem(
        headlineContent = { Text(pref.title) },
        supportingContent = { Text(if (display.isBlank()) "（未设置）" else display) },
        modifier = modifier.clickable { showDialog = true },
    )

    if (showDialog) {
        var text by remember { mutableStateOf(state.value) }
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(pref.title) },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = !pref.multiLine,
                    minLines = if (pref.multiLine) 3 else 1,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (pref.numeric) KeyboardType.Number else KeyboardType.Text,
                    ),
                    placeholder = pref.hint?.let { { Text(it) } },
                )
            },
            confirmButton = {
                MiuixTextButton(
                    text = "确定",
                    onClick = {
                        state.value = text
                        showDialog = false
                    },
                )
            },
            dismissButton = {
                MiuixTextButton(text = "取消", onClick = { showDialog = false })
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ListRow(pref: Pref.ListChoice, modifier: Modifier = Modifier) {
    val state = rememberStringPref(pref.key, pref.default)
    var showDialog by remember { mutableStateOf(false) }
    val currentLabel = pref.entries.firstOrNull { it.second == state.value }?.first ?: pref.summary ?: ""

    ListItem(
        headlineContent = { Text(pref.title) },
        supportingContent = { Text(if (currentLabel.isBlank()) "（未选择）" else currentLabel) },
        modifier = modifier.clickable { showDialog = true },
    )

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(pref.title) },
            text = {
                Column {
                    pref.entries.forEach { (label, value) ->
                        val selected = state.value == value
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(selected = selected, onClick = {
                                    state.value = value
                                    showDialog = false
                                })
                                .padding(vertical = 6.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            RadioButton(selected = selected, onClick = {
                                state.value = value
                                showDialog = false
                            })
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = {
                MiuixTextButton(text = "关闭", onClick = { showDialog = false })
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActionRow(pref: Pref.Action, modifier: Modifier = Modifier) {
    ListItem(
        headlineContent = { Text(pref.title) },
        supportingContent = pref.summary?.let { { Text(it) } },
        leadingContent = pref.icon?.let { { Icon(it, contentDescription = null) } },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
        modifier = modifier.clickable { pref.onClick() },
    )
}

/** 整页分隔线（每个 item 后画一条）。 */
@Composable
fun PrefDivider() {
    HorizontalDivider(modifier = Modifier.padding(horizontal = 0.dp))
}
