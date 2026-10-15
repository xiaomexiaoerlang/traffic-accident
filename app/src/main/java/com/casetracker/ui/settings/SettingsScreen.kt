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
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
    onBack: (() -> Unit)? = null
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onEvent(SettingsUiEvent.ConsumeMessage)
        }
    }

    Scaffold(
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
                    SettingRow(
                        icon = { Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) },
                        title = "法律依据",
                        subtitle = "查看道路交通事故处理相关法条",
                        onClick = { viewModel.onEvent(SettingsUiEvent.ShowLegal) }
                    )
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

    // 法律依据
    if (uiState.showLegalDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.onEvent(SettingsUiEvent.DismissLegal) },
            title = { Text("法律依据") },
            text = {
                Column {
                    LEGAL_BASIS.forEach { (law, content) ->
                        Text(law, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        Text(
                            content,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.onEvent(SettingsUiEvent.DismissLegal) }) { Text("关闭") }
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
