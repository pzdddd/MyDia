package com.pzdd.mydia.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * MIUI 风格形状（大圆角，卡片 16dp、对话框 24dp、按钮全圆角）。
 */
val MiuixShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),    // 卡片
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp), // 底部表单 / 对话框
)
