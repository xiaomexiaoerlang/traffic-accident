package com.casetracker.ui.accident

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.casetracker.domain.model.AccidentFlowTemplate
import com.casetracker.domain.model.FlowNode
import com.casetracker.domain.model.NodeState
import com.casetracker.domain.model.FormatUtils
import com.casetracker.domain.model.CaseType
import com.casetracker.domain.model.ViolationFlowTemplate
import com.casetracker.domain.model.derivedStatusText
import com.casetracker.theme.DeleteRed
import com.casetracker.theme.DangerColor
import com.casetracker.theme.EditBlue
import com.casetracker.theme.SuccessColor
import com.casetracker.theme.WarningColor
import com.casetracker.ui.components.ActionBlockButton
import com.casetracker.ui.components.MiuiCard
import com.casetracker.ui.components.MiuiTextField
import com.casetracker.ui.components.MiuiTopAppBar
import com.casetracker.ui.components.SwipeActionsBox
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AccidentScreen(
    viewModel: AccidentViewModel = hiltViewModel(),
    category: CaseType = CaseType.ACCIDENT,
    onCategoryChange: (CaseType) -> Unit = {},
    onOpenCase: (Long) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // v1.68 联动：进程页类型分栏切换（交通事故 / 违法行为），与「案件」页共享分类状态
    LaunchedEffect(category) {
        viewModel.onCategoryChange(category)
    }

    LaunchedEffect(uiState.navigateToCaseId) {
        uiState.navigateToCaseId?.let { caseId ->
            onOpenCase(caseId)
            viewModel.onNavigationConsumed()
        }
    }

    LaunchedEffect(uiState.message) {
        if (uiState.message.isNotBlank()) {
            snackbarHostState.showSnackbar(uiState.message)
            viewModel.onMessageShown()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        MiuiTopAppBar(
            title = { Text("处理进程", style = MaterialTheme.typography.titleLarge) }
        )

        // v1.68 类型分栏：交通事故 / 违法行为（联动「案件」页，切换后流程列表对应切换）
        Row(modifier = Modifier.fillMaxWidth()) {
            listOf(CaseType.ACCIDENT to "交通事故", CaseType.VIOLATION to "违法行为").forEach { (type, label) ->
                val selected = category == type
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onCategoryChange(type) }
                        .padding(top = 6.dp, bottom = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(if (selected) 0.36f else 0f)
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                if (selected) MaterialTheme.colorScheme.primary
                                else Color.Transparent
                            )
                    )
                }
            }
        }

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
                Text(
                    if (category == CaseType.ACCIDENT) "暂无一般程序事故"
                    else "暂无违法案件",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    if (category == CaseType.ACCIDENT) "在「案件」页创建一般程序事故后\n自动生成法定流程时间线"
                    else "在「案件」页创建违法案件后\n自动生成法定流程时间线",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            // 展开/收纳状态：进行中默认展开，已认定/已完成默认收纳；点击卡片切换，长按进案件详情
            val defaultExpanded = uiState.timelines
                .filter { tl ->
                    tl.case.status != com.casetracker.domain.model.CaseStatus.CLOSED &&
                        tl.nodes.any { it.state != NodeState.DONE }
                }
                .map { it.case.id }
                .toSet()
            var expandedIds by remember { mutableStateOf(defaultExpanded) }
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(uiState.timelines, key = { it.case.id }) { timeline ->
                    val expanded = timeline.case.id in expandedIds
                    // v1.64：展开/收起高度平滑过渡
                    SwipeActionsBox(
                        modifier = Modifier.animateContentSize(),
                        actions = listOf(
                            {
                                ActionBlockButton(
                                    label = "详情",
                                    background = EditBlue,
                                    onClick = { onOpenCase(timeline.case.id) }
                                ) {
                                    Icon(
                                        Icons.Default.Info,
                                        contentDescription = "详情",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            },
                            {
                                ActionBlockButton(
                                    label = "移除",
                                    background = DeleteRed,
                                    onClick = { viewModel.archiveFlow(timeline.case.id) }
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "移除流程",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        )
                    ) {
                        if (expanded) {
                            AccidentTimelineCard(
                                timeline = timeline,
                                onCollapse = { expandedIds = expandedIds - timeline.case.id },
                                onOpenCase = { onOpenCase(timeline.case.id) },
                                onCompleteNode = { node -> viewModel.completeNode(timeline.case.id, node) },
                                onRecordNodeDate = { node, dateText ->
                                    viewModel.recordNodeByDate(timeline.case.id, node, dateText)
                                },
                                onSkipInvestigation = { viewModel.skipInvestigation(timeline.case.id) },
                                onQuickProcess = { viewModel.submitQuickProcess(timeline.case.id) },
                                onUndoNode = { node -> viewModel.undoNode(timeline.case.id, node) }
                            )
                        } else {
                            CompletedTimelineCard(
                                timeline = timeline,
                                onExpand = { expandedIds = expandedIds + timeline.case.id },
                                onLongPress = { onOpenCase(timeline.case.id) }
                            )
                        }
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
    onCollapse: () -> Unit,
    onOpenCase: () -> Unit,
    onCompleteNode: (FlowNode) -> Unit,
    onRecordNodeDate: (FlowNode, String) -> Unit,
    onSkipInvestigation: () -> Unit,
    onQuickProcess: () -> Unit,
    onUndoNode: (FlowNode) -> Unit
) {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val case = timeline.case
    // v1.74：事故流程已去除「受案登记」节点（违法流程保留 VR 受案登记）
    val isViolation = case.caseType == CaseType.VIOLATION
    // 已认定终态：节点全部按完成渲染（未录日期的显示"已结束"）
    val displayNodes = if (case.status == com.casetracker.domain.model.CaseStatus.CLOSED) {
        timeline.nodes.map { it.copy(state = NodeState.DONE) }
    } else timeline.nodes
    val progress = AccidentFlowProgress(displayNodes)
    // 当前待操作的节点 = 第一个未完成节点
    val firstPending = displayNodes.firstOrNull { it.state != NodeState.DONE }

    // 日期修改对话框状态
    var editingNode by remember { mutableStateOf<FlowNode?>(null) }
    var dialogDateText by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }
    // 「鉴定委托」是否需要委托鉴定的提示（免鉴定/快速处理已选择后不再询问）
    var showBranchDialog by remember { mutableStateOf(false) }
    // v1.74 长按撤销确认
    var undoNode by remember { mutableStateOf<FlowNode?>(null) }
    // 每分钟刷新一次当前时间：到「鉴定报告生效日」自动放行事故调查报告
    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(60_000)
            nowMs = System.currentTimeMillis()
        }
    }

    MiuiCard(
        onClick = onCollapse,
        onLongClick = onOpenCase,
        modifier = Modifier.fillMaxWidth()
    ) {
        // 头部（v1.77：显示统一案件名称，不显示案件编号）
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = FormatUtils.unifiedTitle(case),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            // 状态跟随流程：事故 = 已认定/处理中（v1.74 去除受案登记后不再有待受案）；违法 = 已结案/处理中/待受案
            val registered = if (isViolation) {
                displayNodes.any {
                    it.code == ViolationFlowTemplate.REGISTER_CODE &&
                        it.state == NodeState.DONE
                }
            } else true
            val statusText = if (isViolation) {
                when {
                    case.status == com.casetracker.domain.model.CaseStatus.CLOSED -> "已结案"
                    registered -> "处理中"
                    else -> "待受案"
                }
            } else case.derivedStatusText(registered)
            val statusColor = when {
                case.status == com.casetracker.domain.model.CaseStatus.CLOSED -> SuccessColor
                registered -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            // 胶囊状态标签（澎湃/MIUI 风格）
            Text(
                text = statusText,
                style = MaterialTheme.typography.labelMedium,
                color = statusColor,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(statusColor.copy(alpha = 0.12f))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "${progress}%",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(8.dp))
            val doneCount = displayNodes.count { it.state == NodeState.DONE }
            Text(
                text = "$doneCount/${displayNodes.size} 环节",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(8.dp))
            androidx.compose.material3.TextButton(
                onClick = onCollapse,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                modifier = Modifier.height(28.dp)
            ) {
                Text("收纳", style = MaterialTheme.typography.labelMedium)
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = FormatUtils
                .accidentDateTime(case.accidentDate, case.accidentTime)
                .ifBlank { "事故发生时间未填写" },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (case.location.isNotBlank()) {
            Text(
                text = case.location,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // v1.77：鉴定报告生效日 = 送达（N3）后第 4 个工作日（送达当天不算，第 1~3 个工作日为异议期），生效前事故调查报告（N4）不可推进
        val n3Node = displayNodes.firstOrNull { it.code == "N3" }
        val n3Date = n3Node?.event?.occurredAt?.takeIf { it > 0 }
            ?: n3Node?.expectedDate?.takeIf { it > 0 }
            ?: 0L
        val n3EffectDate = if (n3Date > 0L) {
            com.casetracker.domain.model.CalendarUtil.toEpochMillis(
                com.casetracker.domain.model.CalendarUtil.addWorkDays(
                    com.casetracker.domain.model.CalendarUtil.toLocalDate(n3Date), 4
                )
            )
        } else 0L

        // 环节时间轴（含右侧操作按钮/修改日期；鉴定委托可判断是否需要委托，已完成节点长按撤销）
        displayNodes.forEachIndexed { idx, node ->
            MiniTimelineRow(
                node = node,
                dateFormat = dateFormat,
                isLast = idx == displayNodes.lastIndex,
                isCurrentPending = node.code == firstPending?.code,
                isViolation = isViolation,
                n3EffectDate = n3EffectDate,
                nowMs = nowMs,
                onComplete = {
                    // 生效门禁：事故调查报告（N4）须等鉴定报告送达后第 4 个工作日生效
                    if (!isViolation && node.code == "N4" && n3EffectDate > 0L && nowMs < n3EffectDate) {
                        // 未生效：按钮已禁用，此处兜底拦截
                        return@MiniTimelineRow
                    }
                    // 事故「鉴定委托」节点：先弹「是否需要委托鉴定」提示（免鉴定则跳过鉴定与事故调查报告流程）
                    if (!isViolation &&
                        node.code == com.casetracker.domain.model.AccidentFlowTemplate.INVESTIGATE_CODE &&
                        !case.flowSkipInvestigation && !case.flowQuickProcess
                    ) {
                        showBranchDialog = true
                    } else {
                        onCompleteNode(node)
                    }
                },
                onEditDate = {
                    editingNode = node
                    val ts = node.event?.occurredAt ?: 0L
                    dialogDateText = if (ts > 0) FormatUtils.shortDate(ts) else ""
                },
                onUndo = { undoNode = node }
            )
        }
    }

    // 日历选择器
    if (showDatePicker) {
        val initTs = editingNode?.event?.occurredAt?.takeIf { it > 0 }
        com.casetracker.ui.components.MiuiDatePickerDialog(
            initialMillis = initTs,
            onDateSelected = { _, text -> dialogDateText = text },
            onDismiss = { showDatePicker = false }
        )
    }

    // 鉴定委托：是否需要委托鉴定
    if (showBranchDialog) {
        AlertDialog(
            onDismissRequest = { showBranchDialog = false },
            title = { Text("是否需要委托鉴定") },
            text = {
                Column {
                    Text(
                        "「需要委托鉴定」：按主线流程办理（鉴定委托 → 鉴定报告出具 → 鉴定报告送达 → 事故调查报告）。",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "「不需要委托鉴定」：跳过鉴定与事故调查报告环节，自事故发生之日起 10 个工作日内出具事故认定书。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    // 需要委托鉴定 → 进入日期录入（选择具体委托鉴定日期，非直接录今天）
                    showBranchDialog = false
                    val n1 = firstPending
                    if (n1 != null) {
                        editingNode = n1
                        dialogDateText = ""
                    }
                }) { Text("需要委托鉴定") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showBranchDialog = false
                    onSkipInvestigation()
                }) { Text("不需要委托鉴定") }
            }
        )
    }

    // 日期修改对话框
    editingNode?.let { node ->
        // v1.74：受案登记仅违法流程保留（VR）；事故「事故发生」（N0）处提供一般程序快速处理申请入口
        val isRegister = isViolation && node.code == ViolationFlowTemplate.REGISTER_CODE
        val isAccidentStart = !isViolation && node.code == "N0"
        val canQuick = isAccidentStart && !case.flowSkipInvestigation && !case.flowQuickProcess
        AlertDialog(
            onDismissRequest = { editingNode = null },
            title = { Text(if (isRegister) "受案登记" else "修改「${node.name}」日期") },
            text = {
                Column {
                    if (isRegister) {
                        val registered = node.event?.occurredAt?.takeIf { it > 0 }
                        Text(
                            text = if (registered != null)
                                "当前状态：已受案登记（${dateFormat.format(Date(registered))}）"
                            else "当前状态：未受案登记",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (registered != null) SuccessColor else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (registered != null) FontWeight.Medium else null
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "依据《道路交通安全违法行为处理程序规定》，对查获的违法行为应当及时受理并制作受案登记。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    } else {
                        Text(
                            "修改后，后续环节的预计日期将自动重新计算。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        if (canQuick) {
                            Text(
                                "一般程序快速处理：当事人提交《一般程序快速处理申请书》后，自事故发生之日起 10 个工作日内出具事故认定书（无需鉴定与事故调查报告）。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedButton(
                                onClick = {
                                    editingNode = null
                                    onQuickProcess()
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("提交《一般程序快速处理申请书》")
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                    MiuiTextField(
                        value = dialogDateText,
                        onValueChange = { dialogDateText = it },
                        label = if (isRegister) "受案登记日期" else "实际日期",
                        placeholder = "例如：2026-09-10",
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { showDatePicker = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Default.DateRange,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("打开日历选择日期")
                    }
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

    // v1.74 撤销误点完成：长按已完成节点 → 确认撤销
    undoNode?.let { node ->
        AlertDialog(
            onDismissRequest = { undoNode = null },
            title = { Text("撤销「${node.name}」完成？") },
            text = { Text("撤销后该节点将恢复为进行中，后续环节预计日期自动重新计算。") },
            confirmButton = {
                TextButton(onClick = {
                    onUndoNode(node)
                    undoNode = null
                }) { Text("撤销") }
            },
            dismissButton = {
                TextButton(onClick = { undoNode = null }) { Text("取消") }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MiniTimelineRow(
    node: FlowNode,
    dateFormat: SimpleDateFormat,
    isLast: Boolean,
    isCurrentPending: Boolean,
    isViolation: Boolean,
    n3EffectDate: Long,
    nowMs: Long,
    onComplete: () -> Unit,
    onEditDate: () -> Unit,
    onUndo: () -> Unit
) {
    // v1.76：事故调查报告（N4）出具时间超过——不做「已超期」文字提示，仅节点颜色变警示橙提醒
    val n4Overdue = !isViolation && node.code == AccidentFlowTemplate.REPORT_CODE &&
        node.state == NodeState.OVERDUE

    val dotColor = when {
        n4Overdue -> WarningColor
        node.state == NodeState.DONE -> SuccessColor
        node.state == NodeState.OVERDUE -> DangerColor
        node.state == NodeState.PENDING -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    }
    val lineColor = when {
        n4Overdue -> WarningColor.copy(alpha = 0.7f)
        node.state == NodeState.DONE -> SuccessColor
        node.state == NodeState.OVERDUE -> DangerColor
        node.state == NodeState.PENDING -> MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
    }
    val done = node.state == NodeState.DONE
    // v1.74：受案登记仅违法流程保留（VR）；事故流程已去除受案登记节点
    val isRegister = isViolation && node.code == ViolationFlowTemplate.REGISTER_CODE
    // v1.74 生效门禁：事故调查报告（N4）在鉴定报告生效日之前不可完成
    val n4WaitingEffect = !isViolation && node.code == "N4" &&
        !done && n3EffectDate > 0L && nowMs < n3EffectDate

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

        // 左侧：节点名 + 日期信息（点击修改日期；已完成节点长按撤销 v1.74）
        Column(
            modifier = Modifier
                .weight(1f)
                .combinedClickable(
                    onClick = { onEditDate() },
                    onLongClick = { if (done) onUndo() }
                )
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
                // v1.74：鉴定报告生效中标识
                if (n4WaitingEffect) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "生效中",
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
                node.state == NodeState.DONE -> Text(
                    text = "已结束",
                    style = MaterialTheme.typography.labelSmall,
                    color = SuccessColor
                )
                node.state == NodeState.OVERDUE -> if (n4Overdue) Text(
                    text = "待办理",
                    style = MaterialTheme.typography.labelSmall,
                    color = WarningColor,
                    fontWeight = FontWeight.Medium
                ) else Text(
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
                    text = if (isRegister) "未受案登记" else "待前置环节",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isRegister) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // v1.74：鉴定报告送达（N3）完成后显示生效日
            if (!isViolation && node.code == "N3" && n3EffectDate > 0L) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "生效：${dateFormat.format(Date(n3EffectDate))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (nowMs < n3EffectDate) MaterialTheme.colorScheme.primary
                            else SuccessColor,
                    fontWeight = FontWeight.Medium
                )
            }
            // v1.74：事故调查报告（N4）等待生效提示
            if (n4WaitingEffect) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "生效后方可进入",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 右侧：完成按钮 / 已完成标签（统一靠右）
        Spacer(modifier = Modifier.width(8.dp))
        if (done) {
            // v1.74：点击修改日期，长按撤销完成
            Box(
                modifier = Modifier
                    .combinedClickable(
                        onClick = { onEditDate() },
                        onLongClick = { onUndo() }
                    )
            ) {
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
            }
        } else if (isCurrentPending) {
            FilledTonalButton(
                onClick = onComplete,
                enabled = !n4WaitingEffect,
                shape = CircleShape,
                modifier = Modifier.height(32.dp)
            ) {
                Text(
                    // 鉴定委托待办：提示进入"是否需要委托鉴定"判断；生效中节点提示等待生效
                    when {
                        n4WaitingEffect -> "生效中"
                        isRegister -> "受案"
                        node.code == com.casetracker.domain.model.AccidentFlowTemplate.INVESTIGATE_CODE -> "选择›"
                        else -> "完成"
                    },
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

/** 已收纳流程的紧凑信息卡（v1.64 优化）：上行 圆点+编号+进度+状态胶囊+箭头；下行 事故时间·地点 摘要；底部细进度条；单击展开，长按进案件详情 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CompletedTimelineCard(
    timeline: AccidentTimeline,
    onExpand: () -> Unit,
    onLongPress: () -> Unit
) {
    val case = timeline.case
    val isClosed = case.status == com.casetracker.domain.model.CaseStatus.CLOSED
    val isViolation = case.caseType == CaseType.VIOLATION
    // 收纳卡状态同样跟随流程（事故：已认定/处理中；违法：已结案/处理中/待受案——v1.74 事故已去除受案登记）
    val displayNodes = if (isClosed) {
        timeline.nodes.map { it.copy(state = NodeState.DONE) }
    } else timeline.nodes
    val registered = if (isViolation) {
        displayNodes.any {
            it.code == ViolationFlowTemplate.REGISTER_CODE &&
                it.state == NodeState.DONE
        }
    } else true
    val statusText = if (isViolation) {
        when {
            isClosed -> "已结案"
            registered -> "处理中"
            else -> "待受案"
        }
    } else case.derivedStatusText(registered)
    val statusColor = when {
        isClosed -> SuccessColor
        registered -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val progress = AccidentFlowProgress(displayNodes)
    // 事故时间 · 地点 摘要（优雅信息层）
    val dateText = if (case.accidentDate > 0) {
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(case.accidentDate))
    } else ""
    val timeLoc = listOfNotNull(
        dateText.takeIf { it.isNotBlank() },
        case.accidentTime.takeIf { it.isNotBlank() },
        case.location.takeIf { it.isNotBlank() }
    ).joinToString(" · ")
    MiuiCard(
        onClick = onExpand,
        onLongClick = onLongPress,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (isClosed) SuccessColor else MaterialTheme.colorScheme.primary)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = FormatUtils.unifiedTitle(case),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.width(8.dp))
                // 进度百分比（与展开态一致）
                Text(
                    text = "$progress%",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                // 胶囊状态标签（澎湃/MIUI 风格）
                Text(
                    statusText,
                    style = MaterialTheme.typography.labelMedium,
                    color = statusColor,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(statusColor.copy(alpha = 0.12f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                // 简洁展开箭头（点击整卡展开）
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    contentDescription = "展开流程",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            // 事故时间 · 地点 摘要
            if (timeLoc.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = timeLoc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            // 细进度条（4dp，按进度填充）
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress / 100f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(50))
                        .background(if (isClosed) SuccessColor else MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
}

/** 简单进度计算 */
private fun AccidentFlowProgress(nodes: List<FlowNode>): Int {
    if (nodes.isEmpty()) return 0
    val done = nodes.count { it.state == NodeState.DONE }
    return (done.toFloat() / nodes.size * 100).toInt().coerceIn(0, 100)
}
