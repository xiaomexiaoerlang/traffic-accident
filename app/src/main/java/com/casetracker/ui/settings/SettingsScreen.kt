package com.casetracker.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.casetracker.data.SettingsManager
import com.casetracker.theme.DangerColor
import com.casetracker.ui.components.MiuiCard
import com.casetracker.ui.components.MiuiTopAppBar

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onBack: (() -> Unit)? = null,
    onOpenOverview: (() -> Unit)? = null
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    // v1.85 事故台账导入：系统文件选择器选 .xlsx
    val ledgerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.onEvent(SettingsUiEvent.ImportLedger(uri))
    }
    // 历史优化日志整体折叠进一个卡片，点击查看；版本条目内仍可展开
    var logsExpanded by remember { mutableStateOf(false) }
    // 已展开的版本日志（默认展开最新版本）
    var expandedLogs by remember {
        mutableStateOf(setOf(VERSION_LOGS.firstOrNull()?.version ?: ""))
    }

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onEvent(SettingsUiEvent.ConsumeMessage)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            MiuiTopAppBar(
                title = { Text("设置", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = { onBack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 年度案件总览（v1.69）：按年度查看全部事故 / 违法案件记录
            item {
                Text("案件总览", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                MiuiCard {
                    SettingRow(
                        icon = { Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) },
                        title = "年度案件总览",
                        subtitle = "按年度查看全部事故 / 违法案件记录",
                        onClick = { onOpenOverview?.invoke() }
                    )
                }
            }

            // 通用设置
            item {
                Text("通用设置", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                MiuiCard {
                    SettingRow(
                        icon = { Icon(Icons.Default.DarkMode, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) },
                        title = "深色模式",
                        subtitle = uiState.themeMode.displayName,
                        onClick = { viewModel.onEvent(SettingsUiEvent.OpenThemeDialog) }
                    )
                    // v1.64：振触感反馈开关（点击全 App 短振动）
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Notifications, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("振触感反馈", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "点击操作时短振动提醒",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = uiState.hapticsEnabled,
                            onCheckedChange = { viewModel.onEvent(SettingsUiEvent.ToggleHaptics(it)) },
                            colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary)
                        )
                    }
                }
            }

            // 数据管理
            item {
                Text("数据管理", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                MiuiCard {
                    SettingRow(
                        icon = { Icon(Icons.Default.FileDownload, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) },
                        title = "导出数据",
                        subtitle = "导出为 CSV 到下载目录",
                        onClick = { viewModel.onEvent(SettingsUiEvent.ExportCsv) }
                    )
                    SettingRow(
                        icon = { Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) },
                        title = "导入事故台账",
                        subtitle = if (uiState.importing) "正在导入…" else "选择 .xlsx 台账批量生成已认定事故",
                        onClick = {
                            ledgerLauncher.launch(
                                arrayOf(
                                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                    "application/octet-stream"
                                )
                            )
                        }
                    )
                    SettingRow(
                        icon = { Icon(Icons.Default.Delete, null, tint = DangerColor, modifier = Modifier.size(20.dp)) },
                        title = "清除所有数据",
                        subtitle = "删除全部事故记录",
                        titleColor = DangerColor,
                        onClick = { viewModel.onEvent(SettingsUiEvent.OpenClearConfirm) }
                    )
                }
            }

            // 关于
            item {
                Text("关于", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                MiuiCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("版本", style = MaterialTheme.typography.bodyLarge)
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            uiState.versionName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 版本优化日志：折叠为一个卡片，点击展开查看全部历史
            item {
                Text("版本优化日志", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                MiuiCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { logsExpanded = !logsExpanded }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("历史优化日志", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "共 ${VERSION_LOGS.size} 个版本 · 点击${if (logsExpanded) "收起" else "查看"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            if (logsExpanded) Icons.Default.KeyboardArrowDown
                            else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = if (logsExpanded) "收起" else "展开",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            if (logsExpanded) {
                item {
                    MiuiCard {
                        VERSION_LOGS.forEachIndexed { index, log ->
                            val expanded = log.version in expandedLogs
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        expandedLogs = if (expanded) expandedLogs - log.version
                                                       else expandedLogs + log.version
                                    }
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(log.version, style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        log.date,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(
                                    if (expanded) Icons.Default.KeyboardArrowDown
                                    else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = if (expanded) "收起" else "展开",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (expanded) {
                                log.items.forEach { change ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 3.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Text(
                                            "·",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(end = 8.dp)
                                        )
                                        Text(
                                            change,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                            if (index != VERSION_LOGS.lastIndex) {
                                androidx.compose.material3.HorizontalDivider(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // 深色模式选择
    if (uiState.showThemeDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.onEvent(SettingsUiEvent.DismissThemeDialog) },
            title = { Text("深色模式") },
            text = {
                Column {
                    SettingsManager.ThemeMode.entries.forEach { mode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.onEvent(SettingsUiEvent.SetThemeMode(mode)) }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = uiState.themeMode == mode,
                                onClick = { viewModel.onEvent(SettingsUiEvent.SetThemeMode(mode)) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(mode.displayName, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.onEvent(SettingsUiEvent.DismissThemeDialog) }) { Text("完成") }
            }
        )
    }

    // 清除确认
    if (uiState.showClearConfirm) {
        AlertDialog(
            onDismissRequest = { viewModel.onEvent(SettingsUiEvent.DismissClearConfirm) },
            title = { Text("清除所有数据") },
            text = { Text("将删除全部事故记录和流程节点，此操作不可恢复。确定继续吗？") },
            confirmButton = {
                TextButton(onClick = { viewModel.onEvent(SettingsUiEvent.ClearAll) }) { Text("清除", color = DangerColor) }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onEvent(SettingsUiEvent.DismissClearConfirm) }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun SettingRow(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    titleColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon()
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = titleColor)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
