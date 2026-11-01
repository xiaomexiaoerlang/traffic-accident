package com.casetracker.demo

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.kyant.backdrop.isRuntimeShaderSupported
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * ============================================================
 * 需求 1 + 需求 2 完整实现：KernelSU 风格液态玻璃底部导航栏
 * ============================================================
 *
 * 依赖（二选一）：
 *   ① gradle 依赖（Kyant0/AndroidLiquidGlass，需在 build.gradle.kts 添加）：
 *        dependencies {
 *            implementation("io.github.kyant0:backdrop:1.2.1")   // 版本以 Maven Central 实际发布为准
 *            implementation("io.github.kyant0:shapes:1.2.1")
 *        }
 *   ② 源码集成（本项目采用）：将库源码拷入 app/src/main/java/com/kyant/backdrop/，
 *      无需额外依赖（见 build.gradle.kts 注释）。
 *
 * 需求 1 —— 液态玻璃：
 *   - 实时背板：rememberLayerBackdrop() 捕获内容层，滚动/滑动实时折射（非静态截图）；
 *   - 管线：blur(4dp) → vibrancy → AGSL lens(16/24)（边缘折射 + 色散）；
 *   - 玻璃条垫白/黑 0.4 + 1dp 细边框，液滴为透明透镜（Highlight + Shadow + InnerShadow）；
 *   - 兼容降级：API≥33（isRuntimeShaderSupported）启用 AGSL lens；
 *                 API 31–32 blur+vibrancy 走 RenderEffect 仍生效（lens 跳过）；
 *                 API<31 仅半透明容器色 + 细边框（emptyBackdrop）。
 *
 * 需求 2 —— 按压与滑动动画：
 *   - 按压：Tab 图标/文字 scale → 0.9，spring(DampingRatioMediumBouncy) 回弹 + 水波纹 ripple；
 *   - 指示器：位置 = pagerState.currentPage + currentPageOffsetFraction（逐帧跟手，无延迟）；
 *   - 选中效果：图标/文字按「到指示器的距离」平滑放大（1.12×）与颜色渐变（灰 → 白）；
 *   - 切换脉冲：currentPage 变化时液滴放大 78/56 + 折射增强后回落。
 *
 * 用法：
 *   val pageLayer = if (isRenderEffectSupported()) rememberLayerBackdrop() else null
 *   Box(Modifier.fillMaxSize()) {
 *       Box(Modifier.fillMaxSize().layerBackdrop(pageLayer)) { HorizontalPager(...) }
 *       LiquidGlassNavBar(pagerState, onPageSelected, tabs, backdrop = pageLayer ?: emptyBackdrop(),
 *           Modifier.align(Alignment.BottomCenter))
 *   }
 */

/** 单个 Tab 的数据 */
data class NavBarTab(
    val label: String,
    val icon: ImageVector
)

/**
 * 液态玻璃底部导航栏：玻璃条 + 白色副本层 + 透明液滴指示器（与 HorizontalPager 实时联动）。
 *
 * @param pagerState    HorizontalPager 的状态（驱动指示器跟手位移）
 * @param onPageSelected 点击 Tab 时回调（调用方 animateScrollToPage）
 * @param tabs           Tab 列表
 * @param backdrop       内容层背板（调用方创建 rememberLayerBackdrop 并绑定到 Pager 内容层）
 * @param modifier       外部修饰符（一般 align(BottomCenter)）
 */
@Composable
fun LiquidGlassNavBar(
    pagerState: PagerState,
    onPageSelected: (Int) -> Unit,
    tabs: List<NavBarTab>,
    backdrop: Backdrop,
    modifier: Modifier = Modifier
) {
    val isLightTheme = !isSystemInDarkTheme()
    val containerColor =
        if (isLightTheme) Color(0xFFFAFAFA).copy(alpha = 0.4f)
        else Color(0xFF121212).copy(alpha = 0.4f)
    val idleTint = if (isLightTheme) Color(0xFF5A6472) else Color(0xFF8A94A6)

    // 玻璃条自身 UI 的白色副本背板（API>=31 才录制；<31 液滴直接折射页面背板）
    val tabsBackdrop = if (isRenderEffectSupported()) rememberLayerBackdrop() else null

    // 需求 2：指示器位置与 Pager 滑动实时绑定（跟手，无延迟）
    //   currentPageOffsetFraction：滑动中连续变化（上一页方向为负、下一页方向为正）
    val indicatorFraction = pagerState.currentPage + pagerState.currentPageOffsetFraction

    val scope = rememberCoroutineScope()
    // 切换脉冲：点击 Tab / 滑动翻页后，液滴放大 + 折射增强再回落
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
        val tabWidthPx = with(density) { (maxWidth - 8.dp).toPx() / tabs.size }

        // ===== 1. 玻璃条容器（全宽 64dp 胶囊，blur + vibrancy + lens）=====
        Row(
            modifier = Modifier
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { RoundedCornerShape(50) },
                    effects = {
                        // blur/vibrancy：库内部按 API 用 RenderEffect（31+ 生效）
                        blur(4.dp.toPx())
                        vibrancy()
                        // AGSL lens：仅 API 33+；失败只丢折射，不影响整条管线
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
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEachIndexed { index, tab ->
                // 需求 2：该 Tab 的"活跃度" = 1 - 距指示器的距离（滑动中连续变化）
                val activeFraction = (1f - abs(index - indicatorFraction)).coerceIn(0f, 1f)
                TabContent(
                    icon = tab.icon,
                    label = tab.label,
                    activeFraction = activeFraction,
                    tint = lerp(idleTint, Color.White, activeFraction),
                    onClick = { onPageSelected(index) },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
            }
        }

        // ===== 2. 白色内容副本层（不可见，供液滴折射出白色高亮文字）=====
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
                                runCatching { lens(16.dp.toPx() * press.value, 24.dp.toPx() * press.value) }
                            }
                        },
                        onDrawSurface = { drawRect(containerColor) }
                    )
                    .height(56.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEachIndexed { index, tab ->
                    StaticTabContent(
                        icon = tab.icon,
                        label = tab.label,
                        tint = Color.White,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
            }
        }

        // ===== 3. 液滴指示器（透明玻璃透镜；graphicsLayer 逐帧跟手，不触发重组）=====
        val dropletBackdrop =
            if (tabsBackdrop != null) rememberCombinedBackdrop(backdrop, tabsBackdrop)
            else backdrop

        Box(
            modifier = Modifier
                .padding(horizontal = 4.dp)
                .graphicsLayer {
                    // 需求 2：位移 = (currentPage + offsetFraction) × tabWidth，滑动逐帧跟随
                    translationX = indicatorFraction * tabWidthPx
                    translationY = with(density) { 4.dp.toPx() }
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
                        // 极低底色保证可读性；按压时减弱（更透）——无任何 accent 染色
                        drawRect(
                            if (isLightTheme) Color.Black.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.10f),
                            alpha = 1f - p
                        )
                        drawRect(Color.Black.copy(alpha = 0.03f * p))
                    }
                )
                .height(56.dp)
                .fillMaxWidth(1f / tabs.size)
        )
    }
}

/**
 * 单个 Tab：按压 0.9 回弹（需求 2）+ 水波纹 + 选中平滑放大/颜色渐变。
 *
 * @param activeFraction 该 Tab 的活跃度 0..1（滑动中连续变化，驱动放大与颜色渐变）
 */
@Composable
private fun TabContent(
    icon: ImageVector,
    label: String,
    activeFraction: Float,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    // 需求 2：按压 → scale 0.9，松手 spring(DampingRatioMediumBouncy) 回弹
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.9f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "tabPressScale"
    )
    // 选中放大（最多 1.12×）+ 颜色渐变已由 tint 参数完成
    val scale = pressScale * lerp(1f, 1.12f, activeFraction)

    Column(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current // 水波纹 ripple
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
            fontWeight = if (activeFraction > 0.5f) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** 副本层用：白色静态 Tab 内容（无动画，避免重复触发） */
@Composable
private fun StaticTabContent(
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

// ================= 使用示例（可直接编译） =================

private val DEMO_TABS = listOf(
    NavBarTab("进程", Icons.Default.DateRange),
    NavBarTab("案件", Icons.Default.List),
    NavBarTab("法规", Icons.Default.Scale),
    NavBarTab("设置", Icons.Default.Settings)
)

private val DEMO_PAGES = listOf(
    listOf("处理进程一", "处理进程二", "处理进程三", "处理进程四", "处理进程五"),
    listOf("案件一", "案件二", "案件三", "案件四"),
    listOf("《道路交通安全法》", "《道路交通事故处理程序规定》", "《民法典》侵权责任编"),
    listOf("主题设置", "版本日志", "关于")
)

/**
 * 完整示例：HorizontalPager（4 页）+ 液态玻璃底栏（实时背板 + 跟手指示器）。
 * 列表上下滚动时，底栏玻璃内的折射实时跟随。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LiquidGlassNavDemo() {
    val pagerState = rememberPagerState(pageCount = { DEMO_TABS.size })
    val scope = rememberCoroutineScope()
    // 内容层背板（API>=31 才启用真实透镜；<31 自动降级为纯半透明）
    val pageLayer = if (isRenderEffectSupported()) rememberLayerBackdrop() else null
    val pageBackdrop: Backdrop = pageLayer ?: emptyBackdrop()

    Box(modifier = Modifier.fillMaxSize()) {
        // 内容层：Pager 画面实时录制进背板
        Box(
            modifier = if (pageLayer != null) {
                Modifier.fillMaxSize().layerBackdrop(pageLayer)
            } else {
                Modifier.fillMaxSize()
            }
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp)
                ) {
                    item {
                        Text(
                            text = DEMO_TABS[page].label + " 页",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                    }
                    items(DEMO_PAGES[page]) { title ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 2.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                        ) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }
            }
        }

        // 底部液态玻璃导航栏（与 Pager 实时联动跟手）
        LiquidGlassNavBar(
            pagerState = pagerState,
            onPageSelected = { index ->
                scope.launch { pagerState.animateScrollToPage(index) }
            },
            tabs = DEMO_TABS,
            backdrop = pageBackdrop,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}
