package com.pzdd.mydia.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.pzdd.mydia.ui.prefs.PREFS_GLOBAL
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
/**
 * 独立页面（非 MainActivity）的统一主题入口：读取全局 SP 的
 * `ui_theme`（system/light/dark）与 `ui_dynamic_color`，和主界面保持完全一致。
 *
 * 修复：原来各独立 Activity（功能列表/增强模式/规则编辑/scope 等）裸调
 * [MyDiaTheme] 只跟随系统——手动切「深色」后这些页面仍是亮色。
 */
@Composable
fun MyDiaAppTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val sp = remember(context) {
        context.getSharedPreferences(PREFS_GLOBAL, android.content.Context.MODE_PRIVATE)
    }
    val theme = sp.getString("ui_theme", "system") ?: "system"
    val dynamic = sp.getBoolean("ui_dynamic_color", false)
    val dark = when (theme) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    MyDiaTheme(darkTheme = dark, dynamicColor = dynamic, content = content)
}

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
    // 卡片/背景调换：背景用更深的灰（原卡片浅灰 #F7F7F7 再深一档），
    // 卡片用白（原背景色）。嵌套容器/子容器用浅灰（原卡片色）在白卡上形成层次。
    background = Miuix.LightSurfaceDeep,
    onBackground = Miuix.LightOnBackground,
    surface = Miuix.LightBackground,
    onSurface = Miuix.LightOnSurface,
    surfaceVariant = Miuix.LightSurfaceVariant,
    onSurfaceVariant = Miuix.LightOnSurfaceVariant,
    surfaceContainerLowest = Miuix.LightSurface,
    surfaceContainerLow = Miuix.LightSurface,
    surfaceContainer = Miuix.LightSurface,
    surfaceContainerHigh = Miuix.LightSurfaceContainerHigh,
    surfaceContainerHighest = Miuix.LightSurfaceContainerHighest,
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
    // 卡片/背景调换：背景用纯黑（原卡片色），卡片用深灰 #242424（原背景色），
    // 嵌套容器用稍亮一档 #2D2D2D 在白/深灰卡上形成层次。
    background = Miuix.DarkSurface,
    onBackground = Miuix.DarkOnBackground,
    surface = Miuix.DarkBackground,
    onSurface = Miuix.DarkOnSurface,
    surfaceVariant = Miuix.DarkSurfaceVariant,
    onSurfaceVariant = Miuix.DarkOnSurfaceVariant,
    surfaceContainerLowest = Miuix.DarkSurface,
    surfaceContainerLow = Miuix.DarkSurfaceContainerHighest,
    surfaceContainer = Miuix.DarkSurfaceContainerHighest,
    surfaceContainerHigh = Miuix.DarkSurfaceContainerHighest,
    surfaceContainerHighest = Miuix.DarkSurfaceContainerHighest,
    surfaceTint = Miuix.DarkPrimary,
    inverseSurface = Color(0xFFE6E1E6),
    inverseOnSurface = Color(0xFF313031),
    outline = Miuix.DarkOutline,
    outlineVariant = Miuix.DarkDivider,
    scrim = Miuix.DarkWindowDimming,
)
