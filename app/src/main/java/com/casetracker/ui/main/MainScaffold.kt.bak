package com.casetracker.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.casetracker.ui.accident.AccidentScreen
import com.casetracker.ui.home.HomeScreen
import com.casetracker.ui.law.LawLibraryScreen
import com.casetracker.ui.settings.SettingsScreen
import kotlinx.coroutines.launch

private data class MainTab(
    val label: String,
    val icon: ImageVector
)

private val TABS = listOf(
    MainTab("事故", Icons.Default.DateRange),
    MainTab("案件", Icons.Default.List),
    MainTab("法律法规", Icons.Default.Scale),
    MainTab("设置", Icons.Default.Settings)
)

/**
 * 4 Tab 主框架：横向 Pager 滑动实时跟手、相邻页面无割裂。
 * 在主界面按返回键弹出退出确认，避免误触退出。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainScaffold(
    onOpenCase: (Long) -> Unit,
    onCreateCase: () -> Unit,
    onEditCase: (Long) -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { TABS.size })
    val scope = rememberCoroutineScope()
    var showExitDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

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

    Column(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f)
        ) { page ->
            when (page) {
                0 -> AccidentScreen(onOpenCase = onOpenCase)
                1 -> HomeScreen(
                    onOpenCase = onOpenCase,
                    onEditCase = onEditCase,
                    onCreateCase = onCreateCase
                )
                2 -> LawLibraryScreen()
                3 -> SettingsScreen()
            }
        }

        NavigationBar(
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            TABS.forEachIndexed { index, tab ->
                NavigationBarItem(
                    selected = pagerState.currentPage == index,
                    onClick = {
                        scope.launch { pagerState.animateScrollToPage(index) }
                    },
                    icon = {
                        Icon(
                            tab.icon,
                            contentDescription = tab.label,
                            modifier = Modifier.padding(top = 0.dp)
                        )
                    },
                    label = { Text(tab.label) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            }
        }
    }
}
