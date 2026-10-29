package com.casetracker.ui.components

import android.annotation.SuppressLint
import android.graphics.BitmapShader
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.widget.ImageView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.casetracker.theme.MiuiBackground
import com.casetracker.theme.MiuiPrimary
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

data class NavItem(
    val label: String,
    val icon: ImageVector
)

// ===== KernelSU Lens.kt 同款 AGSL 折射 shader（Apache 2.0，Kyant0/AndroidLiquidGlass → KernelSU）=====
// 液滴内背景取样（content = backdrop 位图），按压时 refractionHeight/Amount 随 press 增强，
// 边缘向内折射放大背景 + 红青-蓝紫七色色散 + 深度法线（depthEffect）。
// 额外加 scaleFactor/baseOffsetX/baseOffsetY：backdrop 是整屏截图条，液滴要取样其中对应区域
// 并保持与 pill 相同缩放比例（content 是 1:1 像素取样，故用坐标缩放+偏移实现）。
private const val LENS_REFRACTION_SHADER = """
uniform shader content;

uniform float2 size;
uniform float2 offset;
uniform float4 cornerRadii;
uniform float refractionHeight;
uniform float refractionAmount;
uniform float depthEffect;
uniform float chromaticAberration;
uniform float scaleFactor;
uniform float baseOffsetX;
uniform float baseOffsetY;

float radiusAt(float2 coord, float4 radii) {
    if (coord.x >= 0.0) {
        if (coord.y <= 0.0) return radii.y;
        else return radii.z;
    } else {
        if (coord.y <= 0.0) return radii.x;
        else return radii.w;
    }
}

float sdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    float outside = length(max(cornerCoord, 0.0)) - radius;
    float inside = min(max(cornerCoord.x, cornerCoord.y), 0.0);
    return outside + inside;
}

float2 gradSdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    if (cornerCoord.x >= 0.0 || cornerCoord.y >= 0.0) {
        return sign(coord) * normalize(max(cornerCoord, 0.0));
    } else {
        float gradX = step(cornerCoord.y, cornerCoord.x);
        return sign(coord) * float2(gradX, 1.0 - gradX);
    }
}

float circleMap(float x) {
    return 1.0 - sqrt(1.0 - x * x);
}

half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 centeredCoord = (coord + offset) - halfSize;
    float radius = radiusAt(centeredCoord, cornerRadii);

    float sd = sdRoundedRect(centeredCoord, halfSize, radius);
    float2 base = coord * scaleFactor + float2(baseOffsetX, baseOffsetY);
    if (-sd >= refractionHeight) {
        return content.eval(base);
    }
    sd = min(sd, 0.0);

    float d = circleMap(1.0 - -sd / refractionHeight) * refractionAmount;
    float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
    float2 grad = normalize(gradSdRoundedRect(centeredCoord, halfSize, gradRadius) + depthEffect * normalize(centeredCoord));

    float2 refractedCoord = base + d * grad;
    float dispersionIntensity = chromaticAberration * ((centeredCoord.x * centeredCoord.y) / (halfSize.x * halfSize.y));
    float2 dispersedCoord = d * grad * dispersionIntensity;

    half4 color = half4(0.0);

    half4 red = content.eval(refractedCoord + dispersedCoord);
    color.r += red.r / 3.5;
    color.a += red.a / 7.0;

    half4 orange = content.eval(refractedCoord + dispersedCoord * (2.0 / 3.0));
    color.r += orange.r / 3.5;
    color.g += orange.g / 7.0;
    color.a += orange.a / 7.0;

    half4 yellow = content.eval(refractedCoord + dispersedCoord * (1.0 / 3.0));
    color.r += yellow.r / 3.5;
    color.g += yellow.g / 3.5;
    color.a += yellow.a / 7.0;

    half4 green = content.eval(refractedCoord);
    color.g += green.g / 3.5;
    color.a += green.a / 7.0;

    half4 cyan = content.eval(refractedCoord - dispersedCoord * (1.0 / 3.0));
    color.g += cyan.g / 3.5;
    color.b += cyan.b / 3.0;
    color.a += cyan.a / 7.0;

    half4 blue = content.eval(refractedCoord - dispersedCoord * (2.0 / 3.0));
    color.b += blue.b / 3.0;
    color.a += blue.a / 7.0;

    half4 purple = content.eval(refractedCoord - dispersedCoord);
    color.r += purple.r / 7.0;
    color.b += purple.b / 3.0;
    color.a += purple.a / 7.0;

    return color;
}
"""

/**
 * 底部导航（v1.88 液态玻璃 · 参照 KernelSU FloatingBottomBar / compose-miuix IosLiquidGlassNavigationBar）
 *
 * 结构（对齐 KernelSU）：
 * - pill：全圆角 64dp 悬浮胶囊，贴合内容宽度，容器色 40% 垫底 + 真模糊 4dp + 饱和度 1.5×
 *   （Android 12+ 走 ImageView RenderEffect GPU 模糊，以下退化为半透明透出兜底）
 *   + 双光源高光（顶部亮白、底部暗）+ 1dp 白色描边 + 6dp 投影；
 * - 液滴：宽 = 正好一个 Tab 宽，高 56dp；内容 = 背景透镜透出 + 10% 纯色（静止淡出）+ 内容副本。
 *   API 33+ 用 KernelSU Lens.kt 同款 AGSL 折射 shader（按压时 refractionHeight/Amount 随 press
 *   增强，边缘折射放大背景 + 七色色散 + 深度法线）；<33 降级为 Matrix 定位 + blur 近似。
 *   按压/拖拽放大 78/56 ≈ 1.39×，内阴影 + 高光描边 + 图标文字 1.2× 放大副本；
 * - 动效：位置以 Tab 为单位追踪（弹簧 damping 1.0 / stiffness 1000，同 KernelSU value 弹簧），
 *   按压 scaleX 0.6/250、scaleY 0.7/250；点击与横向拖拽统一手势，松手吸附最近 Tab。
 */
@SuppressLint("NewApi")
@Composable
fun MiuiNavigationBar(
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    items: List<NavItem>,
    backdrop: ImageBitmap?,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val isDark = isSystemInDarkTheme()
    val pillHeight = 64.dp
    val pillPadding = 4.dp
    val dropHeight = 56.dp
    val accentColor = MiuiPrimary
    val containerColor = if (isDark) Color(0xFF2B2F36).copy(alpha = 0.40f)
    else MiuiBackground.copy(alpha = 0.40f)

    val scope = rememberCoroutineScope()

    // Tab 固定等宽 72dp（KernelSU 液滴宽度同款）：Tab 宽 / 液滴宽 / 液滴位置 / 点击命中
    // 全部由同一常量决定，无测量推算环节，真机结果确定
    val tabWidthDp = 72.dp
    val tabWidthPx = with(density) { tabWidthDp.toPx() }
    val pillPaddingPx = with(density) { pillPadding.toPx() }
    var rowWidthPx by remember { mutableFloatStateOf(0f) }
    // Row 在窗口中的左缘（backdrop 是全屏截图，液滴取样要用窗口坐标定位）
    var rowRootX by remember { mutableFloatStateOf(0f) }

    // 位置以 Tab 为单位（KernelSU DampedDragAnimation.value：damping 1.0 / stiffness 1000）
    val position = remember { Animatable(selectedIndex.toFloat()) }
    // 按压进度 0..1（KernelSU press：damping 1.0 / stiffness 1000）
    val press = remember { Animatable(0f) }
    val dropScaleX = remember { Animatable(1f) }
    val dropScaleY = remember { Animatable(1f) }

    var dragOffsetPx by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    val viewConfig = LocalViewConfiguration.current

    LaunchedEffect(selectedIndex) {
        position.animateTo(
            selectedIndex.toFloat(),
            spring(dampingRatio = 1f, stiffness = 1000f)
        )
    }

    fun setPressed(on: Boolean) {
        scope.launch {
            press.animateTo(if (on) 1f else 0f, spring(dampingRatio = 1f, stiffness = 1000f))
            dropScaleX.animateTo(if (on) 78f / 56f else 1f, spring(dampingRatio = 0.6f, stiffness = 250f))
            dropScaleY.animateTo(if (on) 78f / 56f else 1f, spring(dampingRatio = 0.7f, stiffness = 250f))
        }
    }

    fun indexAt(px: Float): Int {
        if (tabWidthPx <= 0f) return selectedIndex
        return ((px - pillPaddingPx) / tabWidthPx).toInt()
            .coerceIn(0, items.size - 1)
    }

    // 液滴位置 = 索引 × tabWidth（KernelSU dropX = value * tabWidthPx + panelOffset）
    // 液滴盒 padding 4dp 负责对齐 Row padding，故此处不含 pillPadding
    val shownIndex = if (dragging) {
        (position.value + dragOffsetPx / tabWidthPx)
            .coerceIn(0f, (items.size - 1).toFloat())
    } else {
        position.value
    }
    val dropIndex = shownIndex.roundToInt().coerceIn(0, items.size - 1)
    val dropX = shownIndex * tabWidthPx

    Box(
        modifier = modifier
            .navigationBarsPadding()
            .padding(top = 6.dp, bottom = 12.dp)
            .height(pillHeight)
    ) {
        // ===== 玻璃背景层：backdrop 真模糊 + 饱和度 1.5（Android 12+ GPU / 以下半透明兜底） =====
        // 宽度 = Row 实测总宽（同一基准），初始 0，测量后跟随
        val rowWidthDp = if (rowWidthPx > 0f) with(density) { rowWidthPx.toDp() } else 0.dp
        if (backdrop != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val blurRadius = with(density) { 4.dp.toPx() }
                AndroidView(
                    modifier = Modifier
                        .width(rowWidthDp)
                        .height(pillHeight)
                        .clip(CircleShape),
                    factory = { ctx ->
                        ImageView(ctx).apply {
                            scaleType = ImageView.ScaleType.FIT_XY
                            setRenderEffect(
                                RenderEffect.createBlurEffect(
                                    blurRadius, blurRadius, Shader.TileMode.CLAMP
                                )
                            )
                            setColorFilter(
                                ColorMatrixColorFilter(
                                    ColorMatrix().apply { setSaturation(1.5f) }
                                )
                            )
                        }
                    },
                    update = { iv ->
                        iv.setImageBitmap(backdrop.asAndroidBitmap())
                    }
                )
            } else {
                Image(
                    bitmap = backdrop,
                    contentDescription = null,
                    modifier = Modifier
                        .width(rowWidthDp)
                        .height(pillHeight)
                        .clip(CircleShape),
                    filterQuality = FilterQuality.Low,
                    alpha = 0.45f
                )
            }
        }

        // ===== Tab 行：玻璃视觉（背景/高光/描边/投影）与手势都画在这一层 =====
        // 与 pill 同一基准，点击区域不再被圆角裁剪，4 个 Tab 全部可点
        Row(
            modifier = Modifier
                .onGloballyPositioned {
                    rowWidthPx = it.size.width.toFloat()
                    rowRootX = it.positionInRoot().x
                }
                .clip(CircleShape)
                .background(containerColor)
                .drawBehind {
                    // 顶部高光（曲面玻璃受光）
                    drawRect(
                        brush = Brush.verticalGradient(
                            0f to Color.White.copy(alpha = 0.30f),
                            0.35f to Color.White.copy(alpha = 0.08f),
                            1f to Color.Black.copy(alpha = if (isDark) 0.06f else 0.03f)
                        )
                    )
                    // 边缘切面亮边
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.18f),
                        topLeft = Offset(1f, 1f),
                        size = Size(size.width - 2f, size.height - 2f),
                        cornerRadius = CornerRadius(size.height / 2f, size.height / 2f),
                        style = Stroke(width = 1f)
                    )
                }
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
                                // 抬起（up / cancel 统一按 pressed=false 结束）
                                setPressed(false)
                                if (dragMode) {
                                    if (tabWidthPx > 0f) {
                                        val target = ((position.value + totalX / tabWidthPx).roundToInt())
                                            .coerceIn(0, items.size - 1)
                                        // 无论吸附到哪个 Tab，position 都要复位跟随，否则液滴停在拖拽中间位置
                                        if (target != selectedIndex) onItemSelected(target)
                                        scope.launch {
                                            position.animateTo(
                                                target.toFloat(),
                                                spring(dampingRatio = 1f, stiffness = 1000f)
                                            )
                                        }
                                    }
                                } else {
                                    val idx = indexAt(change.position.x)
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
                val contentColor = if (selected) accentColor else Color(0xFF8A94A6)
                Column(
                    modifier = Modifier
                        .width(tabWidthDp)
                        .fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically)
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label,
                        tint = contentColor,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = contentColor,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }

        // ===== 液滴层：宽=一 Tab（72dp 规则胶囊）、高 56dp =====
        // KernelSU 液滴 = drawBackdrop(combinedBackdrop) + lens shader（AGSL 折射）+ onDrawSurface + innerShadow + pillHighlight + 内容副本
        // API 33+：AGSL RuntimeShader（KernelSU Lens.kt 同款折射+色散+深度）直接取样 backdrop 位图；
        // <33 降级：Matrix 定位 + blur 近似
        if (tabWidthPx > 0f) {
            val pressValue = press.value
            val lensShader = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                remember { android.graphics.RuntimeShader(LENS_REFRACTION_SHADER) }
            } else null
            Box(
                modifier = Modifier
                    .padding(horizontal = pillPadding)
                    .graphicsLayer {
                        translationX = dropX
                        // 垂直居中：父 Box 内容区 64dp，液滴 56dp，顶部对齐时下移 4dp
                        translationY = 4.dp.toPx()
                        scaleX = dropScaleX.value
                        scaleY = dropScaleY.value
                    }
                    .width(tabWidthDp)
                    .height(dropHeight)
                    .clip(CircleShape)
            ) {
                // ① 透镜层：透出 backdrop 对应区域（液滴从此"透明"）
                if (backdrop != null) {
                    val lensScaleBase =
                        if (rowWidthPx > 0f) rowWidthPx / backdrop.width.toFloat() else 0f
                    if (lensScaleBase > 0f) {
                        val dropWidthPx = with(density) { tabWidthDp.toPx() }
                        val dropHeightPx = with(density) { dropHeight.toPx() }
                        if (lensShader != null) {
                            // AGSL 折射透镜（KernelSU Lens.kt 机制）
                            AndroidView(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape),
                                factory = { ctx -> ImageView(ctx) },
                                update = { iv ->
                                    if (iv.tag !== backdrop) {
                                        iv.setImageBitmap(backdrop.asAndroidBitmap())
                                        lensShader.setInputShader(
                                            "content",
                                            BitmapShader(
                                                backdrop.asAndroidBitmap(),
                                                Shader.TileMode.CLAMP,
                                                Shader.TileMode.CLAMP
                                            )
                                        )
                                        // RenderEffect 只在此（背景变化）创建一次；
                                        // RuntimeShader 可变，拖拽/按压期间仅更新 uniform，不重建
                                        iv.setRenderEffect(
                                            RenderEffect.createRuntimeShaderEffect(
                                                lensShader, "content"
                                            )
                                        )
                                        iv.tag = backdrop
                                    }
                                    // ===== 轻量参数：只更新 uniform，每帧开销小（跟手） =====
                                    // backdrop 是全屏截图（1:1 窗口像素）→ scaleFactor=1，
                                    // 液滴取样区域 = Row 窗口左缘 + pillPadding + dropX（等大透出正下方背景）
                                    // 垂直：strip 底 = pill 底、高 88dp（MainScaffold 保证），
                                    // 液滴顶对应 backdrop y = 88dp - (pill高64dp - 液滴偏移4dp) = 28dp
                                    val rh = with(density) { (10.dp * pressValue).toPx() }
                                    val ra = with(density) { (14.dp * pressValue).toPx() }
                                    val r = with(density) { dropHeight.toPx() } / 2f
                                    lensShader.setFloatUniform("size", dropWidthPx, dropHeightPx)
                                    lensShader.setFloatUniform("offset", 0f, 0f)
                                    lensShader.setFloatUniform("cornerRadii", r, r, r, r)
                                    lensShader.setFloatUniform("refractionHeight", rh)
                                    lensShader.setFloatUniform("refractionAmount", ra)
                                    lensShader.setFloatUniform("depthEffect", 1f)
                                    lensShader.setFloatUniform("chromaticAberration", 0.5f)
                                    lensShader.setFloatUniform("scaleFactor", 1f)
                                    lensShader.setFloatUniform(
                                        "baseOffsetX", rowRootX + pillPaddingPx + dropX
                                    )
                                    lensShader.setFloatUniform(
                                        "baseOffsetY", with(density) { 28.dp.toPx() }
                                    )
                                }
                            )
                        } else {
                            // 降级（API < 33）：Matrix 定位 + blur 近似
                            val lensBlurRadius = with(density) { 2.dp.toPx() }
                            AndroidView(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape),
                                factory = { ctx ->
                                    ImageView(ctx).apply {
                                        scaleType = ImageView.ScaleType.MATRIX
                                    }
                                },
                                update = { iv ->
                                    if (iv.tag !== backdrop) {
                                        iv.setImageBitmap(backdrop.asAndroidBitmap())
                                        iv.setRenderEffect(
                                            RenderEffect.createBlurEffect(
                                                lensBlurRadius, lensBlurRadius,
                                                Shader.TileMode.CLAMP
                                            )
                                        )
                                        iv.tag = backdrop
                                    }
                                    // 同 AGSL：backdrop 全屏 1:1，等大取样 + 按压轻微放大；
                                    // 平移 = Row 窗口左缘 + pillPadding + dropX（水平），28dp（垂直）
                                    val s = 1f + 0.15f * pressValue
                                    val m = Matrix()
                                    m.postScale(s, s)
                                    m.postTranslate(
                                        -s * (rowRootX + pillPaddingPx + dropX),
                                        -s * with(density) { 28.dp.toPx() }
                                    )
                                    iv.imageMatrix = m
                                }
                            )
                        }
                    }
                }
                // ② 覆盖层：静止 10% 纯色（按压淡出）+ 按压内阴影/色散 + 液滴玻璃高光描边
                // （独立 Box 确保画在透镜层之上）
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .drawBehind {
                            drawRect(
                                color = if (!isDark) Color.Black.copy(alpha = 0.10f)
                                else Color.White.copy(alpha = 0.10f),
                                alpha = 1f - pressValue
                            )
                            drawRect(Color.Black.copy(alpha = 0.03f * pressValue))
                            if (pressValue > 0.01f) {
                                // 按压内阴影（同 KernelSU InnerShadow）
                                drawRoundRect(
                                    color = Color.Black.copy(alpha = 0.15f * pressValue),
                                    topLeft = Offset(1f, 1f),
                                    size = Size(size.width - 2f, size.height - 2f),
                                    cornerRadius = CornerRadius(size.height / 2f, size.height / 2f),
                                    style = Stroke(width = 6f)
                                )
                                // 红青边缘色散（模拟 lens chromaticAberration）
                                drawRoundRect(
                                    color = Color(0xFFFF5A5A).copy(alpha = 0.30f * pressValue),
                                    topLeft = Offset(2f, 2f),
                                    size = Size(8f, size.height - 4f),
                                    cornerRadius = CornerRadius(size.height / 2f, size.height / 2f),
                                    style = Stroke(width = 4f)
                                )
                                drawRoundRect(
                                    color = Color(0xFF4AC8FF).copy(alpha = 0.30f * pressValue),
                                    topLeft = Offset(size.width - 10f, 2f),
                                    size = Size(8f, size.height - 4f),
                                    cornerRadius = CornerRadius(size.height / 2f, size.height / 2f),
                                    style = Stroke(width = 4f)
                                )
                            }
                            // 液滴玻璃高光描边（静止轻微可见、按压增强，同 KernelSU pillHighlight）
                            val edgeAlpha = 0.35f + 0.55f * pressValue
                            drawRoundRect(
                                color = Color.White.copy(alpha = edgeAlpha),
                                topLeft = Offset(1f, 1f),
                                size = Size(size.width - 2f, size.height - 2f),
                                cornerRadius = CornerRadius(size.height / 2f, size.height / 2f),
                                style = Stroke(width = 1f)
                            )
                            drawRoundRect(
                                brush = Brush.verticalGradient(
                                    0f to Color.White.copy(alpha = 0.25f * edgeAlpha),
                                    0.4f to Color.White.copy(alpha = 0.05f * edgeAlpha),
                                    1f to Color.Transparent
                                ),
                                topLeft = Offset(1f, 1f),
                                size = Size(size.width - 2f, size.height - 2f),
                                cornerRadius = CornerRadius(size.height / 2f, size.height / 2f),
                                style = Stroke(width = 6f)
                            )
                        }
                )
                // ③ 选中 Tab 放大副本（图标 + 文字，按压时淡入 1.2×，同 KernelSU 标签副本）
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
                    modifier = Modifier.graphicsLayer {
                        scaleX = 1f + 0.2f * pressValue
                        scaleY = 1f + 0.2f * pressValue
                        alpha = pressValue
                    }
                ) {
                    Icon(
                        imageVector = items[dropIndex].icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = items[dropIndex].label,
                        style = MaterialTheme.typography.labelMedium,
                        color = accentColor,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
