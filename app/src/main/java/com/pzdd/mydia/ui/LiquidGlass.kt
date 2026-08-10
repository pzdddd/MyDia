package com.pzdd.mydia.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens

/**
 * 液态玻璃（Liquid Glass）工具层，基于 backdrop 库（io.github.kyant0:backdrop）。
 *
 * 原理：把可滚动【内容】用 [Modifier.layerBackdrop] 捕获成一个 [LayerBackdrop]，
 * 叠在内容之上的玻璃层（顶栏 / 底栏 / 卡片）用 [Modifier.drawBackdrop] 读取该层，
 * 叠加 blur（模糊）+ lens（折射）效果 → 透出底层内容的液态玻璃质感。
 *
 * 是否启用效果由全局开关 [LocalGlassEnabled] 控制（设置页「液态玻璃」开关）。
 * 关闭时玻璃层退化为半透明纯色，仍可正常使用。
 */

/** 当前子树是否启用液态玻璃效果。由 MainActivity 在最外层注入。 */
val LocalGlassEnabled = compositionLocalOf { false }

/** 当前子树的内容捕获源（LayerBackdrop）。由 MainActivity 注入，供子屏幕的玻璃卡片复用。 */
val LocalGlassBackdrop = compositionLocalOf<LayerBackdrop?> { null }

/**
 * 一个内容捕获容器：把内部内容标成 layerBackdrop 的源，提供对应的 [LayerBackdrop]。
 * 同时通过 [LocalGlassBackdrop] 把 backdrop 透传给子树，让各屏幕的卡片也能复用同一源。
 *
 * 用法：
 * ```
 * val backdrop = rememberLayerBackdrop()
 * GlassSourceBox(backdrop) {                // 可滚动内容（被捕获）
 *     LazyColumn { ... }
 *     GlassOverlay(backdrop) { TopAppBar(...) }   // 叠在内容上的玻璃层
 * }
 * ```
 */
@Composable
fun GlassSourceBox(
    backdrop: LayerBackdrop,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    androidx.compose.runtime.CompositionLocalProvider(LocalGlassBackdrop provides backdrop) {
        Box(modifier = modifier.fillMaxSize().layerBackdrop(backdrop)) {
            content()
        }
    }
}

/**
 * 玻璃层容器：透出底层内容并叠加液态玻璃效果。
 *
 * @param backdrop 与 [GlassSourceBox] 同一个 [LayerBackdrop]（向上转型为 [Backdrop]）
 * @param shape 玻璃形状
 * @param blurRadiusPx 模糊半径（px）
 * @param tint 玻璃着色（半透明覆盖色）；关闭液态玻璃时作为纯背景色
 */
@Composable
fun GlassOverlay(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    shape: Shape = RectangleShape,
    blurRadiusPx: Float = 24f,
    tint: Color = Color.Transparent,
    content: @Composable () -> Unit,
) {
    val enabled = LocalGlassEnabled.current
    val base = if (enabled) {
        // 启用：drawBackdrop 提供真实的液态玻璃。
        // 注意：lens（折射）只支持 CornerBasedShape（圆角矩形），
        //       对 RectangleShape 等非圆角形状会抛 UnsupportedOperationException，
        //       所以这里只在 shape 是 CornerBasedShape 时才加 lens。
        val canLens = shape is CornerBasedShape
        Modifier.drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                blur(blurRadiusPx)
                if (canLens) lens(blurRadiusPx, blurRadiusPx)
            },
        )
    } else {
        // 关闭：退化为半透明纯色背景
        Modifier.background(tint)
    }
    Box(modifier = modifier.then(base)) {
        content()
    }
}

/**
 * 便捷的液态玻璃卡片：自动从 [LocalGlassBackdrop] 取源，圆角矩形 + tint。
 * 给各屏幕的列表项 / 卡片复用（如应用页的 App 行、首页的激活卡）。
 *
 * 若 [LocalGlassBackdrop] 未注入（null），退化为半透明纯色卡片。
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    tint: Color = Color.Transparent,
    content: @Composable () -> Unit,
) {
    val backdrop = LocalGlassBackdrop.current
    if (backdrop != null) {
        GlassOverlay(
            backdrop = backdrop,
            modifier = modifier,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
            blurRadiusPx = 16f,
            tint = tint,
            content = content,
        )
    } else {
        // 无 backdrop 源：纯色卡片兜底
        Box(modifier = modifier.background(tint)) { content() }
    }
}
