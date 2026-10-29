@file:OptIn(ExperimentalLayoutApi::class)

package com.casetracker.ui.detail

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.casetracker.domain.model.CalendarUtil
import com.casetracker.domain.model.CaseStatus
import com.casetracker.domain.model.CaseType
import com.casetracker.domain.model.CheckListTemplates
import com.casetracker.domain.model.FlowNode
import com.casetracker.domain.model.NodeState
import com.casetracker.domain.model.derivedStatusText
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
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class, ExperimentalLayoutApi::class)
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

    // v1.82 办案清单：右滑抽屉状态（左上角清单按钮 + 屏幕左边缘右滑）
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    LaunchedEffect(caseId) { viewModel.loadCase(caseId) }
    LaunchedEffect(shouldGoBack) { if (shouldGoBack) { onBack(); viewModel.onNavigationConsumed() } }
    LaunchedEffect(shouldGoEdit) { if (shouldGoEdit) { onEdit(); viewModel.onNavigationConsumed() } }
    LaunchedEffect(uiState.toastMessage) {
        uiState.toastMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onEvent(DetailUiEvent.ConsumeToast)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = MaterialTheme.colorScheme.background) {
                CaseChecklistDrawer(
                    uiState = uiState,
                    onToggle = { nodeCode, index, text, done ->
                        viewModel.onEvent(
                            DetailUiEvent.ToggleChecklistItem(nodeCode, index, text, done)
                        )
                    },
                    onClose = { scope.launch { drawerState.close() } }
                )
            }
        }
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                MiuiTopAppBar(
                    title = { Text("案件详情", style = MaterialTheme.typography.titleLarge) },
                    navigationIcon = {
                        Row {
                            IconButton(onClick = { viewModel.onEvent(DetailUiEvent.NavigateBack) }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                            }
                            // v1.82 办案清单：左上角清单按钮唤出右滑抽屉
                            IconButton(
                                onClick = { scope.launch { drawerState.open() } }
                            ) {
                                Icon(Icons.Default.List, contentDescription = "办案清单")
                            }
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
                    // v1.67 下拉刷新：中间卡片下拉重新拉取并重算进程状态与剩余天数
                    val pullRefreshState = rememberPullRefreshState(
                        refreshing = uiState.isRefreshing,
                        onRefresh = { viewModel.onEvent(DetailUiEvent.Refresh) }
                    )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pullRefresh(pullRefreshState)
                ) {
                    CaseDetailContent(
                        uiState = uiState,
                        case = case,
                        viewModel = viewModel,
                        padding = padding
                    )
                    // 下拉刷新指示器（自绘：下拉进度 >0 或刷新中即显示）
                    if (pullRefreshState.progress > 0f || uiState.isRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 10.dp)
                                .size(30.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CaseDetailContent(
    uiState: DetailUiState,
    case: com.casetracker.domain.model.Case,
    viewModel: CaseDetailViewModel,
    padding: PaddingValues
) {
    val dateFormat = SimpleDateFormat("yyyy年MM月dd日", Locale.getDefault())
    val now = System.currentTimeMillis()
    // v1.81：案件名称长按修改对话框状态
    var showTitleEdit by remember { mutableStateOf(false) }
    var titleEditText by remember { mutableStateOf("") }
    val isClosed = case.status == CaseStatus.CLOSED
    // 已认定终态：全部节点按完成渲染
    val displayNodes = if (isClosed) {
        uiState.nodes.map { it.copy(state = NodeState.DONE) }
    } else uiState.nodes
    val nextNode = displayNodes.firstOrNull { it.state != NodeState.DONE }
    // 受案登记节点码（事故 NR / 违法 VR）
    val registerCode = if (case.caseType == CaseType.ACCIDENT)
        com.casetracker.domain.model.AccidentFlowTemplate.REGISTER_CODE
    else com.casetracker.domain.model.ViolationFlowTemplate.REGISTER_CODE
    // v1.66/v1.67 超期口径：事故案件按「事故认定书出具」节点（N5）判定，不再按前期流程节点；
    // 认定书已出具（N5 完成）不再显示超期；违法行为案件按法定截止口径
    val overdueBase = when {
        isClosed -> 0L
        case.caseType == CaseType.ACCIDENT -> {
            val n5 = displayNodes.firstOrNull {
                it.code == com.casetracker.domain.model.AccidentFlowTemplate.CERTIFICATE_CODE
            }
            if (n5 != null && n5.state != NodeState.DONE) {
                n5.expectedDate.takeIf { it > 0 } ?: 0L
            } else 0L
        }
        else -> case.deadline.takeIf { it > 0 } ?: 0L
    }
    val daysLeft = overdueBase.takeIf { it > 0 }?.let { CalendarUtil.daysBetween(now, it) }
    val isOverdue = daysLeft != null && daysLeft < 0

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
                                // v1.80 事故已去除受案登记，顶部状态恒「处理中」；违法案件按受案登记（VR）判断
                                val registered = if (case.caseType == CaseType.ACCIDENT) true
                                                 else displayNodes.any {
                                                     it.code == registerCode && it.state == NodeState.DONE
                                                 }
                                val statusColor = if (isClosed) SuccessColor
                                                  else if (isOverdue) DangerColor
                                                  else if (registered) MaterialTheme.colorScheme.primary
                                                  else MaterialTheme.colorScheme.onSurfaceVariant
                                Text(
                                    text = case.derivedStatusText(registered),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = statusColor,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                                    modifier = Modifier
                                        .background(statusColor.copy(alpha = 0.12f), RoundedCornerShape(50))
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = com.casetracker.domain.model.FormatUtils.unifiedTitle(case),
                                style = MaterialTheme.typography.headlineSmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                // v1.81：长按案件名称可修改（事故/违法均可）
                                modifier = Modifier.combinedClickable(
                                    onClick = {},
                                    onLongClick = {
                                        titleEditText = com.casetracker.domain.model.FormatUtils.unifiedTitle(case)
                                        showTitleEdit = true
                                    }
                                )
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
                            // 当事人列表：有几个显示几个（兼容旧字段 selfParty/otherParty）
                            val parties = case.parties
                                .filter { it.isNotBlank() }
                                .ifEmpty {
                                    listOfNotNull(
                                        case.selfParty.takeIf { it.isNotBlank() },
                                        case.otherParty.takeIf { it.isNotBlank() }
                                    )
                                }
                            if (parties.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "当事人",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                parties.forEach { party ->
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "· $party",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (case.injuryDetail.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "伤情：${case.injuryDetail}",
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
                                // 案情情况：点击直接进入编辑界面（无需再点右上角编辑）
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { viewModel.onEvent(DetailUiEvent.NavigateToEdit) }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "案情情况",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = case.description,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "编辑 ›",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }

                    // 进度卡片
                    item {
                        val nodeProgress = if (isClosed) 100 else if (uiState.nodes.isNotEmpty()) {
                            com.casetracker.domain.model.AccidentFlowTemplate.calculateProgress(uiState.nodes)
                        } else case.progress
                        MiuiCard {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("处理进度", style = MaterialTheme.typography.titleMedium)
                                Spacer(modifier = Modifier.weight(1f))
                                Text(
                                    text = "$nodeProgress%",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = if (isClosed) SuccessColor else MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            MiuiProgressBar(progress = nodeProgress / 100f, isOverdue = isOverdue)
                            Spacer(modifier = Modifier.height(12.dp))
                            Row {
                                // v1.79：事故已去除受案登记流程，状态恒为「处理中」；违法案件按受案登记（VR）判断
                                val registered = if (case.caseType == CaseType.ACCIDENT) true
                                                 else displayNodes.any {
                                                     it.code == registerCode && it.state == NodeState.DONE
                                                 }
                                Text(
                                    text = "状态：${case.derivedStatusText(registered)}",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                if (isClosed) {
                                    Text(
                                        text = "已结束",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = SuccessColor
                                    )
                                } else if (isOverdue) {
                                    Text(
                                        text = "已超期 ${-daysLeft!!} 天",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = DangerColor
                                    )
                                } else if (daysLeft != null) {
                                    Text(
                                        text = if (daysLeft == 0) "今天到期" else "剩余 $daysLeft 天",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (daysLeft <= 3) WarningColor
                                                else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    // 下一节点无预计日期（如等待前置环节）：退回法定截止口径
                                    Text(
                                        text = "截止：${dateFormat.format(Date(case.deadline))}",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (!isClosed) {
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
                                        // v1.67：预计完成时间右侧增加「到下一节点剩余天数」
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = "预计完成：${dateFormat.format(Date(nextNode.expectedDate))}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Spacer(modifier = Modifier.weight(1f))
                                            val nodeDaysLeft = CalendarUtil.daysBetween(now, nextNode.expectedDate)
                                            // v1.76：下一节点为事故调查报告（N4）且超期时，不显示「已超期」文字，仅颜色提醒
                                            val nextN4Overdue =
                                                nextNode.code == com.casetracker.domain.model.AccidentFlowTemplate.REPORT_CODE &&
                                                nodeDaysLeft < 0
                                            Text(
                                                text = when {
                                                    nextN4Overdue -> "待办理"
                                                    nodeDaysLeft < 0 -> "已超期 ${-nodeDaysLeft} 天"
                                                    else -> "剩余 $nodeDaysLeft 天"
                                                },
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                                                color = when {
                                                    nextN4Overdue -> WarningColor
                                                    nodeDaysLeft < 0 -> DangerColor
                                                    else -> MaterialTheme.colorScheme.primary
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                            if (!isClosed) {
                                Spacer(modifier = Modifier.height(12.dp))
                                val allDone = uiState.nodes.isNotEmpty() &&
                                    uiState.nodes.all { it.state == NodeState.DONE }
                                if (allDone) {
                                    MiuiButton(
                                        text = "标记已认定",
                                        onClick = { viewModel.onEvent(DetailUiEvent.CloseCase) },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                } else {
                                    Text(
                                        text = "完成全部流程节点后可标记已认定（录入节点日期会自动推进）",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // v1.70 责任划分与扣车：事故认定书（N5）出具后自动出现，认定书出具前不显示
                    if (case.caseType == CaseType.ACCIDENT &&
                        (isClosed || displayNodes.any { it.code == com.casetracker.domain.model.AccidentFlowTemplate.CERTIFICATE_CODE && it.state == NodeState.DONE })
                    ) {
                        item {
                            val parties = case.parties
                                .filter { it.isNotBlank() }
                                .ifEmpty {
                                    listOfNotNull(
                                        case.selfParty.takeIf { it.isNotBlank() },
                                        case.otherParty.takeIf { it.isNotBlank() }
                                    )
                                }
                            MiuiCard(
                                modifier = if (uiState.showLiabilityHint)
                                    Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(18.dp))
                                else Modifier
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("责任划分与扣车", style = MaterialTheme.typography.titleMedium)
                                    Spacer(modifier = Modifier.weight(1f))
                                    Text(
                                        "认定书已出具",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                if (uiState.showLiabilityHint) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        "事故认定书已出具，请为每位当事人选择责任情况",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                if (parties.isEmpty()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        "暂无当事人，可在编辑案件中添加后再填写责任",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    parties.forEach { party ->
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Text(
                                            party,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            LIABILITY_OPTIONS.forEach { option ->
                                                val selected = uiState.partyLiabilities[party] == option
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(50))
                                                        .background(
                                                            if (selected) MaterialTheme.colorScheme.primary
                                                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                                        )
                                                        .clickable {
                                                            viewModel.onEvent(
                                                                DetailUiEvent.SetPartyLiability(party, option)
                                                            )
                                                        }
                                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                                ) {
                                                    Text(
                                                        option,
                                                        style = MaterialTheme.typography.labelMedium,
                                                        color = if (selected) MaterialTheme.colorScheme.onPrimary
                                                                else MaterialTheme.colorScheme.onSurface
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                androidx.compose.material3.HorizontalDivider(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                // 扣留车辆 / 车辆已返还 / 违法行为处理（同一卡片内）
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("扣留车辆", style = MaterialTheme.typography.bodyLarge)
                                        Text(
                                            "事故车辆被扣留",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Switch(
                                        checked = case.vehicleSeized,
                                        onCheckedChange = { viewModel.onEvent(DetailUiEvent.SetVehicleSeized(it)) },
                                        colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary)
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("车辆已返还", style = MaterialTheme.typography.bodyLarge)
                                        Text(
                                            if (case.vehicleSeized) "扣留车辆已返还当事人"
                                            else "未扣留车辆，无需返还",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Switch(
                                        checked = case.vehicleReturned,
                                        onCheckedChange = { viewModel.onEvent(DetailUiEvent.SetVehicleReturned(it)) },
                                        enabled = case.vehicleSeized,
                                        colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary)
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("违法行为处理", style = MaterialTheme.typography.bodyLarge)
                                        Text(
                                            "事故中当事人违法行为是否另行处理完毕",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Switch(
                                        checked = case.violationHandled,
                                        onCheckedChange = { viewModel.onEvent(DetailUiEvent.SetViolationHandled(it)) },
                                        colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary)
                                    )
                                }
                            }
                        }
                    }

                    // 8 环节流程时间线
                    if (displayNodes.isNotEmpty()) {
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("法定流程节点", style = MaterialTheme.typography.titleMedium)
                                Spacer(modifier = Modifier.weight(1f))
                                Text(
                                    "共 ${displayNodes.count { it.state == NodeState.DONE }}/${displayNodes.size} 完成",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        items(displayNodes, key = { it.code }) { node ->
                            FlowNodeItem(
                                node = node,
                                isLast = node.code == displayNodes.last().code,
                                onClick = {
                                    // 鉴定委托：先判断是否需要委托鉴定（免鉴定/快速处理已选择后直接录入）
                                    if (node.code == com.casetracker.domain.model.AccidentFlowTemplate.INVESTIGATE_CODE &&
                                        !case.flowSkipInvestigation && !case.flowQuickProcess
                                    ) {
                                        viewModel.onEvent(DetailUiEvent.ShowBranchDialog)
                                    } else {
                                        viewModel.onEvent(DetailUiEvent.ShowRecordDialog(node.code))
                                    }
                                }
                            )
                        }
                    }

                    // v1.67 委托鉴定选择修改入口：已选 免鉴定/快速处理 后可点击改回主线或切换
                    if (case.caseType == CaseType.ACCIDENT &&
                        (case.flowSkipInvestigation || case.flowQuickProcess)
                    ) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f))
                                    .clickable { viewModel.onEvent(DetailUiEvent.ShowBranchDialog) }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "鉴定选择：${if (case.flowQuickProcess) "一般程序快速处理" else "不需要委托鉴定"}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                Text(
                                    text = "点击修改",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

        // 录入/修改节点日期对话框
        uiState.recordNodeCode?.let { nodeCode ->
            val node = uiState.nodes.firstOrNull { it.code == nodeCode }
            val isEditing = node?.state == NodeState.DONE
            val inputFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
            val displayFormat = remember { SimpleDateFormat("yyyy年MM月dd日", Locale.getDefault()) }
            var dateText by remember(nodeCode) {
                val existed = node?.event?.occurredAt?.takeIf { it > 0 }
                mutableStateOf(if (existed != null) inputFormat.format(Date(existed)) else "")
            }
            var showDatePicker by remember(nodeCode) { mutableStateOf(false) }
            if (showDatePicker) {
                com.casetracker.ui.components.MiuiDatePickerDialog(
                    initialMillis = node?.event?.occurredAt?.takeIf { it > 0 },
                    onDateSelected = { _, text -> dateText = text },
                    onDismiss = { showDatePicker = false }
                )
            }
            AlertDialog(
                onDismissRequest = { viewModel.onEvent(DetailUiEvent.DismissRecordDialog) },
                title = { Text(if (isEditing) "修改：${node?.name ?: ""}" else "录入：${node?.name ?: ""}") },
                text = {
                    Column {
                        val detailCase = uiState.case
                        val isViolation = detailCase?.caseType == CaseType.VIOLATION
                        val canQuick = detailCase != null && !isViolation &&
                            !detailCase.flowSkipInvestigation && !detailCase.flowQuickProcess
                        // v1.74：受案登记仅违法流程保留；事故「事故发生」（N0）节点提供快速处理申请入口
                        if (node?.code == com.casetracker.domain.model.AccidentFlowTemplate.REGISTER_CODE) {
                            Text(
                                text = if (isEditing) "已受案登记：${displayFormat.format(Date(node.event!!.occurredAt))}"
                                       else "当前状态：未受案登记。可在此录入受案登记日期（依据《道路交通安全违法行为处理程序规定》，对查获的违法行为应当及时受理并制作受案登记）。",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isEditing) SuccessColor else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                        } else {
                            Text(
                                text = "修改后，后续环节的预计日期将自动重新计算。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            // 一般程序快速处理入口：事故流程在「事故发生」节点提交申请书后 10 个工作日出认定书
                            if (canQuick && node?.code == "N0") {
                                Text(
                                    text = "一般程序快速处理：当事人提交《一般程序快速处理申请书》后，自事故发生之日起 10 个工作日内出具事故认定书（无需鉴定与事故调查报告）。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedButton(
                                    onClick = { viewModel.onEvent(DetailUiEvent.ChooseQuickProcess) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("提交《一般程序快速处理申请书》（申请日 = 今天）")
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                            }
                        }
                        if ((node?.expectedDate ?: 0L) > 0L) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "预计日期：${displayFormat.format(Date(node!!.expectedDate))}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
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
                        viewModel.onEvent(DetailUiEvent.RecordNodeDate(nodeCode, dateText))
                    }) { Text("保存") }
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

        // 鉴定委托：是否需要委托鉴定（v1.67：三选一，已选后可修改）
        if (uiState.showBranchDialog) {
            val quickChosen = case.flowQuickProcess
            val skipChosen = case.flowSkipInvestigation && !case.flowQuickProcess
            AlertDialog(
                onDismissRequest = { viewModel.onEvent(DetailUiEvent.DismissBranchDialog) },
                title = { Text("鉴定方式") },
                text = {
                    Column {
                        Text(
                            "选择鉴定方式后，后续流程节点预计日期将同步重算：",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        // 需要委托鉴定（主线）
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (!quickChosen && !skipChosen) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                )
                                .clickable {
                                    if (quickChosen || skipChosen) {
                                        viewModel.onEvent(DetailUiEvent.RestoreMainline)
                                    } else {
                                        viewModel.onEvent(DetailUiEvent.DismissBranchDialog)
                                        viewModel.onEvent(DetailUiEvent.ShowRecordDialog(
                                            com.casetracker.domain.model.AccidentFlowTemplate.INVESTIGATE_CODE
                                        ))
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "需要委托鉴定（主线）",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (!quickChosen && !skipChosen) MaterialTheme.colorScheme.primary
                                       else MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            if (!quickChosen && !skipChosen) {
                                Text("当前", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        // 不需要委托鉴定（免鉴定）
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (skipChosen) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                )
                                .clickable { viewModel.onEvent(DetailUiEvent.ChooseSkipInvestigation) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "不需要委托鉴定",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (skipChosen) MaterialTheme.colorScheme.primary
                                       else MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            if (skipChosen) {
                                Text("当前", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        // 一般程序快速处理
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (quickChosen) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                )
                                .clickable { viewModel.onEvent(DetailUiEvent.ChooseQuickProcess) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "一般程序快速处理",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (quickChosen) MaterialTheme.colorScheme.primary
                                       else MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            if (quickChosen) {
                                Text("当前", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "免鉴定 / 快速处理：事故发生之日起 10 个工作日内出具事故认定书；主线：鉴定报告送达后第 8 个工作日出具。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.onEvent(DetailUiEvent.DismissBranchDialog) }) {
                        Text("取消")
                    }
                },
                dismissButton = {}
            )
        }

        // v1.81：案件名称长按修改对话框
        if (showTitleEdit) {
            CaseTitleEditDialog(
                initialText = titleEditText,
                onConfirm = { value ->
                    if (value.isNotBlank()) viewModel.onEvent(DetailUiEvent.UpdateTitle(value))
                    showTitleEdit = false
                },
                onDismiss = { showTitleEdit = false }
            )
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
    // v1.76：事故调查报告（N4）出具时间超过——不做「已超期」文字提示，仅节点颜色变警示橙提醒
    val n4Overdue = node.code == com.casetracker.domain.model.AccidentFlowTemplate.REPORT_CODE &&
        node.state == NodeState.OVERDUE
    val stateColor = when {
        n4Overdue -> WarningColor
        node.state == NodeState.DONE -> SuccessColor
        node.state == NodeState.PENDING -> MaterialTheme.colorScheme.primary
        node.state == NodeState.OVERDUE -> DangerColor
        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    }
    // 所有节点均可点击：待办/超期可录入，已完成可修改日期
    val canClick = true

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
                } else if (node.state == NodeState.DONE) {
                    // 已认定终态中未录日期的节点显示"已结束"
                    Text(
                        "已结束",
                        style = MaterialTheme.typography.bodySmall,
                        color = SuccessColor
                    )
                } else {
                    Text(
                        when (node.state) {
                            NodeState.PENDING ->
                                if (node.code == com.casetracker.domain.model.AccidentFlowTemplate.REGISTER_CODE)
                                    "未受案登记"
                                else "待录入"
                            NodeState.OVERDUE -> if (n4Overdue) "待办理" else "已超期"
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
                        // 鉴定委托待办：提示进入"是否需要委托鉴定"判断
                        if (actual > 0) "修改 ›"
                        else if (node.code == com.casetracker.domain.model.AccidentFlowTemplate.INVESTIGATE_CODE) "选择 ›"
                        else "录入 ›",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

// v1.81：案件名称长按修改对话框（事故/违法均可）
@Composable
private fun CaseTitleEditDialog(
    initialText: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initialText) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("修改案件名称") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = { Text("案件名称") },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text.trim()) }) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
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
