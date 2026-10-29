package com.casetracker.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.isRenderEffectSupported
import com.kyant.backdrop.isRuntimeShaderSupported
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 液态玻璃悬浮底栏（v1.90 · 纯 Compose + AGSL 光学管线，对齐 KernelSU）
 *
 * 管线（与 KernelSU FloatingBottomBar 同源，backdrop 库官方实现）：
 *   backdrop（实时层捕获）→ blur 4dp GPU 高斯模糊 → lens AGSL 折射/色散/深度
 *   → colorControls 容器色垫底 → Fresnel 高光 → InnerShadow 内阴影
 *
 * 关键点：
 * - 背板由 `Backdrop` 参数提供（调用方 rememberLayerBackdrop / rememberCombinedBackdrop
 *   绑定内容层），库托管捕获与实时刷新，无 OnPreDrawListener、无静态截图；
 * - 液滴 = 内容副本（layerBackdrop）+ 独立 drawBackdrop 透镜（按压时 lens 增强）；
 * - pill wrapContent 不 fillMaxWidth；Tab 等宽 72dp，液滴位置 = 索引 × tabWidth；
 * - 按压：液滴 78/56 放大、折射淡入；焦点标签 1.1×；
 * - 降级：API 33+ 完整 AGSL；API 31–32 blur+容器色（无折射）；<31 半透明+细边框。
 *
 * 依赖：io.github.kyant0:backdrop + io.github.kyant0:shapes:1.2.1
 * 前置：Compose ≥ 1.7（GraphicsLayer API），推荐对齐库的 Compose Multiplatform 1.12。
 */
@Composable
fun LiquidGlassBottomBar(
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    items: List<NavItem>,
    backdrop: Backdrop,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val isDark = isSystemInDarkTheme()
    val accentColor = if (isDark) Color(0xFF0091FF) else Color(0xFF1B7AE8)
    val containerColor = if (isDark) Color(0xFF121212).copy(alpha = 0.40f)
    else Color(0xFFFAFAFA).copy(alpha = 0.40f)

    val scope = rememberCoroutineScope()
    val viewConfig = LocalViewConfiguration.current

    // KernelSU 对齐常量
    val tabWidth = 72.dp                      // 等宽 Tab / 液滴宽
    val tabWidthPx = with(density) { tabWidth.toPx() }
    val pillHeight = 64.dp
    val pillPadding = 4.dp
    val dropHeight = 56.dp
    val blurRadius = 4.dp                     // KernelSU 对齐：4dp 轻模糊
    val lensHeight = 24.dp                    // KernelSU 对齐：折射边缘带 24dp
    val lensAmount = 24.dp
    val chromaticAberration = 0.5f             // KernelSU 对齐

    // 位置与按压动效（KernelSU value 弹簧：damping 1.0 / stiffness 1000）
    val position = remember { Animatable(selectedIndex.toFloat()) }
    val press = remember { Animatable(0f) }
    val scaleX = remember { Animatable(1f) }
    val scaleY = remember { Animatable(1f) }

    var dragOffsetPx by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }

    LaunchedEffect(selectedIndex) {
        position.animateTo(selectedIndex.toFloat(), spring(dampingRatio = 1f, stiffness = 1000f))
    }

    fun setPressed(on: Boolean) {
        scope.launch {
            press.animateTo(if (on) 1f else 0f, spring(dampingRatio = 1f, stiffness = 1000f))
            val target = if (on) 78f / 56f else 1f
            scaleX.animateTo(target, spring(dampingRatio = 0.6f, stiffness = 250f))
            scaleY.animateTo(target, spring(dampingRatio = 0.7f, stiffness = 250f))
        }
    }

    val shownIndex = if (dragging) {
        (position.value + dragOffsetPx / tabWidthPx).coerceIn(0f, (items.size - 1).toFloat())
    } else position.value
    val dropIndex = shownIndex.roundToInt().coerceIn(0, items.size - 1)
    val dropX = shownIndex * tabWidthPx

    // 液滴内容副本（选中 Tab 图标+文字，实时层捕获，随内容滚动刷新）
    // API >= 31（RenderEffect 支持）才创建 GraphicsLayer 层；以下为 null → 不录制副本
    val liquidContentBackdrop =
        if (isRenderEffectSupported()) rememberLayerBackdrop() else null

    val progress = press.value
    val pillEffects: com.kyant.backdrop.BackdropEffectScope.() -> Unit = {
        if (isRuntimeShaderSupported()) {
            // API 33+：完整 AGSL 光学管线（KernelSU Lens 同源）
            colorControls(saturation = 1.5f)
            blur(blurRadius.toPx())
            // v1.90.2：AGSL 折射单独防御——编译失败只丢弃折射，保留模糊玻璃
            runCatching {
                lens(
                    refractionHeight = lensHeight.toPx(),
                    refractionAmount = lensAmount.toPx(),
                    depthEffect = true,
                    chromaticAberration = true
                )
            }
        } else if (isRenderEffectSupported()) {
            // API 31–32：GPU 模糊 + 色调垫底，跳过 AGSL 折射
            colorControls(saturation = 1.5f)
            blur(blurRadius.toPx())
        } else {
            // API <31：无模糊无折射，仅容器色
        }
    }

    Box(
        modifier = modifier
            .navigationBarsPadding()
            .padding(top = 6.dp, bottom = 12.dp)
            .height(pillHeight)
    ) {
        // ===== pill：悬浮胶囊，wrapContent，不 fillMaxWidth =====
        Row(
            modifier = Modifier
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { RoundedCornerShape(50) },
                    effects = pillEffects,
                    highlight = {
                        // v1.90.2：高光 shader 失败不画，不影响主体
                        runCatching { Highlight.Default }.getOrNull()
                    },
                    shadow = {
                        runCatching { Shadow.Default }.getOrNull()
                    },
                    innerShadow = {
                        runCatching { InnerShadow.Default }.getOrNull()
                    },
                    onDrawSurface = {
                        // 容器色 + 玻璃光泽（顶部高光渐变 + 底部内阴影，全 API 生效）
                        drawRect(containerColor)
                        drawRect(
                            brush = Brush.verticalGradient(
                                0f to Color.White.copy(alpha = if (isDark) 0.10f else 0.20f),
                                0.35f to Color.White.copy(alpha = if (isDark) 0.03f else 0.07f),
                                1f to Color.Transparent
                            ),
                            topLeft = Offset.Zero,
                            size = Size(size.width, size.height * 0.5f)
                        )
                        drawRect(
                            brush = Brush.verticalGradient(
                                0f to Color.Transparent,
                                1f to Color.Black.copy(alpha = if (isDark) 0.30f else 0.06f)
                            ),
                            topLeft = Offset(0f, size.height - 8.dp.toPx()),
                            size = Size(size.width, 8.dp.toPx())
                        )
                    }
                )
                .height(pillHeight)
                .padding(horizontal = pillPadding)
                .pointerInput(items.size, selectedIndex) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        setPressed(true)
                        var dragMode = false
                        var totalX = 0f
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                setPressed(false)
                                if (dragMode) {
                                    val target = ((position.value + totalX / tabWidthPx).roundToInt())
                                        .coerceIn(0, items.size - 1)
                                    if (target != selectedIndex) onItemSelected(target)
                                    scope.launch {
                                        position.animateTo(
                                            target.toFloat(),
                                            spring(dampingRatio = 1f, stiffness = 1000f)
                                        )
                                    }
                                } else {
                                    val idx = ((change.position.x - pillPadding.toPx()) / tabWidthPx)
                                        .roundToInt().coerceIn(0, items.size - 1)
                                    if (idx != selectedIndex) onItemSelected(idx)
                                    scope.launch {
                                        position.animateTo(
                                            idx.toFloat(),
                                            spring(dampingRatio = 1f, stiffness = 1000f)
                                        )
                                    }
                                }
                                dragOffsetPx = 0f
                                dragging = false
                                break
                            }
                            if (!dragMode) {
                                val dx = change.position.x - down.position.x
                                val dy = change.position.y - down.position.y
                                val slop = viewConfig.touchSlop
                                if (abs(dx) > slop || abs(dy) > slop) {
                                    dragMode = true
                                    dragging = true
                                    change.consume()
                                }
                            } else {
                                totalX += change.positionChange().x
                                dragOffsetPx = totalX
                                change.consume()
                            }
                        }
                    }
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEachIndexed { index, item ->
                val selected = selectedIndex == index
                val labelScale = if (selected) 1.1f else 1f
                Column(
                    modifier = Modifier
                        .width(tabWidth)
                        .fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically)
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label,
                        tint = if (selected) accentColor else Color(0xFF8A94A6),
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected) accentColor else Color(0xFF8A94A6),
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.graphicsLayer {
                            this.scaleX = labelScale
                            this.scaleY = labelScale
                        }
                    )
                }
            }
        }

        // ===== 液滴：内容副本 + AGSL 折射透镜（按压淡入），位置 = 索引 × tabWidth =====
        val liquidEffects: com.kyant.backdrop.BackdropEffectScope.() -> Unit = {
            if (isRuntimeShaderSupported()) {
                colorControls(saturation = 1.5f)
                blur(blurRadius.toPx())
                runCatching {
                    lens(
                        refractionHeight = lensHeight.toPx() * progress,
                        refractionAmount = lensAmount.toPx() * progress,
                        depthEffect = true,
                        chromaticAberration = true
                    )
                }
            } else if (isRenderEffectSupported()) {
                colorControls(saturation = 1.5f)
                blur(blurRadius.toPx())
            }
        }

        Row(
            modifier = Modifier
                .graphicsLayer {
                    this.translationX = dropX
                    this.translationY = with(density) { 4.dp.toPx() }
                    this.scaleX = scaleX.value
                    this.scaleY = scaleY.value
                }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { RoundedCornerShape(50) },
                    effects = liquidEffects,
                    highlight = {
                        // v1.90.2：高光 shader 失败不画，不影响主体
                        if (isRuntimeShaderSupported()) {
                            runCatching { Highlight.Default.copy(alpha = progress) }.getOrNull()
                        } else null
                    },
                    innerShadow = {
                        if (progress > 0.01f) {
                            runCatching { InnerShadow.Default.copy(alpha = progress) }.getOrNull()
                        } else null
                    },
                    onDrawSurface = {
                        // 液滴底色：accent tint（静止浅蓝 15%，按压加深至 60%）
                        drawRect(accentColor.copy(alpha = 0.15f + 0.45f * progress))
                        drawRect(Color.Black.copy(alpha = 0.05f * progress))
                        // v1.90.2 玻璃光泽：顶部高光渐变（按压时增强）
                        drawRect(
                            brush = Brush.verticalGradient(
                                0f to Color.White.copy(alpha = 0.12f + 0.18f * progress),
                                0.4f to Color.White.copy(alpha = 0.02f + 0.04f * progress),
                                1f to Color.Transparent
                            ),
                            topLeft = Offset.Zero,
                            size = Size(size.width, size.height * 0.5f)
                        )
                        // API <31 降级：纯半透明色调 + 细边框（无模糊无折射）
                        if (!isRenderEffectSupported()) {
                            drawRoundRect(
                                color = Color.White.copy(alpha = 0.6f),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
                            )
                        }
                    }
                )
                .then(
                    if (liquidContentBackdrop != null) {
                        Modifier.layerBackdrop(liquidContentBackdrop)
                    } else {
                        Modifier
                    }
                )
                .height(dropHeight)
                .width(tabWidth)
                .clip(RoundedCornerShape(50))
                .graphicsLayer {
                    this.alpha = 0.4f + 0.6f * progress
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            // 选中 Tab 放大副本（layerBackdrop 录制，按压淡入 1.1×）
            Column(
                modifier = Modifier
                    .graphicsLayer {
                        this.scaleX = 1f + 0.1f * progress
                        this.scaleY = 1f + 0.1f * progress
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically)
            ) {
                Icon(
                    imageVector = items[dropIndex].icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = items[dropIndex].label,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }
        }
    }
}
