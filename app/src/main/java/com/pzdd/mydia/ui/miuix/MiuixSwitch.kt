package com.pzdd.mydia.ui.miuix

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp

/**
 * MIUI 风格开关（提取自 Miuix `top.yukonga.miuix.kmp.basic.Switch`）。
 *
 * 视觉特征（与 Material3 Switch 区别）：
 *  - 轨道胶囊形 49×28dp，开启时填充强调色（不透明），关闭时灰色半透明
 *  - 滑块白色圆形 20dp，带阴影
 *  - 开关位移动画（checked→25dp，unchecked→4dp）
 *  - 无外围边框，扁平干净
 *
 * @param checked  是否开启
 * @param onCheckedChange 点击回调（null = 只读）
 */
@Composable
fun MiuixSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    // 判断深色：用 surface 亮度，而不是 isSystemInDarkTheme()——
    // 这样用户在「设置」里手动切深色模式时开关颜色也会跟着变。
    val dark = cs.surface.luminance() < 0.5f

    // 轨道色：开启=强调色（明显），关闭=中性灰。
    // 用 surfaceContainerHigh（浅色 #E8E8E8 / 深色 #2D2D2D）而不是 surfaceVariant：
    // surfaceVariant 浅色下是白色，与白色卡片重叠导致开关隐形。
    val trackColor = if (checked) cs.primary else cs.surfaceContainerHigh
    // 滑块色：开启=onPrimary（与轨道对比），关闭=onSurfaceVariant
    val thumbColor = if (checked) cs.onPrimary else cs.onSurfaceVariant

    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 25.dp else 4.dp,
        animationSpec = tween(200),
        label = "thumb_offset",
    )

    Box(
        modifier = modifier
            .width(49.dp)
            .height(28.dp)
            .clip(CircleShape)
            .background(trackColor)
            .then(
                if (onCheckedChange != null) Modifier.clickable { onCheckedChange(!checked) }
                else Modifier
            ),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(20.dp)
                .clip(CircleShape)
                .background(thumbColor),
        )
    }
}
