package com.pzdd.mydia.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.pzdd.mydia.ui.miuix.MiuixColors as Miuix

/**
 * MIUI 风格主题。
 *
 * 配色提取自 Miuix 源码（top.yukonga.miuix.kmp.theme）的真实色值，
 * 映射成 Material3 [colorScheme]，这样所有 Material3 组件自动套用 MIUI 配色，
 * 无需引入整个 Miuix KMP 库（节省体积 + 编译稳定）。
 *
 * - Android 12+ 默认跟随系统动态色彩（Material You），可在设置里关掉用纯 MIUI 配色；
 * - 深色模式跟随系统；
 * - 组件层用 [com.pzdd.mydia.ui.miuix.MiuixSwitch] 等 MIUI 风格组件进一步还原。
 *
 * 色值对照见 [MiuixColors]。
 */
@Composable
fun MyDiaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,   // 默认关闭动态色彩，用纯 MIUI 配色
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) androidx.compose.material3.dynamicDarkColorScheme(context)
            else androidx.compose.material3.dynamicLightColorScheme(context)
        }
        darkTheme -> miuixDarkColorScheme()
        else -> miuixLightColorScheme()
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = MiuixTypography,
        shapes = MiuixShapes,
        content = content,
    )
}

/** MIUI 浅色配色 → Material3 ColorScheme。 */
private fun miuixLightColorScheme() = lightColorScheme(
    primary = Miuix.LightPrimary,
    onPrimary = Miuix.LightOnPrimary,
    primaryContainer = Miuix.LightPrimaryContainer,
    onPrimaryContainer = Miuix.LightOnPrimaryContainer,
    secondary = Miuix.LightPrimary,
    onSecondary = Miuix.LightOnPrimary,
    secondaryContainer = Miuix.LightSecondaryContainer,
    onSecondaryContainer = Miuix.LightOnSecondaryContainer,
    tertiary = Miuix.LightPrimary,
    tertiaryContainer = Miuix.LightTertiaryContainer,
    error = Miuix.LightError,
    onError = Miuix.LightOnError,
    errorContainer = Color(0xFFFDF6F4),
    onErrorContainer = Color(0xFF410002),
    background = Miuix.LightBackground,
    onBackground = Miuix.LightOnBackground,
    surface = Miuix.LightSurface,
    onSurface = Miuix.LightOnSurface,
    surfaceVariant = Miuix.LightSurfaceVariant,
    onSurfaceVariant = Miuix.LightOnSurfaceVariant,
    surfaceTint = Miuix.LightPrimary,
    inverseSurface = Color(0xFF313031),
    inverseOnSurface = Color(0xFFF4F0F4),
    outline = Miuix.LightOutline,
    outlineVariant = Miuix.LightDivider,
    scrim = Miuix.LightWindowDimming,
)

/** MIUI 深色配色 → Material3 ColorScheme。 */
private fun miuixDarkColorScheme() = darkColorScheme(
    primary = Miuix.DarkPrimary,
    onPrimary = Miuix.DarkOnPrimary,
    primaryContainer = Miuix.DarkPrimaryContainer,
    onPrimaryContainer = Miuix.DarkOnPrimaryContainer,
    secondary = Miuix.DarkPrimary,
    onSecondary = Miuix.DarkOnPrimary,
    secondaryContainer = Miuix.DarkSecondaryContainer,
    onSecondaryContainer = Miuix.DarkOnSecondaryContainer,
    tertiary = Miuix.DarkPrimary,
    tertiaryContainer = Miuix.DarkTertiaryContainer,
    error = Miuix.DarkError,
    onError = Miuix.DarkOnError,
    errorContainer = Color(0xFF2E0603),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Miuix.DarkBackground,
    onBackground = Miuix.DarkOnBackground,
    surface = Miuix.DarkSurface,
    onSurface = Miuix.DarkOnSurface,
    surfaceVariant = Miuix.DarkSurfaceVariant,
    onSurfaceVariant = Miuix.DarkOnSurfaceVariant,
    surfaceTint = Miuix.DarkPrimary,
    inverseSurface = Color(0xFFE6E1E6),
    inverseOnSurface = Color(0xFF313031),
    outline = Miuix.DarkOutline,
    outlineVariant = Miuix.DarkDivider,
    scrim = Miuix.DarkWindowDimming,
)
