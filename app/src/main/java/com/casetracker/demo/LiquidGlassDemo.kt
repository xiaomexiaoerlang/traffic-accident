package com.casetracker.demo

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.emptyBackdrop
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
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 液态玻璃底栏 Demo（视觉对齐 KernelSU 管理器 / Kyant0 AndroidLiquidGlass 官方 LiquidBottomTabs）。
 *
 * 结构：
 * - MainScreen    ：背板源（可滚动法规列表）+ 悬浮玻璃底栏的组合页
 * - LawList       ：顶部标题 + 搜索框 + 法规卡片列表（layerBackdrop 录制为玻璃采样源）
 * - GlassBottomBar：玻璃条容器 + 内容副本层 + 液滴（三层 drawBackdrop）
 * - TabItem       ：单个 Tab 图标 + 文字
 *
 * 动效与 ksu 同款：
 * - 点击切换：液滴 spring 滑动，滑动期间按压放大 78/56 且折射强度跟随 progress
 * - 液滴可拖拽：按下放大跟手，松手吸附最近 Tab
 *
 * 兼容降级（由 backdrop 库内部判定，无需手写 SDK_INT）：
 * - API >= 33：blur + vibrancy + lens（AGSL 折射 + 色散）全管线
 * - API 31-32：blur + vibrancy（RenderEffect），lens 自动跳过
 * - API < 31 ：仅容器色 + 细边框（纯半透明底色）
 */
@Composable
fun MainScreen(modifier: Modifier = Modifier) {
    var selectedTab by rememberSaveable { mutableIntStateOf(2) } // 默认「法规」（对齐参考截图）
    val tabs = remember {
        listOf(
            BottomTabItem(Icons.Default.BarChart, "进程"),
            BottomTabItem(Icons.Default.Schedule, "案件"),
            BottomTabItem(Icons.Default.Info, "法规"),
            BottomTabItem(Icons.Default.Settings, "设置")
        )
    }

    // 背板：API >= 31 用实时层录制（列表滚动时玻璃内折射实时跟随）；< 31 用空背板（纯色降级）
    val renderSupported = isRenderEffectSupported()
    val pageBackdrop = if (renderSupported) rememberLayerBackdrop() else emptyBackdrop()

    Box(modifier = modifier.fillMaxSize()) {
        // ===== 页面内容（玻璃采样源，layerBackdrop 实时捕获）=====
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (renderSupported) Modifier.layerBackdrop(pageBackdrop as com.kyant.backdrop.backdrops.LayerBackdrop) else Modifier)
        ) {
            when (selectedTab) {
                2 -> LawList(modifier = Modifier.padding(bottom = 84.dp))
                else -> PlaceholderPage(tabs[selectedTab].label)
            }
        }

        // ===== 悬浮玻璃底栏 =====
        GlassBottomBar(
            selectedTabIndex = selectedTab,
            onTabSelected = { selectedTab = it },
            backdrop = pageBackdrop,
            tabs = tabs,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

/** 底部导航数据项 */
data class BottomTabItem(val icon: ImageVector, val label: String)

/** ===== 法律法规列表页（可滚动，作为玻璃的实时背板源）===== */
@Composable
fun LawList(modifier: Modifier = Modifier) {
    val laws = remember {
        listOf(
            Law("中华人民共和国道路交通安全法", "共124条·8章"),
            Law("中华人民共和国道路交通安全法实施条例", "共115条·8章"),
            Law("道路交通事故处理程序规定", "共114条·12章"),
            Law("合肥市道路交通安全条例", "共68条·7章"),
            Law("合肥市非机动车安全管理条例", "共34条·7章"),
            Law("安徽省电动自行车管理条例", "共49条·7章")
        )
    }

    Column(modifier = modifier.fillMaxSize()) {
        // 顶部标题
        Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp)) {
            Text("法律法规", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                "道路交通事故处理相关法规（时效已核查至2026年10月）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            // 搜索框（静态样式；接入真搜索时替换为 TextField）
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth().height(44.dp)
            ) {
                Box(contentAlignment = Alignment.CenterStart, modifier = Modifier.padding(horizontal = 14.dp)) {
                    Text(
                        "搜索法条全文/标题（空格拆词·支持错字容错）",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // 法规卡片列表（可滚动）
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(laws) { law ->
                LawCard(law)
            }
        }
    }
}

private data class Law(val title: String, val meta: String)

@Composable
private fun LawCard(law: Law) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    law.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    law.meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                "点击查看 →",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

@Composable
private fun PlaceholderPage(label: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("$label 页（示例占位）", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * ===== 液态玻璃底栏（对齐 ksu / 官方 LiquidBottomTabs）=====
 * 三层结构：
 *  1. 玻璃条容器   ：全宽 64dp 胶囊，blur(4dp) + vibrancy + lens(16/24) + 容器色 + 细边框
 *  2. 内容副本层   ：alpha=0 不可见，layerBackdrop 录制玻璃条 UI（白色 tint）→ 供液滴折射
 *  3. 液滴指示器   ：1 个 tab 宽，lens 折射 + Highlight + Shadow + InnerShadow，无底色（透明玻璃）
 */
@Composable
fun GlassBottomBar(
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    backdrop: Backdrop,
    tabs: List<BottomTabItem>,
    modifier: Modifier = Modifier
) {
    val isLightTheme = !isSystemInDarkTheme()
    val containerColor =
        if (isLightTheme) Color(0xFFFAFAFA).copy(alpha = 0.4f)
        else Color(0xFF121212).copy(alpha = 0.4f)
    // 未选中 Tab 图标/文字颜色（中性灰，无蓝色）
    val idleTint = if (isLightTheme) Color(0xFF5A6472) else Color(0xFF8A94A6)

    // 玻璃条自身 UI 的录制背板（仅 API >= 31 有意义）
    val tabsBackdrop = if (isRenderEffectSupported()) rememberLayerBackdrop() else null

    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        val tabWidthPx = with(density) { (maxWidth - 8.dp).toPx() / tabs.size }

        val position = remember { Animatable(selectedTabIndex.toFloat()) } // 液滴位置（单位：tab 索引）
        val press = remember { Animatable(0f) }                           // 按压进度 0→1（放大 + 折射联动）
        val scope = rememberCoroutineScope()

        // 外部选中变化（点击 Tab / 程序切换）→ 液滴滑动 + 按压脉冲
        LaunchedEffect(selectedTabIndex) {
            press.snapTo(1f)
            position.animateTo(
                selectedTabIndex.toFloat(),
                spring(dampingRatio = 1f, stiffness = 600f)
            )
            press.animateTo(0f, spring(dampingRatio = 1f, stiffness = 800f))
        }

        // ===== 1. 玻璃条容器 =====
        Row(
            modifier = Modifier
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { RoundedCornerShape(50) },
                    effects = {
                        // 完整 AGSL 管线（API >= 33）；31-32 blur+vibrancy；<31 仅容器色
                        blur(4.dp.toPx())
                        vibrancy()
                        lens(16.dp.toPx(), 24.dp.toPx())
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
            tabs.forEachIndexed { index, item ->
                TabItem(
                    icon = item.icon,
                    label = item.label,
                    tint = idleTint,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { onTabSelected(index) }
                )
            }
        }

        // ===== 2. 内容副本层（不可见，白色 tint，供液滴折射出高亮文字）=====
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
                            lens(16.dp.toPx() * p, 24.dp.toPx() * p)
                        },
                        onDrawSurface = { drawRect(containerColor) }
                    )
                    .height(56.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEachIndexed { index, item ->
                    TabItem(
                        icon = item.icon,
                        label = item.label,
                        tint = Color.White,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                }
            }
        }

        // ===== 3. 液滴指示器（透明玻璃透镜，无染色）=====
        val dropletBackdrop = if (tabsBackdrop != null) {
            rememberCombinedBackdrop(backdrop, tabsBackdrop)
        } else {
            backdrop
        }

        Box(
            modifier = Modifier
                .padding(horizontal = 4.dp)
                .graphicsLayer { translationX = position.value * tabWidthPx }
                .drawBackdrop(
                    backdrop = dropletBackdrop,
                    shape = { RoundedCornerShape(50) },
                    effects = {
                        val p = press.value
                        lens(
                            refractionHeight = 10.dp.toPx() * p,
                            refractionAmount = 14.dp.toPx() * p,
                            chromaticAberration = true
                        )
                    },
                    highlight = {
                        val p = press.value
                        Highlight.Default.copy(alpha = p)
                    },
                    shadow = {
                        val p = press.value
                        Shadow(alpha = p)
                    },
                    innerShadow = {
                        val p = press.value
                        InnerShadow(radius = 8.dp * p, alpha = p)
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
                        // 极低底色保证可读性，按压时减弱（玻璃更透）
                        drawRect(
                            if (isLightTheme) Color.Black.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.10f),
                            alpha = 1f - p
                        )
                        drawRect(Color.Black.copy(alpha = 0.03f * p))
                    }
                )
                .height(56.dp)
                .fillMaxWidth(1f / tabs.size)
                .pointerInput(tabs.size, tabWidthPx) {
                    // 液滴拖拽：按下放大跟手，松手吸附最近 Tab
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
                                    .coerceIn(0, tabs.size - 1)
                                if (target != selectedTabIndex) {
                                    onTabSelected(target) // LaunchedEffect 负责吸附动画
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
                            if (abs(dragTotal) > viewConfiguration.touchSlop) {
                                change.consume()
                                dragTotal += change.positionChange().x
                                scope.launch {
                                    position.snapTo(
                                        (position.value + change.positionChange().x / tabWidthPx)
                                            .coerceIn(0f, (tabs.size - 1).toFloat())
                                    )
                                }
                            } else {
                                dragTotal += change.positionChange().x
                            }
                        }
                    }
                }
        )
    }
}

/** 单个 Tab：图标 + 文字 */
@Composable
fun TabItem(
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
            maxLines = 1
        )
    }
}
