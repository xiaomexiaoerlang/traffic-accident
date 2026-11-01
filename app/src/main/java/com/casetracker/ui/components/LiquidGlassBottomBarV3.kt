package com.casetracker.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import com.kyant.backdrop.isRuntimeShaderSupported
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 液态玻璃悬浮底栏 V3（v1.90.7）——KernelSU 三层玻璃结构 + Pager 跟手指示器 + Tab 按压动画。
 *
 * v1.90.7 跟手性修复（用户反馈：tab 左右滑动不跟手）：
 *   【根因】v1.90.6 在「组合期」读取 pagerState 计算 indicatorFraction，导致每次滑动都重组整个底栏
 *           （玻璃条 drawBackdrop 的 effects lambda 重建、副本层每帧重跑玻璃管线）→ 掉帧、滞后。
 *   【修复】所有滑动相关读取移到「布局/绘制期」：
 *           1. 液滴位移：offset lambda 内直接读 pagerState.currentPage + currentPageOffsetFraction，
 *              状态变化只 relayout 液滴节点（跳过重组），GPU 层逐帧跟随；
 *           2. Tab 高亮/放大/渐变：graphicsLayer lambda 内读 pagerState 计算活跃度，
 *              scale + ColorFilter.tint 逐帧更新该 layer（不重组）；
 *           3. 副本层 effects 去掉 press 联动（固定 lens，脉冲期间不重跑全宽玻璃管线）。
 *   保留：三层玻璃结构（blur+vibrancy+lens 防御/白色副本/透明透镜无蓝色）、液滴拖拽（offset hit 跟随）、
 *   Tab 按压 0.9 回弹 + ripple、API 降级、导航栏 padding。
 */
@Composable
fun LiquidGlassBottomBarV3(
    pagerState: PagerState,
    onItemSelected: (Int) -> Unit,
    items: List<NavItem>,
    backdrop: Backdrop,
    modifier: Modifier = Modifier
) {
    val isLightTheme = !isSystemInDarkTheme()
    val containerColor =
        if (isLightTheme) Color(0xFFFAFAFA).copy(alpha = 0.4f)
        else Color(0xFF121212).copy(alpha = 0.4f)
    val idleTint = if (isLightTheme) Color(0xFF5A6472) else Color(0xFF8A94A6)

    val scope = rememberCoroutineScope()
    // 玻璃条自身 UI 白色副本背板（仅 API>=31 录制；<31 液滴直接折射页面背板）
    val tabsBackdrop = if (isRenderEffectSupported()) rememberLayerBackdrop() else null

    // 拖拽覆盖值：手指按住液滴拖动时非 null；offset lambda 内读取（变化只 relayout 液滴）
    var dragFraction by remember { mutableStateOf<Float?>(null) }

    // 切换脉冲：点击 Tab / 滑动翻页后液滴放大 78/56 + 折射增强再回落
    val press = remember { Animatable(0f) }
    LaunchedEffect(pagerState.currentPage) {
        press.snapTo(1f)
        press.animateTo(0f, spring(dampingRatio = 1f, stiffness = 800f))
    }

    BoxWithConstraints(
        modifier = modifier
            .navigationBarsPadding()
            .padding(top = 6.dp, bottom = 12.dp)
    ) {
        val density = LocalDensity.current
        val tabWidthPx = with(density) { (maxWidth - 8.dp).toPx() / items.size }
        val dropletTranslationY = with(density) { 4.dp.toPx() }

        // ===== 1. 玻璃条容器（全宽 64dp 胶囊，blur + vibrancy + lens）=====
        Row(
            modifier = Modifier
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { RoundedCornerShape(50) },
                    effects = {
                        blur(4.dp.toPx())
                        vibrancy()
                        // AGSL lens：仅 API 33+；失败只丢折射，不影响管线
                        if (isRuntimeShaderSupported()) {
                            runCatching { lens(16.dp.toPx(), 24.dp.toPx()) }
                        }
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
                .pointerInput(items.size, tabWidthPx) {
                    // v1.90.8：整条底栏任意位置水平拖动 → 液滴实时跟手（c001apk 同款交互）
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            scope.launch { press.snapTo(1f) }
                            val startX = offset.x - with(density) { 4.dp.toPx() }
                            dragFraction = (startX / tabWidthPx).coerceIn(0f, (items.size - 1).toFloat())
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            val base = dragFraction ?: (pagerState.currentPage + pagerState.currentPageOffsetFraction)
                            dragFraction = (base + dragAmount / tabWidthPx)
                                .coerceIn(0f, (items.size - 1).toFloat())
                        },
                        onDragEnd = {
                            val base = dragFraction ?: (pagerState.currentPage + pagerState.currentPageOffsetFraction)
                            val target = base.roundToInt().coerceIn(0, items.size - 1)
                            dragFraction = null
                            if (target != pagerState.currentPage) {
                                onItemSelected(target) // Pager 动画归位
                            } else {
                                scope.launch { press.animateTo(0f, spring(dampingRatio = 1f, stiffness = 800f)) }
                            }
                        },
                        onDragCancel = {
                            dragFraction = null
                            scope.launch { press.animateTo(0f, spring(dampingRatio = 1f, stiffness = 800f)) }
                        }
                    )
                }
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEachIndexed { index, item ->
                GlassTabItemV3(
                    icon = item.icon,
                    label = item.label,
                    index = index,
                    pagerState = pagerState,
                    idleTint = idleTint,
                    onClick = { onItemSelected(index) },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
            }
        }

        // ===== 2. 白色内容副本层（不可见，供液滴折射白色高亮；effects 固定不随 press 重跑）=====
        if (tabsBackdrop != null) {
            Row(
                modifier = Modifier
                    .alpha(0f)
                    .layerBackdrop(tabsBackdrop)
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { RoundedCornerShape(50) },
                        effects = {
                            blur(4.dp.toPx())
                            vibrancy()
                            if (isRuntimeShaderSupported()) {
                                runCatching { lens(16.dp.toPx(), 24.dp.toPx()) }
                            }
                        },
                        onDrawSurface = { drawRect(containerColor) }
                    )
                    .height(56.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEachIndexed { index, item ->
                    StaticGlassTabContent(
                        icon = item.icon,
                        label = item.label,
                        tint = Color.White,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
            }
        }

        // ===== 3. 液滴指示器（透明玻璃透镜；offset 布局期读 Pager 状态，逐帧跟随不重组）=====
        val dropletBackdrop =
            if (tabsBackdrop != null) rememberCombinedBackdrop(backdrop, tabsBackdrop)
            else backdrop

        Box(
            modifier = Modifier
                .padding(horizontal = 4.dp)
                .offset {
                    // 布局期读取：滑动时仅 relayout 液滴节点（跳过重组，GPU 逐帧跟随）
                    val fraction = dragFraction ?: (pagerState.currentPage + pagerState.currentPageOffsetFraction)
                    IntOffset(x = (fraction * tabWidthPx).roundToInt(), y = dropletTranslationY.roundToInt())
                }
                .drawBackdrop(
                    backdrop = dropletBackdrop,
                    shape = { RoundedCornerShape(50) },
                    effects = {
                        val p = press.value
                        if (isRuntimeShaderSupported()) {
                            runCatching {
                                lens(
                                    refractionHeight = 10.dp.toPx() * p,
                                    refractionAmount = 14.dp.toPx() * p,
                                    chromaticAberration = true
                                )
                            }
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
                        val p = press.value
                        val scale = lerp(1f, 78f / 56f, p)
                        scaleX = scale
                        scaleY = scale
                    },
                    onDrawSurface = {
                        val p = press.value
                        drawRect(
                            if (isLightTheme) Color.Black.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.10f),
                            alpha = 1f - p
                        )
                        drawRect(Color.Black.copy(alpha = 0.03f * p))
                    }
                )
                .height(56.dp)
                .fillMaxWidth(1f / items.size)
        )
    }
}

/**
 * 单个 Tab（v1.90.7）：
 *   - 按压：scale→0.9，spring(DampingRatioMediumBouncy) 回弹 + 水波纹 ripple（按压动画组合期驱动，单 Tab 重组）；
 *   - 滑动高亮：颜色渐变在组合期（重组范围仅本 Tab，玻璃条/副本层不重组）；
 *     scale 放大在 graphicsLayer 绘制期读取（只更新本 layer，不触发重组）。
 */
@Composable
private fun GlassTabItemV3(
    icon: ImageVector,
    label: String,
    index: Int,
    pagerState: PagerState,
    idleTint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.9f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "tabPressScale"
    )

    // 组合期读取：滑动中本 Tab 颜色渐变（灰 → 白）；重组范围仅本 Tab，不影响玻璃条管线
    val fraction = pagerState.currentPage + pagerState.currentPageOffsetFraction
    val active = (1f - abs(index - fraction)).coerceIn(0f, 1f)
    val tint = lerp(idleTint, Color.White, active)

    Column(
        modifier = modifier
            .graphicsLayer {
                // 绘制期读取：滑动中仅更新本 layer 的缩放（不触发重组）
                val f = pagerState.currentPage + pagerState.currentPageOffsetFraction
                val a = (1f - abs(index - f)).coerceIn(0f, 1f)
                val s = pressScale * lerp(1f, 1.12f, a)
                scaleX = s
                scaleY = s
            }
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current
            ) { onClick() },
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
            fontWeight = if (index == pagerState.currentPage) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** 副本层用：白色静态 Tab 内容 */
@Composable
private fun StaticGlassTabContent(
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
        Icon(imageVector = icon, contentDescription = label, tint = tint, modifier = Modifier.size(22.dp))
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = tint, maxLines = 1)
    }
}
