package com.casetracker.ui.overview

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.casetracker.domain.model.CaseType
import com.casetracker.theme.MiuiBackground
import com.casetracker.theme.MiuiPrimary
import com.casetracker.ui.components.MiuiCard
import com.casetracker.ui.components.MiuiTextField
import com.casetracker.ui.components.MiuiTopAppBar

// 澎湃 OS 风格状态胶囊配色
private val ToneGreenBg = Color(0xFFE4F7EE)
private val ToneGreenFg = Color(0xFF00A656)
private val ToneBlueBg = Color(0xFFE8F2FF)
private val ToneBlueFg = Color(0xFF1B7AE8)
private val ToneOrangeBg = Color(0xFFFFF2E0)
private val ToneOrangeFg = Color(0xFFF08C00)
private val ToneRedBg = Color(0xFFFFECEC)
private val ToneRedFg = Color(0xFFE5484D)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverviewScreen(
    viewModel: OverviewViewModel = hiltViewModel(),
    onBack: () -> Unit,
    onOpenCase: (Long) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    // 右上角三点菜单
    var menuExpanded by remember { mutableStateOf(false) }
    // v1.70 上传（导入）CSV 文件选择
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.onEvent(OverviewUiEvent.ImportCsv(it)) }
    }

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onEvent(OverviewUiEvent.ConsumeMessage)
        }
    }

    Scaffold(
        containerColor = MiuiBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            MiuiTopAppBar(
                title = { Text("年度案件总览", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "更多操作")
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("导出表格") },
                                onClick = {
                                    menuExpanded = false
                                    viewModel.onEvent(OverviewUiEvent.ExportCsv)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("上传表格") },
                                onClick = {
                                    menuExpanded = false
                                    importLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "application/csv", "*/*"))
                                }
                            )
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
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 年度选择行：左右切换 + 年份
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    IconButton(
                        onClick = { viewModel.onEvent(OverviewUiEvent.ShiftYear(-1)) },
                        enabled = uiState.year > uiState.minYear
                    ) {
                        Icon(
                            Icons.Default.ChevronLeft,
                            contentDescription = "上一年",
                            tint = if (uiState.year > uiState.minYear) MaterialTheme.colorScheme.onSurface
                                   else MaterialTheme.colorScheme.outlineVariant
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "${uiState.year}年",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "${uiState.totalCount} 起${if (uiState.category == CaseType.ACCIDENT) "事故" else "违法"}案件",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = { viewModel.onEvent(OverviewUiEvent.ShiftYear(1)) },
                        enabled = uiState.year < uiState.maxYear
                    ) {
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = "下一年",
                            tint = if (uiState.year < uiState.maxYear) MaterialTheme.colorScheme.onSurface
                                   else MaterialTheme.colorScheme.outlineVariant
                        )
                    }
                }
            }

            // 类型分栏：交通事故 / 违法行为（澎湃胶囊风格）
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(4.dp)
                ) {
                    listOf(CaseType.ACCIDENT to "交通事故", CaseType.VIOLATION to "违法行为").forEach { (type, label) ->
                        val selected = uiState.category == type
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(11.dp))
                                .background(if (selected) Color.White else Color.Transparent)
                                .clickable { viewModel.onEvent(OverviewUiEvent.SelectCategory(type)) }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                label,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (selected) ToneBlueFg else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 统计卡：总数 / 已认定·已结案 / 待处理·待处罚 / 超期
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatCell("总数", uiState.totalCount, Color(0xFF1B7AE8), Modifier.weight(1f))
                    StatCell(
                        if (uiState.category == CaseType.ACCIDENT) "已认定" else "已结案",
                        uiState.doneCount, Color(0xFF00A656), Modifier.weight(1f)
                    )
                    StatCell(
                        if (uiState.category == CaseType.ACCIDENT) "待处理" else "待处罚",
                        uiState.pendingCount, Color(0xFFF08C00), Modifier.weight(1f)
                    )
                    StatCell("超期", uiState.overdueCount, Color(0xFFE5484D), Modifier.weight(1f))
                }
            }

            // 搜索框
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MiuiTextField(
                        value = uiState.query,
                        onValueChange = { viewModel.onEvent(OverviewUiEvent.UpdateQuery(it)) },
                        label = "搜索",
                        placeholder = "当事人 / 案号 / 地点 / 案情",
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            if (uiState.isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            } else if (uiState.rows.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            if (uiState.query.isNotBlank()) "未找到匹配的案件"
                            else "${uiState.year}年暂无${if (uiState.category == CaseType.ACCIDENT) "事故" else "违法"}记录",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(uiState.rows, key = { it.case.id }) { row ->
                    OverviewCard(
                        row = row,
                        onClick = { onOpenCase(row.case.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun StatCell(label: String, value: Int, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .padding(vertical = 14.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            value.toString(),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun OverviewCard(row: OverviewRow, onClick: () -> Unit) {
    val (bg, fg) = when (row.tone) {
        OverviewTone.DONE -> ToneGreenBg to ToneGreenFg
        OverviewTone.PENDING -> ToneBlueBg to ToneBlueFg
        OverviewTone.REGISTERED -> ToneOrangeBg to ToneOrangeFg
        OverviewTone.OVERDUE -> ToneRedBg to ToneRedFg
    }
    MiuiCard(onClick = onClick) {
        // 标题行：状态胶囊 + 案号
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(bg)
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    row.statusLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = fg
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                row.case.caseNumber,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(10.dp))

        // 标题（时间 + 当事人）
        Text(
            row.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(6.dp))

        // 时间 / 当事人行
        Row(modifier = Modifier.fillMaxWidth()) {
            FieldText("时间", row.dateText, Modifier.weight(1f))
            FieldText("当事人", row.partiesText, Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(6.dp))

        // 地点 / 简要案情
        Text(
            row.descText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "地点：${row.locationText}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        // 底栏字段区
        Spacer(modifier = Modifier.height(10.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            row.fields.chunked(2).forEach { pair ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    pair.forEach { field ->
                        FieldText(field.label, field.value, Modifier.weight(1f))
                    }
                    if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun FieldText(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
