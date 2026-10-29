package com.casetracker.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.casetracker.ui.accident.AccidentScreen
import com.casetracker.ui.components.LiquidGlassBottomBarV2
import com.casetracker.ui.components.NavItem
import com.casetracker.ui.home.HomeScreen
import com.casetracker.ui.law.LawLibraryScreen
import com.casetracker.ui.settings.SettingsScreen
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.emptyBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.launch

private data class MainTab(
    val label: String,
    val icon: ImageVector
)

private val TABS = listOf(
    MainTab("进程", Icons.Default.DateRange),
    MainTab("案件", Icons.Default.List),
    MainTab("法律法规", Icons.Default.Scale),
    MainTab("设置", Icons.Default.Settings)
)

/**
 * 4 Tab 主框架：横向 Pager 滑动实时跟手、相邻页面无割裂。
 * 在主界面按返回键弹出退出确认，避免误触退出。
 *
 * v1.90：底部导航升级为 backdrop 库 AGSL 液态玻璃（纯 Compose，无 AndroidView）。
 * 背板 = rememberLayerBackdrop 实时层捕获：内容层 Modifier.layerBackdrop 绑定后，
 * 滚动/切换/动画实时刷新（无 View.draw 截图、无防抖延迟），
 * 液滴/pill 透出的就是内容层当前帧画面。API <31 自动降级为 emptyBackdrop。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainScaffold(
    onOpenCase: (Long) -> Unit,
    onCreateCase: () -> Unit,
    onEditCase: (Long) -> Unit,
    onOpenOverview: () -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { TABS.size })
    val scope = rememberCoroutineScope()
    var showExitDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    // v1.68 共享案件分类（交通事故 / 违法行为）：App 冷启动默认交通事故，跨 Tab 联动记忆
    var category by remember { mutableStateOf(com.casetracker.domain.model.CaseType.ACCIDENT) }

    // v1.90 实时层背板：GraphicsLayer 层捕获（Compose 1.7+），滚动/切换实时刷新
    // API >= 31（Android 12+）走真实透镜；以下用 emptyBackdrop（纯容器色降级）
    val pageLayer: com.kyant.backdrop.backdrops.LayerBackdrop? =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            rememberLayerBackdrop()
        } else {
            null
        }
    val pageBackdrop: Backdrop = pageLayer ?: emptyBackdrop()

    // 主界面拦截返回键，弹出退出确认
    BackHandler(enabled = true) { showExitDialog = true }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("退出应用") },
            text = { Text("是否退出案件跟进？") },
            confirmButton = {
                TextButton(onClick = {
                    showExitDialog = false
                    (context as? android.app.Activity)?.finishAffinity()
                }) { Text("退出") }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) { Text("取消") }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 内容层：pager 画面实时录制进背板（layerBackdrop 托管层捕获）
        Box(
            modifier = if (pageLayer != null) {
                Modifier
                    .fillMaxSize()
                    .layerBackdrop(pageLayer)
            } else {
                Modifier.fillMaxSize()
            }
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                // 预渲染相邻两页（覆盖全部 4 个 Tab），滑动时目标页已完成组合，彻底消除顿挫
                beyondViewportPageCount = 2
            ) { page ->
                when (page) {
                    0 -> AccidentScreen(
                        category = category,
                        onCategoryChange = { category = it },
                        onOpenCase = onOpenCase
                    )
                    1 -> HomeScreen(
                        category = category,
                        onCategoryChange = { category = it },
                        onOpenCase = onOpenCase,
                        onEditCase = onEditCase,
                        onCreateCase = onCreateCase
                    )
                    2 -> LawLibraryScreen()
                    3 -> SettingsScreen(onOpenOverview = onOpenOverview)
                }
            }
        }

        // 底部导航：液态玻璃悬浮胶囊 V2（v1.90.4 对齐 KernelSU 三层结构：全宽玻璃条 + 白色副本层 + 透明透镜液滴）
        LiquidGlassBottomBarV2(
            selectedIndex = pagerState.currentPage,
            onItemSelected = { index ->
                scope.launch { pagerState.animateScrollToPage(index) }
            },
            items = TABS.map { NavItem(it.label, it.icon) },
            backdrop = pageBackdrop,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}
