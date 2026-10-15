package com.casetracker.ui.accident

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.casetracker.domain.model.FlowNode
import com.casetracker.domain.model.NodeState
import com.casetracker.domain.model.FormatUtils
import com.casetracker.theme.DangerColor
import com.casetracker.theme.SuccessColor
import com.casetracker.ui.components.MiuiCard
import com.casetracker.ui.components.MiuiTextField
import com.casetracker.ui.components.MiuiTopAppBar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AccidentScreen(
    viewModel: AccidentViewModel = hiltViewModel(),
    onOpenCase: (Long) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.message) {
        if (uiState.message.isNotBlank()) {
            snackbarHostState.showSnackbar(uiState.message)
            viewModel.onMessageShown()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        MiuiTopAppBar(
            title = { Text("事故流程", style = MaterialTheme.typography.titleLarge) }
        )

        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (uiState.timelines.isEmpty()) {
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
                Text("暂无一般程序事故", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "在「案件」页创建一般程序事故后\n自动生成法定流程时间线",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            // 已完成流程默认折叠，用户手动展开的 id 集合
            var expandedIds by remember { mutableStateOf(setOf<Long>()) }
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(uiState.timelines, key = { it.case.id }) { timeline ->
                    val allDone = timeline.nodes.isNotEmpty() &&
                        timeline.nodes.all { it.state == NodeState.DONE }
                    if (allDone && timeline.case.id !in expandedIds) {
                        // 已完成：收纳为紧凑行
                        CompletedTimelineCard(
                            timeline = timeline,
                            onExpand = { expandedIds = expandedIds + timeline.case.id }
                        )
                    } else {
                        AccidentTimelineCard(
                            timeline = timeline,
                            collapsible = allDone,
                            onCollapse = { expandedIds = expandedIds - timeline.case.id },
                            onOpenCase = { onOpenCase(timeline.case.id) },
                            onCompleteNode = { node -> viewModel.completeNode(timeline.case.id, node) },
                            onRecordNodeDate = { node, dateText ->
                                viewModel.recordNodeByDate(timeline.case.id, node, dateText)
                            }
                        )
                    }
                }
            }
        }
        SnackbarHost(hostState = snackbarHostState)
    }
}

@Composable
private fun AccidentTimelineCard(
    timeline: AccidentTimeline,
    collapsible: Boolean,
    onCollapse: () -> Unit,
    onOpenCase: () -> Unit,
    onCompleteNode: (FlowNode) -> Unit,
    onRecordNodeDate: (FlowNode, String) -> Unit
) {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val progress = AccidentFlowProgress(timeline.nodes)
    // 当前待操作的节点 = 第一个未完成节点
    val firstPending = timeline.nodes.firstOrNull { it.state != NodeState.DONE }

    // 日期修改对话框状态
    var editingNode by remember { mutableStateOf<FlowNode?>(null) }
    var dialogDateText by remember { mutableStateOf("") }

    MiuiCard(
        modifier = Modifier.clickable(onClick = onOpenCase)
    ) {
        // 头部
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = timeline.case.caseNumber,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "${progress}%",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.weight(1f))
            val doneCount = timeline.nodes.count { it.state == NodeState.DONE }
            Text(
                text = "$doneCount/${timeline.nodes.size} 环节",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (collapsible) {
                Spacer(modifier = Modifier.width(8.dp))
                androidx.compose.material3.TextButton(
                    onClick = onCollapse,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Text("收纳", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = FormatUtils
                .accidentDateTime(timeline.case.accidentDate, timeline.case.accidentTime)
                .ifBlank { "事故发生时间未填写" },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (timeline.case.location.isNotBlank()) {
            Text(
                text = timeline.case.location,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 7 环节时间轴（含右侧完成按钮/修改日期）
        timeline.nodes.forEach { node ->
            MiniTimelineRow(
                node = node,
                dateFormat = dateFormat,
                isCurrentPending = node.code == firstPending?.code,
                onComplete = { onCompleteNode(node) },
                onEditDate = {
                    editingNode = node
                    dialogDateText = if (node.event?.occurredAt ?: 0L > 0)
                        FormatUtils.shortDate(node.event!!.occurredAt) else ""
                }
            )
        }
    }

    // 日期修改对话框
    editingNode?.let { node ->
        AlertDialog(
            onDismissRequest = { editingNode = null },
            title = { Text("修改「${node.name}」日期") },
            text = {
                Column {
                    Text(
                        "修改后，后续环节的预计日期将自动重新计算。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    MiuiTextField(
                        value = dialogDateText,
                        onValueChange = { dialogDateText = it },
                        label = "实际日期",
                        placeholder = "例如：2026-09-10",
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onRecordNodeDate(node, dialogDateText)
                    editingNode = null
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { editingNode = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun MiniTimelineRow(
    node: FlowNode,
    dateFormat: SimpleDateFormat,
    isCurrentPending: Boolean,
    onComplete: () -> Unit,
    onEditDate: () -> Unit
) {
    val dotColor = when (node.state) {
        NodeState.DONE -> SuccessColor
        NodeState.OVERDUE -> DangerColor
        NodeState.PENDING -> MaterialTheme.colorScheme.primary
        NodeState.NOT_STARTED -> MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    }
    val lineColor = when (node.state) {
        NodeState.DONE -> SuccessColor
        NodeState.OVERDUE -> DangerColor
        NodeState.PENDING -> MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
        NodeState.NOT_STARTED -> MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
    }
    val isLast = node.code == "N6"
    val done = node.state == NodeState.DONE

    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(44.dp)
                        .background(lineColor)
                )
            }
        }
        Spacer(modifier = Modifier.width(10.dp))

        // 左侧：节点名 + 日期信息（点击修改日期）
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable { onEditDate() }
                .padding(vertical = 4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = node.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (node.state == NodeState.NOT_STARTED)
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    else MaterialTheme.colorScheme.onSurface
                )
                if (node.isAuto) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "自动",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            val actual = node.event?.occurredAt ?: 0L
            when {
                actual > 0 -> Text(
                    text = "实际：${dateFormat.format(Date(actual))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = SuccessColor,
                    fontWeight = FontWeight.Medium
                )
                node.state == NodeState.OVERDUE -> Text(
                    text = "已超期",
                    style = MaterialTheme.typography.labelSmall,
                    color = DangerColor
                )
                node.expectedDate > 0 -> Text(
                    text = "预计：${dateFormat.format(Date(node.expectedDate))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = when (node.state) {
                        NodeState.PENDING -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                else -> Text(
                    text = "待前置环节",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 右侧：完成按钮 / 已完成标签（统一靠右）
        Spacer(modifier = Modifier.width(8.dp))
        if (done) {
            FilledTonalButton(
                onClick = { onEditDate() },
                shape = CircleShape,
                modifier = Modifier.height(32.dp)
            ) {
                Text(
                    "已完成",
                    style = MaterialTheme.typography.labelMedium,
                    color = SuccessColor
                )
            }
        } else if (isCurrentPending) {
            FilledTonalButton(
                onClick = onComplete,
                shape = CircleShape,
                modifier = Modifier.height(32.dp)
            ) {
                Text(
                    "完成",
                    style = MaterialTheme.typography.labelMedium
                )
            }
        } else {
            // 未开始节点：提供修改入口（补录历史日期）
            OutlinedButton(
                onClick = { onEditDate() },
                shape = CircleShape,
                modifier = Modifier.height(32.dp)
            ) {
                Text(
                    "修改",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** 已完成流程的紧凑收纳卡片（一行） */
@Composable
private fun CompletedTimelineCard(
    timeline: AccidentTimeline,
    onExpand: () -> Unit
) {
    MiuiCard(onClick = onExpand) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(SuccessColor)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = timeline.case.caseNumber,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "已完成",
                style = MaterialTheme.typography.labelMedium,
                color = SuccessColor
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                "${timeline.nodes.size}/${timeline.nodes.size} 环节",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 简单进度计算 */
private fun AccidentFlowProgress(nodes: List<FlowNode>): Int {
    if (nodes.isEmpty()) return 0
    val done = nodes.count { it.state == NodeState.DONE }
    return (done.toFloat() / nodes.size * 100).toInt().coerceIn(0, 100)
}
