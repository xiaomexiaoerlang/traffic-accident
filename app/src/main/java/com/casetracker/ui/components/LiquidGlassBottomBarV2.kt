package com.casetracker.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.isRenderEffectSupported
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 液态玻璃悬浮底栏 V2（v1.90.4）——结构对齐 KernelSU / Kyant0 AndroidLiquidGlass 官方 LiquidBottomTabs。
 *
 * 与旧版（v1.90.3 LiquidGlassBottomBar）的关键差异（消除蓝色）：
 * - 玻璃条容器：wrapContent 小胶囊 → 全宽 64dp 胶囊，effects = blur(4dp) + vibrancy + lens(16/24)，
 *   垫色仅 containerColor（白/黑 0.4）+ 1dp 细边框，无 accent 蓝色；
 * - 液滴：accent 蓝垫底 → 透明玻璃透镜（lens 折射 + Highlight + Shadow + InnerShadow + 黑/白 0.1 底色），
 *   按压时底色减弱（更透）、放大 78/56、折射增强，全程不染色；
 * - 新增内容副本层（alpha=0 + layerBackdrop 录制玻璃条 UI，白色 tint）：液滴折射出白色高亮文字，
 *   选中 Tab 高亮由透镜放大 + 白字呈现（ksu 同款），而非蓝色；
 * - 液滴位置 = position × tabWidth（全宽均分），支持拖拽跟手、松手吸附。
 *
 * 保留项（不回归）：
 * - 实时背板：backdrop 由调用方提供（rememberLayerBackdrop 绑定内容层），列表滚动折射实时跟随；
 * - v1.90.1/1.90.2 拆级防御：AGSL lens 单独 runCatching（真机编译失败只丢折射，保留 blur+vibrancy），
 *   highlight/shadow/innerShadow 各自 runCatching 返回 null；
 * - 兼容降级：API≥33 完整 AGSL；31–32 blur+vibrancy（lens 跳过）；<31 仅容器色+边框（库内部自动判断）。
 */
@Composable
fun LiquidGlassBottomBarV2(
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    items: List<NavItem>,
    backdrop: Backdrop,
    modifier: Modifier = Modifier
) {
    val isLightTheme = !isSystemInDarkTheme()
    val containerColor =
        if (isLightTheme) Color(0xFFFAFAFA).copy(alpha = 0.4f)
        else Color(0xFF121212).copy(alpha = 0.4f)
    // 未选中 Tab 图标/文字颜色（中性灰，无蓝色）
    val idleTint = if (isLightTheme) Color(0xFF5A6472) else Color(0xFF8A94A6)

    val scope = rememberCoroutineScope()
    // 玻璃条自身 UI 的录制背板（仅 API >= 31 有意义；<31 液滴直接折射页面背板）
    val tabsBackdrop = if (isRenderEffectSupported()) rememberLayerBackdrop() else null

    BoxWithConstraints(
        modifier = modifier
            .navigationBarsPadding()
            .padding(top = 6.dp, bottom = 12.dp)
    ) {
        val density = LocalDensity.current
        // 全宽均分 tab 宽（与玻璃条 Row 的 weight 均分一致）
        val tabWidthPx = with(density) { (maxWidth - 8.dp).toPx() / items.size }
        val position = remember { Animatable(selectedIndex.toFloat()) } // 液滴位置（单位：tab 索引）
        val press = remember { Animatable(0f) }                         // 按压进度 0→1（放大 + 折射联动）
        val dropletTranslationY = with(density) { 4.dp.toPx() }        // 液滴垂直居中于玻璃条内容区

        // 外部选中变化（点击 Tab / 程序切换）→ 液滴滑动 + 按压脉冲
        LaunchedEffect(selectedIndex) {
            press.snapTo(1f)
            position.animateTo(
                selectedIndex.toFloat(),
                spring(dampingRatio = 1f, stiffness = 600f)
            )
            press.animateTo(0f, spring(dampingRatio = 1f, stiffness = 800f))
        }

        // ===== 1. 玻璃条容器（全宽 64dp 胶囊，blur + vibrancy + lens）=====
        Row(
            modifier = Modifier
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { RoundedCornerShape(50) },
                    effects = {
                        // 完整 AGSL 管线；lens 单独防御（真机 shader 编译失败只丢折射）
                        blur(4.dp.toPx())
                        vibrancy()
                        runCatching { lens(16.dp.toPx(), 24.dp.toPx()) }
                    },
                    onDrawSurface = {
                        drawRect(containerColor)
                        drawRoundRect(
                            color = if (isLightTheme) Color.White.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.12f),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
                        )
                    }
                )
                .height(64.dp)
                .fillMaxWidth()
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEachIndexed { index, item ->
                LiquidGlassTabItem(
                    icon = item.icon,
                    label = item.label,
                    tint = idleTint,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { onItemSelected(index) }
                )
            }
        }

        // ===== 2. 内容副本层（不可见，白色，供液滴折射出高亮文字）=====
        if (tabsBackdrop != null) {
            Row(
                modifier = Modifier
                    .alpha(0f)
                    .layerBackdrop(tabsBackdrop)
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { RoundedCornerShape(50) },
                        effects = {
                            val p = press.value
                            blur(4.dp.toPx())
                            vibrancy()
                            runCatching { lens(16.dp.toPx() * p, 24.dp.toPx() * p) }
                        },
                        onDrawSurface = { drawRect(containerColor) }
                    )
                    .height(56.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEachIndexed { index, item ->
                    LiquidGlassTabItem(
                        icon = item.icon,
                        label = item.label,
                        tint = Color.White,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                }
            }
        }

        // ===== 3. 液滴指示器（透明玻璃透镜，无染色）=====
        val dropletBackdrop =
            if (tabsBackdrop != null) rememberCombinedBackdrop(backdrop, tabsBackdrop)
            else backdrop

        Box(
            modifier = Modifier
                .padding(horizontal = 4.dp)
                // v1.90.5：layout 级 offset 移动（命中区域跟随液滴，可拖拽跟手）——对齐 ksu 官方
                .offset {
                    IntOffset(
                        x = (position.value * tabWidthPx).roundToInt(),
                        y = dropletTranslationY.roundToInt()
                    )
                }
                .drawBackdrop(
                    backdrop = dropletBackdrop,
                    shape = { RoundedCornerShape(50) },
                    effects = {
                        val p = press.value
                        // 折射强度随按压增强（ksu 同款）；失败静默跳过
                        runCatching {
                            lens(
                                refractionHeight = 10.dp.toPx() * p,
                                refractionAmount = 14.dp.toPx() * p,
                                chromaticAberration = true
                            )
                        }
                    },
                    highlight = {
                        val p = press.value
                        if (p > 0.01f) runCatching { Highlight.Default.copy(alpha = p) }.getOrNull() else null
                    },
                    shadow = {
                        val p = press.value
                        if (p > 0.01f) runCatching { Shadow(alpha = p) }.getOrNull() else null
                    },
                    innerShadow = {
                        val p = press.value
                        if (p > 0.01f) runCatching { InnerShadow(radius = 8.dp * p, alpha = p) }.getOrNull() else null
                    },
                    layerBlock = {
                        // ksu 同款：按压放大 78/56
                        val p = press.value
                        val scale = lerp(1f, 78f / 56f, p)
                        scaleX = scale
                        scaleY = scale
                    },
                    onDrawSurface = {
                        val p = press.value
                        // 极低底色保证可读性；按压时减弱（玻璃更透）——无任何 accent 染色
                        drawRect(
                            if (isLightTheme) Color.Black.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.10f),
                            alpha = 1f - p
                        )
                        drawRect(Color.Black.copy(alpha = 0.03f * p))
                    }
                )
                .height(56.dp)
                .fillMaxWidth(1f / items.size)
                .pointerInput(items.size, tabWidthPx) {
                    // v1.90.5：液滴拖拽——按下即跟手（无 touchSlop 门限），松手吸附最近 Tab
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        scope.launch { press.snapTo(1f) }
                        var dragTotal = 0f
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                val target = (position.value + dragTotal / tabWidthPx)
                                    .roundToInt()
                                    .coerceIn(0, items.size - 1)
                                if (target != selectedIndex) {
                                    onItemSelected(target) // 由 LaunchedEffect 负责吸附动画
                                } else {
                                    scope.launch {
                                        position.animateTo(
                                            target.toFloat(),
                                            spring(dampingRatio = 1f, stiffness = 600f)
                                        )
                                    }
                                }
                                scope.launch { press.animateTo(0f, spring(dampingRatio = 1f, stiffness = 800f)) }
                                break
                            }
                            // 跟手：位移增量逐帧换算成液滴位置增量（hit 区域随 offset 跟随，拖得动）
                            change.consume()
                            val delta = change.positionChange().x
                            dragTotal += delta
                            scope.launch {
                                position.snapTo(
                                    (position.value + delta / tabWidthPx)
                                        .coerceIn(0f, (items.size - 1).toFloat())
                                )
                            }
                        }
                    }
                }
        )
    }
}

/** 单个 Tab：图标 + 文字（选中高亮由液滴透镜内白色副本呈现） */
@Composable
private fun LiquidGlassTabItem(
    icon: ImageVector,
    label: String,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = tint,
            fontWeight = if (tint == Color.White) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1
        )
    }
}
