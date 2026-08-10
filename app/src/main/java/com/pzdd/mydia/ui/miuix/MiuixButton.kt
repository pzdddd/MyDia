package com.pzdd.mydia.ui.miuix

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * MIUI 风格填充按钮。
 *
 * 视觉特征（对照 Material3 Button）：
 *  - 强调色（primary）填充背景，白色文字
 *  - 圆角 12dp（Material3 默认 full / 20dp，MIUI 偏中等圆角）
 *  - 无阴影、无水波纹
 *  - 水平 padding 24dp、垂直 10dp
 *  - 字号 15sp Medium
 *
 * @param text 按钮文字
 * @param onClick 点击回调
 * @param enabled 是否可用（不可用时降为半透明灰）
 */
@Composable
fun MiuixButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 24.dp, vertical = 10.dp),
) {
    val cs = MaterialTheme.colorScheme
    val bg = if (enabled) cs.primary else cs.surfaceVariant
    val fg = if (enabled) cs.onPrimary else cs.onSurfaceVariant
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .background(bg, RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
            ) { onClick() }
            .padding(contentPadding),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            color = fg,
            fontWeight = FontWeight.Medium,
            style = MaterialTheme.typography.titleSmall,
        )
    }
}

/**
 * MIUI 风格文字按钮（无边框、无背景、纯强调色文字）。
 *
 * 用于对话框「确定 / 取消」等次级操作。点击有微弱反馈。
 */
@Composable
fun MiuixTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val cs = MaterialTheme.colorScheme
    val fg = if (enabled) cs.primary else cs.onSurfaceVariant.copy(alpha = 0.5f)
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
            ) { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            color = fg,
            fontWeight = FontWeight.Medium,
            style = MaterialTheme.typography.titleSmall,
        )
    }
}
