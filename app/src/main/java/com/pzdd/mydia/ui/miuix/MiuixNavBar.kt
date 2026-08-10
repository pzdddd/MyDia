package com.pzdd.mydia.ui.miuix

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pzdd.mydia.ui.LocalGlassEnabled
import com.pzdd.mydia.ui.LocalGlassBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 液态玻璃底部导航栏（参考 pznote 的 LiquidGlassBottomBar）。
 *
 * 四层结构：
 *  - [图层1] 底栏整体：drawBackdrop 真液态玻璃（blur + lens + 高光 + 阴影）。无 backdrop 时降级半透明。
 *  - [图层2] 选中滑块：跟随选中位置丝滑滑动（Animatable），按压时果冻膨胀 + 拖拽拉伸形变。
 *  - [图层3] 图标与文字：最上层，清晰不被模糊。
 *  - [图层4] 隐形拖拽层：跟手指实时移动滑块，松手弹性吸附到最近 tab。
 *
 * @param items Tab 项（标题 + 图标）
 * @param selected 当前选中
 * @param onSelect 切换回调
 */
@Composable
fun MiuixNavBar(
    items: List<Pair<String, ImageVector>>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    backdrop: com.kyant.backdrop.backdrops.LayerBackdrop? = null,
) {
    val cs = MaterialTheme.colorScheme
    val isLight = cs.background.luminance() > 0.5f
    val tabCount = items.size
    val useBackdrop = LocalGlassEnabled.current && backdrop != null

    BoxWithConstraints(modifier = modifier.fillMaxWidth().height(72.dp)) {
        val density = LocalDensity.current
        val tabWidthPx = with(density) { maxWidth.toPx() } / tabCount
        val tabWidth = maxWidth / tabCount
        val scope = rememberCoroutineScope()

        // 滑块位置动画（丝滑滑动）
        val position = remember { Animatable(selected.toFloat()) }
        var isPressed by remember { mutableStateOf(false) }
        var dragVelocity by remember { mutableFloatStateOf(0f) }

        // 果冻缩放：按压/拖拽当前选中 tab 时滑块变大（状态驱动 + 弹簧回弹）
        var pressScale by remember { mutableFloatStateOf(1f) }
        val jellyScale by animateFloatAsState(
            targetValue = pressScale,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
            label = "jellyScale",
        )
        // 外部选中变化 → 滑块平滑滑过去（切 tab 不弹大，只滑动）
        LaunchedEffect(selected) {
            if (!isPressed) {
                position.animateTo(
                    selected.toFloat(),
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow,
                    ),
                )
            }
        }
        // 拖拽拉伸形变（拖得快时滑块沿拖动方向拉伸）
        val stretchAmount by animateFloatAsState(
            targetValue = if (isPressed) (abs(dragVelocity) * 0.0015f).coerceIn(0f, 0.25f) else 0f,
            animationSpec = spring(stiffness = Spring.StiffnessHigh),
            label = "stretchAmount",
        )
        val stretchDirection = if (dragVelocity >= 0f) 1f else -1f

        val sliderOffsetPx = position.value * tabWidthPx

        // ==================== [图层1] 底栏整体液态玻璃背景 ====================
        // 有 backdrop（在 layerBackdrop 树内）：用 drawBackdrop 画真液态玻璃。
        // 无 backdrop（底栏在树外，由外层 Haze 负责模糊）：不画背景，避免和 Haze 叠加/带边框。
        val containerColor = if (isLight) Color(0xFFFAFAFA).copy(alpha = 0.72f)
        else Color(0xFF121212).copy(alpha = 0.72f)
        val bgMod = if (useBackdrop && backdrop != null) {
            Modifier.fillMaxSize().drawBackdrop(
                backdrop = backdrop,
                shape = { Capsule() },
                effects = { blur(100.dp.toPx()) },
                highlight = { Highlight.Default },
                shadow = { Shadow() },
                onDrawSurface = { drawRect(containerColor) },
            )
        } else {
            // 降级（backdrop 源未生效）：画半实色背景，保证底栏不透明、不透出背后文字
            Modifier
                .fillMaxSize()
                .clip(Capsule())
                .background(containerColor)
                .border(0.5.dp, cs.onSurface.copy(alpha = 0.15f), Capsule())
        }
        Box(modifier = bgMod)

        // ==================== [图层2] 选中滑块 ====================
        val sliderMod = if (useBackdrop && backdrop != null) {
            Modifier
                .align(Alignment.CenterStart)
                .offset { IntOffset(sliderOffsetPx.roundToInt(), 0) }
                .width(tabWidth)
                .fillMaxHeight()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .graphicsLayer {
                    scaleX = jellyScale * (1f + stretchAmount * stretchDirection * 0.3f)
                    scaleY = jellyScale * (1f - stretchAmount * 0.15f)
                }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { Capsule() },
                    effects = {
                        val pressProgress = if (isPressed) 1f else 0f
                        lens(
                            10.dp.toPx() + 6.dp.toPx() * pressProgress,
                            14.dp.toPx() + 8.dp.toPx() * pressProgress,
                            chromaticAberration = true,
                        )
                    },
                    highlight = { Highlight.Default },
                    shadow = { Shadow() },
                    innerShadow = { InnerShadow(radius = 8.dp) },
                    onDrawSurface = {
                        drawRect(if (isLight) Color.Black.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.10f))
                    },
                )
        } else {
            // 降级（外层 Haze 负责背景）：液态透明水滴滑块（白色高光边 + 极透填充 + 按压膨胀）
            Modifier
                .align(Alignment.CenterStart)
                .offset { IntOffset(sliderOffsetPx.roundToInt(), 0) }
                .width(tabWidth)
                .fillMaxHeight()
                .padding(horizontal = 14.dp, vertical = 8.dp)
                .graphicsLayer {
                    scaleX = jellyScale * (1f + stretchAmount * stretchDirection * 0.3f)
                    scaleY = jellyScale * (1f - stretchAmount * 0.15f)
                }
                .clip(Capsule())
                .border(1.dp, Color.White.copy(alpha = 0.6f), Capsule())
                .background(Color.White.copy(alpha = 0.18f))
        }
        Box(modifier = sliderMod)

        // ==================== [图层3] 图标与文字 ====================
        Row(modifier = Modifier.fillMaxSize()) {
            items.forEachIndexed { index, (label, icon) ->
                val isSelected = selected == index
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .pointerInput(index) {
                            detectTapGestures { onSelect(index) }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    val contentColor by animateColorAsState(
                        targetValue = if (isSelected) cs.primary else cs.onSurfaceVariant,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium,
                        ),
                        label = "tabContentColor",
                    )
                    val iconScale by animateFloatAsState(
                        targetValue = if (isSelected) 1.15f else 1f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium,
                        ),
                        label = "tabIconScale",
                    )
                    val labelAlpha by animateFloatAsState(
                        targetValue = if (isSelected) 1f else 0.7f,
                        animationSpec = spring(stiffness = Spring.StiffnessMedium),
                        label = "tabLabelAlpha",
                    )
                    val labelScale by animateFloatAsState(
                        targetValue = if (isSelected) 1.05f else 1f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium,
                        ),
                        label = "tabLabelScale",
                    )
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = label,
                            tint = contentColor,
                            modifier = Modifier.size(24.dp).graphicsLayer {
                                scaleX = iconScale; scaleY = iconScale
                            },
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = label,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = contentColor.copy(alpha = labelAlpha),
                            modifier = Modifier.graphicsLayer {
                                scaleX = labelScale; scaleY = labelScale
                            },
                        )
                    }
                }
            }
        }

        // ==================== [图层4] 隐形拖拽层 ====================
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset { IntOffset(sliderOffsetPx.roundToInt(), 0) }
                .width(tabWidth)
                .fillMaxHeight()
                .pointerInput(tabCount, tabWidthPx) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        // 按下：滑块变大（按压效果）
                        pressScale = 1.3f
                        var lastX = down.position.x
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            if (change.isConsumed) continue
                            if (change.pressed) {
                                // 拖动跟手
                                val dx = change.position.x - lastX
                                if (dx != 0f) {
                                    change.consume()
                                    lastX = change.position.x
                                    dragVelocity = dx
                                    scope.launch {
                                        position.snapTo(
                                            (position.value + dx / tabWidthPx).coerceIn(0f, (tabCount - 1).toFloat()),
                                        )
                                    }
                                }
                            } else {
                                // 松手：弹回 + 吸附到最近 tab
                                pressScale = 1f
                                dragVelocity = 0f
                                val target = position.value.roundToInt().coerceIn(0, tabCount - 1)
                                scope.launch {
                                    position.animateTo(
                                        target.toFloat(),
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessLow,
                                        ),
                                    )
                                }
                                onSelect(target)
                                break
                            }
                        }
                        // 手势结束兜底：确保弹回
                        if (pressScale != 1f) pressScale = 1f
                    }
                },
        )
    }
}
