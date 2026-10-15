package com.casetracker.ui.detail

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.casetracker.domain.model.CaseStatus
import com.casetracker.domain.model.FlowNode
import com.casetracker.domain.model.NodeState
import com.casetracker.theme.DangerColor
import com.casetracker.theme.SuccessColor
import com.casetracker.theme.WarningColor
import com.casetracker.ui.components.MiuiButton
import com.casetracker.ui.components.MiuiCard
import com.casetracker.ui.components.MiuiProgressBar
import com.casetracker.ui.components.MiuiTopAppBar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaseDetailScreen(
    caseId: Long,
    viewModel: CaseDetailViewModel = hiltViewModel(),
    onBack: () -> Unit,
    onEdit: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val shouldGoBack by viewModel.shouldNavigateBack.collectAsStateWithLifecycle()
    val shouldGoEdit by viewModel.shouldNavigateToEdit.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(caseId) { viewModel.loadCase(caseId) }
    LaunchedEffect(shouldGoBack) { if (shouldGoBack) { onBack(); viewModel.onNavigationConsumed() } }
    LaunchedEffect(shouldGoEdit) { if (shouldGoEdit) { onEdit(); viewModel.onNavigationConsumed() } }
    LaunchedEffect(uiState.toastMessage) {
        uiState.toastMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onEvent(DetailUiEvent.ConsumeToast)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            MiuiTopAppBar(
                title = { Text("案件详情", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.onEvent(DetailUiEvent.NavigateBack) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.onEvent(DetailUiEvent.NavigateToEdit) }) {
                        Icon(Icons.Default.Edit, contentDescription = "编辑")
                    }
                    IconButton(onClick = { viewModel.onEvent(DetailUiEvent.DeleteCase) }) {
                        Icon(Icons.Default.Delete, contentDescription = "删除", tint = DangerColor)
                    }
                }
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            uiState.case?.let { case ->
                val dateFormat = SimpleDateFormat("yyyy年MM月dd日", Locale.getDefault())
                val now = System.currentTimeMillis()
                val daysRemaining = case.daysRemaining(now)
                val isOverdue = case.isOverdue(now)
                val nextNode = uiState.nodes.firstOrNull { it.state != NodeState.DONE }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 案件摘要卡片
                    item {
                        MiuiCard {
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
                                    color = if (isOverdue) DangerColor else MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = case.title,
                                style = MaterialTheme.typography.headlineSmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (case.accidentDate > 0) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "事故时间：${com.casetracker.domain.model.FormatUtils.accidentDateTime(case.accidentDate, case.accidentTime)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (case.caseType == com.casetracker.domain.model.CaseType.ACCIDENT) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = buildList {
                                        add(case.accidentType.displayName)
                                        add(case.procedure.displayName)
                                        if (case.hitAndRun) add("逃逸")
                                        if (case.appraisalExtended) add("鉴定延期")
                                    }.joinToString(" · "),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                                )
                            }
                            if (case.location.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "地点：${case.location}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (case.selfParty.isNotBlank() || case.otherParty.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = buildList {
                                        if (case.selfParty.isNotBlank()) add("本方：${case.selfParty}")
                                        if (case.otherParty.isNotBlank()) add("对方：${case.otherParty}")
                                    }.joinToString("　"),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (case.handlerUnit.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "处理单位：${case.handlerUnit}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (case.handlingOfficer.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "办案民警：${case.handlingOfficer}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (case.description.isNotBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = case.description,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    // 进度卡片
                    item {
                        val nodeProgress = if (uiState.nodes.isNotEmpty()) {
                            com.casetracker.domain.model.AccidentFlowTemplate.calculateProgress(uiState.nodes)
                        } else case.progress
                        MiuiCard {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("处理进度", style = MaterialTheme.typography.titleMedium)
                                Spacer(modifier = Modifier.weight(1f))
                                Text(
                                    text = "$nodeProgress%",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            MiuiProgressBar(progress = nodeProgress / 100f, isOverdue = isOverdue)
                            Spacer(modifier = Modifier.height(12.dp))
                            Row {
                                Text(
                                    text = "状态：${case.status.displayName}",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                if (isOverdue) {
                                    Text(
                                        text = "已超期 ${-daysRemaining} 天",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = DangerColor
                                    )
                                } else {
                                    Text(
                                        text = "剩余 $daysRemaining 天",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (daysRemaining <= 3) WarningColor
                                                else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "法定截止：${dateFormat.format(Date(case.deadline))}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (nextNode != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "下一节点：${nextNode.name}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                if (nextNode.expectedDate > 0) {
                                    Text(
                                        text = "预计完成：${dateFormat.format(Date(nextNode.expectedDate))}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            if (case.status != CaseStatus.CLOSED) {
                                val allDone = uiState.nodes.isNotEmpty() &&
                                    uiState.nodes.all { it.state == NodeState.DONE }
                                MiuiButton(
                                    text = if (allDone) "标记结案" else "推进到下一状态",
                                    onClick = {
                                        if (allDone) viewModel.onEvent(DetailUiEvent.CloseCase)
                                        else viewModel.onEvent(DetailUiEvent.AdvanceStatus)
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                if (!allDone) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "完成全部节点后可标记结案",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // 12 节点流程时间线
                    if (uiState.nodes.isNotEmpty()) {
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("法定流程节点", style = MaterialTheme.typography.titleMedium)
                                Spacer(modifier = Modifier.weight(1f))
                                Text(
                                    "共 ${uiState.nodes.count { it.state == NodeState.DONE }}/${uiState.nodes.size} 完成",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        items(uiState.nodes, key = { it.code }) { node ->
                            FlowNodeItem(
                                node = node,
                                isLast = node.code == uiState.nodes.last().code,
                                onClick = { viewModel.onEvent(DetailUiEvent.ShowRecordDialog(node.code)) }
                            )
                        }
                    }

                    // 自定义记录
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("处理记录", style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.weight(1f))
                            IconButton(onClick = { viewModel.onEvent(DetailUiEvent.ShowAddEventDialog) }) {
                                Icon(Icons.Default.Add, contentDescription = "添加记录")
                            }
                        }
                    }
                    val customEvents = uiState.events.filter { it.nodeCode.isBlank() }
                    if (customEvents.isEmpty()) {
                        item {
                            Text(
                                "暂无额外记录",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    } else {
                        items(customEvents, key = { it.id }) { event ->
                            TimelineCard(event.title, event.description, event.occurredAt)
                        }
                    }
                }
            }
        }

        // 录入节点日期对话框
        uiState.recordNodeCode?.let { nodeCode ->
            val node = uiState.nodes.firstOrNull { it.code == nodeCode }
            var dateText by remember { mutableStateOf("") }
            val shortFormat = SimpleDateFormat("yyyy年MM月dd日", Locale.getDefault())
            AlertDialog(
                onDismissRequest = { viewModel.onEvent(DetailUiEvent.DismissRecordDialog) },
                title = { Text("录入：${node?.name ?: ""}") },
                text = {
                    Column {
                        if ((node?.expectedDate ?: 0L) > 0L) {
                            Text(
                                text = "预计日期：${shortFormat.format(Date(node!!.expectedDate))}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        OutlinedTextField(
                            value = dateText,
                            onValueChange = { dateText = it },
                            label = { Text("实际日期（如 2026-09-15）") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            )
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.onEvent(DetailUiEvent.RecordNodeDate(nodeCode, dateText))
                    }) { Text("确定") }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.onEvent(DetailUiEvent.DismissRecordDialog) }) {
                        Text("取消")
                    }
                }
            )
        }

        // 添加记录弹窗
        if (uiState.showAddEventDialog) {
            var eventTitle by remember { mutableStateOf("") }
            var eventDesc by remember { mutableStateOf("") }
            var isMilestone by remember { mutableStateOf(false) }

            AlertDialog(
                onDismissRequest = { viewModel.onEvent(DetailUiEvent.DismissAddEventDialog) },
                title = { Text("添加处理记录") },
                text = {
                    Column {
                        OutlinedTextField(
                            value = eventTitle,
                            onValueChange = { eventTitle = it },
                            label = { Text("记录标题") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            )
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = eventDesc,
                            onValueChange = { eventDesc = it },
                            label = { Text("记录描述") },
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            )
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.material3.Checkbox(
                                checked = isMilestone,
                                onCheckedChange = { isMilestone = it }
                            )
                            Text("标记为里程碑", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.onEvent(DetailUiEvent.AddEvent(eventTitle, eventDesc, isMilestone))
                    }) { Text("添加") }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.onEvent(DetailUiEvent.DismissAddEventDialog) }) {
                        Text("取消")
                    }
                }
            )
        }
    }
}

/** 流程节点条目 */
@Composable
private fun FlowNodeItem(
    node: FlowNode,
    isLast: Boolean,
    onClick: () -> Unit
) {
    val dateFormat = SimpleDateFormat("MM月dd日", Locale.getDefault())
    val stateColor = when (node.state) {
        NodeState.DONE -> SuccessColor
        NodeState.PENDING -> MaterialTheme.colorScheme.primary
        NodeState.OVERDUE -> DangerColor
        NodeState.NOT_STARTED -> MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    }
    val canClick = node.state == NodeState.PENDING || node.state == NodeState.OVERDUE

    Row(modifier = Modifier.padding(vertical = 2.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(if (node.isRequired) 16.dp else 12.dp)
                    .clip(CircleShape)
                    .background(stateColor)
            )
            if (!isLast) {
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(44.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(
            modifier = Modifier
                .padding(bottom = 6.dp)
                .fillMaxWidth()
                .then(if (canClick) Modifier.clickable(onClick = onClick) else Modifier)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = node.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = when (node.state) {
                        NodeState.NOT_STARTED -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                )
                if (node.isAuto) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "自动",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
                if (node.isRequired) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "必填",
                        style = MaterialTheme.typography.labelSmall,
                        color = DangerColor
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            if (node.hint.isNotBlank()) {
                Text(
                    text = node.hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                val actual = node.event?.occurredAt ?: 0L
                if (actual > 0) {
                    Text(
                        "已完成：${dateFormat.format(Date(actual))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = SuccessColor
                    )
                } else {
                    Text(
                        when (node.state) {
                            NodeState.PENDING -> "待录入"
                            NodeState.OVERDUE -> "已超期"
                            else -> "未开始"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = stateColor
                    )
                    if (node.expectedDate > 0) {
                        Text(
                            "  预计：${dateFormat.format(Date(node.expectedDate))}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (canClick) {
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        "录入 ›",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

/** 自定义记录卡片 */
@Composable
private fun TimelineCard(title: String, description: String, occurredAt: Long) {
    val dateFormat = SimpleDateFormat("MM月dd日 HH:mm", Locale.getDefault())
    MiuiCard {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            dateFormat.format(Date(occurredAt)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (description.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
