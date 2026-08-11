package com.pzdd.mydia.ui.miuix

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * MIUI 风格搜索框。
 *
 * 视觉特征：
 *  - 圆角容器（圆角 24dp，接近胶囊），浅色=白底，深色=深灰底（随主题）
 *  - 左侧搜索图标（灰），输入文字 onSurface
 *  - placeholder 中性灰
 *  - 无下划线、无边框
 *
 * @param value 当前输入文字
 * @param onValueChange 输入回调
 * @param placeholder 占位提示
 * @param modifier 修饰符
 */
@Composable
fun MiuixSearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "搜索",
) {
    val cs = MaterialTheme.colorScheme
    // 深色模式下搜索框背景是深灰，在深色页面上不突出——加一圈亮色描边
    // 让它可见（浅色保持无边框，视觉不变）。
    val dark = cs.surface.luminance() < 0.5f
    val borderModifier = if (dark) {
        Modifier.border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(24.dp))
    } else {
        Modifier
    }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(cs.surfaceVariant, RoundedCornerShape(24.dp))
            .then(borderModifier),
        singleLine = true,
        textStyle = TextStyle(
            color = cs.onSurface,
            fontSize = 15.sp,
        ),
        cursorBrush = SolidColor(cs.primary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    tint = cs.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                // placeholder / 内容
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        color = cs.onSurfaceVariant.copy(alpha = 0.7f),
                        fontSize = 15.sp,
                    )
                } else {
                    innerTextField()
                }
            }
        },
    )
}
