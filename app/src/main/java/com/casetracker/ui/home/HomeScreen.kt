package com.casetracker.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.casetracker.domain.model.Case
import com.casetracker.domain.model.CaseEvent
import com.casetracker.domain.model.CaseStatus
import com.casetracker.domain.model.CaseType
import com.casetracker.theme.DangerColor
import com.casetracker.theme.SuccessColor
import com.casetracker.theme.WarningColor
import com.casetracker.ui.components.MiuiCard
import com.casetracker.ui.components.MiuiProgressBar
import com.casetracker.ui.components.MiuiTopAppBar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    onOpenCase: (Long) -> Unit,
    onCreateCase: () -> Unit,
    onEditCase: (Long) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val navigation by viewModel.navigation.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState()
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(navigation) {
        when (val nav = navigation) {
            is HomeNavigation.ToDetail -> {
                onOpenCase(nav.caseId)
                viewModel.onNavigationConsumed()
            }
            is HomeNavigation.ToCreate -> {
                onCreateCase()
                viewModel.onNavigationConsumed()
            }
            is HomeNavigation.ToEdit -> {
                onEditCase(nav.caseId)
                viewModel.onNavigationConsumed()
            }
            null -> {}
        }
    }

    Scaffold(
        topBar = {
            MiuiTopAppBar(
                title = { Text("案件", style = MaterialTheme.typography.titleLarge) }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.onEvent(HomeUiEvent.NavigateToCreate) },
                containerColor = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "新建事故", tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 统计卡片（2 列网格）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatMiniCard("处理中", uiState.processingCount, MaterialTheme.colorScheme.primary, Icons.Default.Schedule, Modifier.weight(1f))
                StatMiniCard("临近到期", uiState.dueSoonCount, WarningColor, Icons.Default.Warning, Modifier.weight(1f))
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatMiniCard("已超期", uiState.overdueCount, DangerColor, Icons.Default.EventBusy, Modifier.weight(1f))
                StatMiniCard("已结案", uiState.closedCount, SuccessColor, Icons.Default.CheckCircle, Modifier.weight(1f))
            }

            // 状态筛选
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = uiState.filterStatus == null,
                    onClick = { viewModel.onEvent(HomeUiEvent.FilterByStatus(null)) },
                    label = { Text("全部", style = MaterialTheme.typography.labelMedium) }
                )
                CaseStatus.entries.forEach { status ->
                    FilterChip(
                        selected = uiState.filterStatus == status,
                        onClick = { viewModel.onEvent(HomeUiEvent.FilterByStatus(status)) },
                        label = { Text(status.displayName, style = MaterialTheme.typography.labelMedium) }
                    )
                }
            }

            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (uiState.cases.isEmpty()) {
                EmptyState()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.cases, key = { it.id }) { case ->
                        CaseCardItem(
                            case = case,
                            nextNode = uiState.nextNodeByCase[case.id],
                            onClick = { viewModel.onEvent(HomeUiEvent.OpenCaseDetail(case.id)) },
                            onLongPress = { viewModel.onEvent(HomeUiEvent.LongPressCase(case)) }
                        )
                    }
                }
            }
        }
    }

    // 长按操作面板
    uiState.longPressCase?.let { case ->
        ModalBottomSheet(
            onDismissRequest = { viewModel.onEvent(HomeUiEvent.DismissLongPress) },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(bottom = 32.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(case.caseNumber, style = MaterialTheme.typography.titleMedium)
                        Text(
                            case.title,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                SheetAction(
                    icon = { Icon(Icons.Default.Edit, null, tint = MaterialTheme.colorScheme.primary) },
                    label = "编辑事故",
                    onClick = { viewModel.onEvent(HomeUiEvent.EditCase(case.id)) }
                )
                if (case.status != CaseStatus.CLOSED) {
                    SheetAction(
                        icon = { Icon(Icons.Default.CheckCircle, null, tint = SuccessColor) },
                        label = "标记结案",
                        onClick = { viewModel.onEvent(HomeUiEvent.CloseCase(case.id)) }
                    )
                }
                SheetAction(
                    icon = { Icon(Icons.Default.Delete, null, tint = DangerColor) },
                    label = "删除事故",
                    danger = true,
                    onClick = {
                        viewModel.onEvent(HomeUiEvent.DismissLongPress)
                        showDeleteConfirm = true
                    }
                )
            }
        }
    }

    // 删除二次确认
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除事故") },
            text = { Text("确定要删除该事故吗？删除后不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    uiState.longPressCase?.let { viewModel.onEvent(HomeUiEvent.DeleteCase(it.id)) }
                    showDeleteConfirm = false
                }) { Text("删除", color = DangerColor) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun StatMiniCard(
    label: String,
    value: Int,
    color: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    MiuiCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Column {
                Text(
                    text = value.toString(),
                    style = MaterialTheme.typography.titleLarge,
                    color = color
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text("暂无事故记录", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "点击 + 添加第一个事故",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CaseCardItem(
    case: Case,
    nextNode: CaseEvent?,
    onClick: () -> Unit,
    onLongPress: () -> Unit
) {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val now = System.currentTimeMillis()
    val daysRemaining = case.daysRemaining(now)
    val isOverdue = case.isOverdue(now)

    MiuiCard(
        modifier = Modifier.combinedClickable(
            onClick = onClick,
            onLongClick = onLongPress
        )
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = case.caseType.displayName,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = case.caseNumber,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = case.status.displayName,
                style = MaterialTheme.typography.labelMedium,
                color = if (isOverdue) DangerColor else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = case.title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(4.dp))
        val dateTimeText = com.casetracker.domain.model.FormatUtils
            .accidentDateTime(case.accidentDate, case.accidentTime)
        Text(
            text = listOfNotNull(dateTimeText.ifBlank { null }, case.location.ifBlank { null }).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (case.caseType == CaseType.ACCIDENT) {
            Spacer(modifier = Modifier.height(4.dp))
            val extras = buildList {
                add(case.accidentType.displayName)
                add(case.procedure.displayName)
                if (case.hitAndRun) add("逃逸")
            }
            Text(
                text = extras.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${case.progress}%",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(8.dp))
            MiuiProgressBar(
                progress = case.progress / 100f,
                isOverdue = isOverdue,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (nextNode != null && case.status != CaseStatus.CLOSED) {
                Text(
                    text = "待办：${nextNode.title}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (nextNode.expectedDate > 0) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "预计 ${dateFormat.format(Date(nextNode.expectedDate))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Text(
                    text = "截止：${dateFormat.format(Date(case.deadline))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            if (isOverdue) {
                Text(
                    text = "已超期 ${-daysRemaining} 天",
                    style = MaterialTheme.typography.labelMedium,
                    color = DangerColor
                )
            } else if (daysRemaining in 0..3) {
                Text(
                    text = "剩余 $daysRemaining 天",
                    style = MaterialTheme.typography.labelMedium,
                    color = WarningColor
                )
            } else {
                Text(
                    text = "剩余 $daysRemaining 天",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SheetAction(
    icon: @Composable () -> Unit,
    label: String,
    onClick: () -> Unit,
    danger: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon()
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (danger) DangerColor else MaterialTheme.colorScheme.onSurface
        )
    }
}
