package com.pzdd.mydia.ui.miuix

import androidx.compose.ui.graphics.Color

/**
 * MIUI 配色色值（提取自 Miuix 源码 `top.yukonga.miuix.kmp.theme` 的 lightColorScheme）。
 *
 * 设计语言：
 *  - 强调色 [Primary] = MIUI 蓝（#3482FF），用于开关、按钮、选中态
 *  - 浅色模式背景纯白、surface 微灰（#F7F7F7），深色模式背景 #242424
 *  - 分隔线极淡（#E0E0E0 / #393939），符合 MIUI 克制的视觉
 */
object MiuixColors {
    // ---- 浅色模式 ----
    val LightPrimary = Color(0xFF3482FF)
    val LightOnPrimary = Color.White
    val LightPrimaryContainer = Color(0xFF5D9BFF)
    val LightOnPrimaryContainer = Color.White
    val LightError = Color(0xFFE94634)
    val LightOnError = Color.White

    val LightBackground = Color.White
    val LightOnBackground = Color.Black
    val LightOnBackgroundVariant = Color(0xFF8C93B0)

    val LightSurface = Color(0xFFF7F7F7)         // 列表/卡片底
    /** 页面背景（比 surface 深一档，让白色卡片浮现） */
    val LightSurfaceDeep = Color(0xFFE8E8E8)
    val LightOnSurface = Color.Black
    val LightSurfaceVariant = Color.White
    val LightSurfaceContainer = Color.White        // 卡片
    val LightSurfaceContainerHigh = Color(0xFFE8E8E8)
    val LightSurfaceContainerHighest = Color(0xFFE8E8E8)
    // 加深次要文字色：原来 0xFF959595 太浅（非动态取色时显灰），
    // 改为深灰，让未选中 tab / 副标题在不开动态取色时也清晰。
    val LightOnSurfaceVariant = Color(0xFF333333)
    val LightOnSurfaceVariantSummary = Color(0x99000000)   // 副标题
    val LightOnSurfaceVariantActions = Color(0x66000000)   // 操作态

    val LightOutline = Color(0xFFD9D9D9)
    val LightDivider = Color(0xFFE0E0E0)
    val LightSecondaryContainer = Color(0xFFF0F0F0)
    val LightOnSecondaryContainer = Color(0xFFA9A9A9)
    val LightTertiaryContainer = Color(0xFFEAF2FF)  // 选中高亮底
    val LightWindowDimming = Color.Black.copy(alpha = 0.3f)

    // MIUI 开关专属
    val LightSwitchDisabledTrack = Color(0xFFC2D9FF)
    val LightSwitchDisabledThumb = Color(0xFFF3F8FF)

    // ---- 深色模式 ----
    val DarkPrimary = Color(0xFF277AF7)
    val DarkOnPrimary = Color.White
    val DarkPrimaryContainer = Color(0xFF338FE4)
    val DarkOnPrimaryContainer = Color.White
    val DarkError = Color(0xFFF12522)
    val DarkOnError = Color.White

    val DarkBackground = Color(0xFF242424)
    val DarkOnBackground = Color(0xE6FFFFFF)
    val DarkOnBackgroundVariant = Color(0xFF787E96)

    val DarkSurface = Color.Black
    val DarkOnSurface = Color(0xFFF2F2F2)
    val DarkSurfaceVariant = Color(0xFF242424)
    val DarkSurfaceContainer = Color(0xFF242424)
    val DarkSurfaceContainerHigh = Color(0xFF242424)
    val DarkSurfaceContainerHighest = Color(0xFF2D2D2D)
    val DarkOnSurfaceVariant = Color(0xFF737373)
    val DarkOnSurfaceVariantSummary = Color(0x80FFFFFF)
    val DarkOnSurfaceVariantActions = Color(0x66FFFFFF)

    val DarkOutline = Color(0xFF404040)
    val DarkDivider = Color(0xFF393939)
    val DarkSecondaryContainer = Color(0xFF434343)
    val DarkOnSecondaryContainer = Color(0xFF7C7C7C)
    val DarkTertiaryContainer = Color(0xFF2B3B54)
    val DarkWindowDimming = Color.Black.copy(alpha = 0.5f)

    val DarkSwitchDisabledTrack = Color(0xFF253E64)
    val DarkSwitchDisabledThumb = Color(0xFF677993)
}
