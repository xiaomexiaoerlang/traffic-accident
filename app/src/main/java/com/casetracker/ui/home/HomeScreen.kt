package com.casetracker.ui.home

import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.casetracker.data.SettingsManager
import com.casetracker.domain.model.CalendarUtil
import com.casetracker.domain.model.Case
import com.casetracker.domain.model.CaseStatus
import com.casetracker.domain.model.CaseType
import com.casetracker.domain.model.FlowNode
import com.casetracker.domain.model.FormatUtils
import com.casetracker.domain.model.ViolationType
import com.casetracker.theme.DeleteRed
import com.casetracker.theme.DangerColor
import com.casetracker.theme.EditBlue
import com.casetracker.theme.SuccessColor
import com.casetracker.theme.WarningColor
import com.casetracker.ui.components.ActionBlockButton
import com.casetracker.ui.components.MiuiCard
import com.casetracker.ui.components.MiuiProgressBar
import com.casetracker.ui.components.MiuiTopAppBar
import com.casetracker.ui.components.SwipeActionsBox
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * v1.79 进程级弹窗门控：仅本次软件启动（进程存活期间）弹出一次，
 * 日常切换其他软件再回来（进程未销毁）不再弹出。
 */
private object ReminderGate {
    @Volatile
    var shown: Boolean = false
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    category: CaseType = CaseType.ACCIDENT,
    onCategoryChange: (CaseType) -> Unit = {},
    onOpenCase: (Long) -> Unit,
    onCreateCase: () -> Unit,
    onEditCase: (Long) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val navigation by viewModel.navigation.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState()
    var deleteTarget by remember { mutableStateOf<Case?>(null) }

    // v1.79 进入软件提醒：仅本次软件启动（进程存活期间）弹一次；日常切换其他软件再回来不弹
    var showReminder by remember { mutableStateOf(false) }
    LaunchedEffect(uiState) {
        if (!ReminderGate.shown && !uiState.isLoading && uiState.reminderItems.isNotEmpty()) {
            ReminderGate.shown = true
            showReminder = true
        }
    }

    // v1.68 联动：案件页类型分栏切换时同步到「进程」页（共享分类状态），并驱动本页统计/筛选/列表
    LaunchedEffect(category) {
        viewModel.onEvent(HomeUiEvent.CategoryChange(category))
    }

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

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                MiuiTopAppBar(
                    title = { Text("案件", style = MaterialTheme.typography.titleLarge) }
                )
            }
        ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // v1.68 类型分栏：交通事故 / 违法行为（联动「进程」页，切换后统计卡/筛选/列表对应切换）
            Row(
                modifier = Modifier.fillMaxWidth()
            ) {
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

            if (category == CaseType.ACCIDENT) {
                // 事故统计卡片（2 列网格：待处理事故 / 将超期事故 / 已认定事故 / 本年度事故）
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatMiniCard(
                        "待处理事故", uiState.pendingCount, MaterialTheme.colorScheme.primary, Icons.Default.Schedule, Modifier.weight(1f),
                        onClick = { viewModel.onEvent(HomeUiEvent.FilterBy(HomeFilter.ACC_PENDING)) }
                    )
                    StatMiniCard(
                        "将超期事故", uiState.dueSoonCount, WarningColor, Icons.Default.Warning, Modifier.weight(1f),
                        onClick = { viewModel.onEvent(HomeUiEvent.FilterBy(HomeFilter.ACC_PENDING)) }
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatMiniCard(
                        "已认定事故", uiState.closedCount, SuccessColor, Icons.Default.CheckCircle, Modifier.weight(1f),
                        onClick = { viewModel.onEvent(HomeUiEvent.FilterBy(HomeFilter.ACC_CLOSED)) }
                    )
                    StatMiniCard(
                        "本年度事故", uiState.thisYearCount, DangerColor, Icons.Default.DateRange, Modifier.weight(1f),
                        onClick = { viewModel.onEvent(HomeUiEvent.FilterBy(HomeFilter.ACC_THIS_YEAR)) }
                    )
                }
            } else {
                // v1.68 违法统计卡片：已受案 / 待立案 / 待处罚 / 已结案 / 本年度违法（v1.72 删除列表上方筛选按钮，统计卡保留）
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatMiniCard(
                        "已受案", uiState.vioRegisteredCount, MaterialTheme.colorScheme.primary, Icons.Default.CheckCircle, Modifier.weight(1f),
                        onClick = { viewModel.onEvent(HomeUiEvent.FilterBy(HomeFilter.VIO_REGISTERED)) }
                    )
                    StatMiniCard(
                        "待立案", uiState.vioPendingCount, MaterialTheme.colorScheme.primary, Icons.Default.Schedule, Modifier.weight(1f),
                        onClick = { viewModel.onEvent(HomeUiEvent.FilterBy(HomeFilter.VIO_PENDING)) }
                    )
                    StatMiniCard(
                        "待处罚", uiState.vioPenaltyCount, WarningColor, Icons.Default.Warning, Modifier.weight(1f),
                        onClick = { viewModel.onEvent(HomeUiEvent.FilterBy(HomeFilter.VIO_PENALTY)) }
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatMiniCard(
                        "已结案", uiState.vioClosedCount, SuccessColor, Icons.Default.CheckCircle, Modifier.weight(1f),
                        onClick = { viewModel.onEvent(HomeUiEvent.FilterBy(HomeFilter.VIO_CLOSED)) }
                    )
                    StatMiniCard(
                        "本年度违法", uiState.vioThisYearCount, DangerColor, Icons.Default.DateRange, Modifier.weight(1f),
                        onClick = { viewModel.onEvent(HomeUiEvent.FilterBy(HomeFilter.VIO_THIS_YEAR)) }
                    )
                }
            }

            // 筛选（事故：待处理事故 / 已认定事故 / 本年度事故；违法：已受案 / 待立案 / 待处罚 / 已结案）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HomeFilter.forCategory(category).forEach { mode ->
                    FilterChip(
                        selected = uiState.filterMode == mode,
                        onClick = { viewModel.onEvent(HomeUiEvent.FilterBy(mode)) },
                        label = { Text(mode.displayName, style = MaterialTheme.typography.labelMedium) }
                    )
                }
            }

            if (uiState.isLoading) {
                // v1.71 加载骨架屏：脉动占位卡片，替代单一转圈
                LoadingSkeleton()
            } else if (uiState.cases.isEmpty()) {
                EmptyState(category = category)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.cases, key = { it.id }) { case ->
                        // 每起案件可左滑，露出等高色块「编辑 / 删除」按钮（蓝/红，参考视频样式）
                        SwipeActionsBox(
                            actions = listOf(
                                {
                                    ActionBlockButton(
                                        label = "编辑",
                                        background = EditBlue,
                                        onClick = { viewModel.onEvent(HomeUiEvent.EditCase(case.id)) }
                                    ) {
                                        Icon(
                                            Icons.Default.Edit,
                                            contentDescription = "编辑",
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                },
                                {
                                    ActionBlockButton(
                                        label = "删除",
                                        background = DeleteRed,
                                        onClick = { deleteTarget = case }
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "删除",
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            )
                        ) {
                            CaseCardItem(
                                case = case,
                                nextNode = uiState.nextNodeByCase[case.id],
                                overdueBase = uiState.overdueBaseByCase[case.id] ?: 0L,
                                isRegistered = if (case.caseType == CaseType.ACCIDENT) true
                                               else case.id in uiState.violationRegisteredCaseIds,
                                onClick = { viewModel.onEvent(HomeUiEvent.OpenCaseDetail(case.id)) },
                                onLongPress = { viewModel.onEvent(HomeUiEvent.LongPressCase(case)) }
                            )
                        }
                    }
                }
            }
        }
    }

    // v1.76 进入软件节点提醒弹窗：每个处理中事故案件的下一节点与剩余天数
    if (showReminder && uiState.reminderItems.isNotEmpty()) {
        ReminderDialog(
            items = uiState.reminderItems,
            onDismiss = { showReminder = false },
            onOpenCase = { id ->
                showReminder = false
                onOpenCase(id)
            }
        )
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
                            FormatUtils.unifiedTitle(case),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                val isViolation = case.caseType == CaseType.VIOLATION
                SheetAction(
                    icon = { Icon(Icons.Default.Edit, null, tint = MaterialTheme.colorScheme.primary) },
                    label = if (isViolation) "编辑违法案件" else "编辑事故",
                    onClick = { viewModel.onEvent(HomeUiEvent.EditCase(case.id)) }
                )
                if (case.status != CaseStatus.CLOSED) {
                    SheetAction(
                        icon = { Icon(Icons.Default.CheckCircle, null, tint = SuccessColor) },
                        label = if (isViolation) "标记已结案" else "标记已认定",
                        onClick = { viewModel.onEvent(HomeUiEvent.CloseCase(case.id)) }
                    )
                }
                SheetAction(
                    icon = { Icon(Icons.Default.Delete, null, tint = DangerColor) },
                    label = if (isViolation) "删除违法案件" else "删除事故",
                    danger = true,
                    onClick = {
                        viewModel.onEvent(HomeUiEvent.DismissLongPress)
                        deleteTarget = case
                    }
                )
            }
        }
        }

        // v1.90.3 可拖拽悬浮按钮：自由移动位置（避开液态玻璃底栏），位置持久化
        DraggableFab(
            screenWidthPx = with(LocalDensity.current) { maxWidth.toPx() }.roundToInt(),
            screenHeightPx = with(LocalDensity.current) { maxHeight.toPx() }.roundToInt(),
            onClick = { viewModel.onEvent(HomeUiEvent.NavigateToCreate) }
        )
    }

    // 删除二次确认（左滑删除 / 长按删除共用）
    deleteTarget?.let { target ->
        val isViolation = target.caseType == CaseType.VIOLATION
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(if (isViolation) "删除违法案件" else "删除事故") },
            text = { Text("确定要删除 ${target.caseNumber} 吗？删除后不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.onEvent(HomeUiEvent.DeleteCase(target.id))
                    deleteTarget = null
                }) { Text("删除", color = DangerColor) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消") }
            }
        )
    }
}

/**
 * v1.76 进入软件节点提醒弹窗：列出每个处理中事故案件的下一节点与剩余天数；
 * 点击案件行跳转对应案件详情，点「知道了」关闭。
 */
@Composable
private fun ReminderDialog(
    items: List<AccidentReminder>,
    onDismiss: () -> Unit,
    onOpenCase: (Long) -> Unit
) {
    val dateFmt = SimpleDateFormat("M月d日", Locale.getDefault())
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            // v1.77 仅标题「案件节点提醒」居中；v1.79 移除副文案
            Text(
                "案件节点提醒",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .heightIn(max = 400.dp)
            ) {
                items.forEach { item ->
                    val overdue = item.daysLeft < 0
                    val soon = !overdue && item.daysLeft <= 3
                    val bg = when {
                        item.isN4Overdue -> WarningColor.copy(alpha = 0.10f)
                        overdue -> DangerColor.copy(alpha = 0.08f)
                        soon -> WarningColor.copy(alpha = 0.08f)
                        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    }
                    val badgeText = when {
                        item.isN4Overdue -> "待办理"
                        overdue -> "已超期 ${-item.daysLeft} 天"
                        item.daysLeft == 0 -> "今天到期"
                        else -> "剩余 ${item.daysLeft} 天"
                    }
                    val badgeColor = when {
                        item.isN4Overdue -> WarningColor
                        overdue -> DangerColor
                        else -> if (soon) WarningColor else MaterialTheme.colorScheme.primary
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(bg)
                            .clickable { onOpenCase(item.caseId) }
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = item.caseTitle,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = badgeText,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium,
                                color = badgeColor
                            )
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "下一节点：${item.nodeName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (item.expectedDate > 0) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "预计 ${dateFmt.format(Date(item.expectedDate))}" +
                                    if (item.isN4Overdue) " · 出具时间已超过，请注意办理" else "",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (item.isN4Overdue) WarningColor
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("知道了") }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Composable
private fun StatMiniCard(
    label: String,
    value: Int,
    color: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    MiuiCard(modifier = modifier, onClick = onClick) {
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
private fun EmptyState(category: CaseType) {
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
            if (category == CaseType.ACCIDENT) "暂无事故记录" else "暂无违法记录",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            if (category == CaseType.ACCIDENT) "点击 + 添加第一个事故"
            else "点击 + 添加第一个违法案件",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * v1.71 加载骨架屏：3 张占位卡片 + 透明度脉动，贴合真实卡片结构，
 * 避免加载瞬间页面跳动；自动适配深色模式（surfaceVariant 色）。
 */
@Composable
private fun LoadingSkeleton() {
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.45f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(durationMillis = 700),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "skeleton_alpha"
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        repeat(3) {
            val blockColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha)
            MiuiCard(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .width(64.dp)
                            .height(20.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(blockColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(14.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(blockColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .width(72.dp)
                            .height(24.dp)
                            .clip(RoundedCornerShape(50))
                            .background(blockColor)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.72f)
                        .height(18.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(blockColor)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .height(12.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(blockColor)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .width(40.dp)
                            .height(16.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(blockColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(10.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(blockColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .width(56.dp)
                            .height(14.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(blockColor)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CaseCardItem(
    case: Case,
    nextNode: FlowNode?,
    overdueBase: Long,
    isRegistered: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit
) {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val now = System.currentTimeMillis()
    val isClosed = case.status == CaseStatus.CLOSED
    // v1.66 超期口径：事故案件按「事故认定书出具」节点预计日期（overdueBase 由 ViewModel 计算）；
    // 0 表示已出证/无法推算，不按超期显示
    val daysLeft = overdueBase.takeIf { it > 0 }?.let { CalendarUtil.daysBetween(now, it) }
    val isOverdue = daysLeft != null && daysLeft < 0
    val effectiveDays = daysLeft ?: case.daysRemaining(now)
    // 状态跟随流程：已认定 / 处理中（已完成受案登记）/ 待受案（未完成受案登记）
    val statusText = when {
        isClosed -> "已认定"
        isRegistered -> "处理中"
        else -> "待受案"
    }
    val statusColor = when {
        isClosed -> SuccessColor
        isOverdue -> DangerColor
        isRegistered -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    MiuiCard(
        onClick = onClick,
        onLongClick = onLongPress,
        modifier = Modifier.fillMaxWidth()
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
            // 胶囊状态标签（澎湃/MIUI 风格），状态与流程对应
            Text(
                text = statusText,
                style = MaterialTheme.typography.labelMedium,
                color = statusColor,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .background(statusColor.copy(alpha = 0.12f), RoundedCornerShape(50))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = FormatUtils.unifiedTitle(case),
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
        } else {
            // v1.68 违法案件副行：行政案件 / 刑事案件
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = case.violationType.displayName,
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
                color = if (isClosed) SuccessColor else MaterialTheme.colorScheme.primary
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
            if (isClosed) {
                // 已认定终态：显示完成信息，不再展示剩余天数/截止
                Text(
                    text = "案件已认定 ✓",
                    style = MaterialTheme.typography.bodySmall,
                    color = SuccessColor,
                    fontWeight = FontWeight.Medium
                )
            } else if (nextNode != null) {
                Text(
                    text = "下一阶段：${nextNode.name}",
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
                // 无待办节点 = 流程全部完成，可标记已认定
                Text(
                    text = "流程已完成，可标记已认定",
                    style = MaterialTheme.typography.bodySmall,
                    color = SuccessColor,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            if (isClosed) {
                Text(
                    text = "已结束",
                    style = MaterialTheme.typography.labelMedium,
                    color = SuccessColor,
                    fontWeight = FontWeight.Medium
                )
            } else if (isOverdue) {
                Text(
                    text = "已超期 ${-effectiveDays} 天",
                    style = MaterialTheme.typography.labelMedium,
                    color = DangerColor
                )
            } else if (effectiveDays in 0..3) {
                Text(
                    text = if (effectiveDays == 0) "今天到期" else "剩余 $effectiveDays 天",
                    style = MaterialTheme.typography.labelMedium,
                    color = WarningColor
                )
            } else {
                // 无超期基准（认定书已出具/无法推算）时按法定截止展示；法定截止已过不再显示负剩余
                Text(
                    text = if (effectiveDays >= 0) "剩余 $effectiveDays 天"
                           else "法定截止：${dateFormat.format(Date(case.deadline))}",
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

/**
 * v1.90.3 可拖拽悬浮按钮（案件页「+」）：
 * - 按住拖动自由定位，松手停住并持久化到 SettingsManager（重启/切页保持）；
 * - 轻点仍触发新建（拖动超过阈值才进入移动，不误触）；
 * - 拖动边界限制在屏幕内（四周留 16dp），默认位置避开液态玻璃底栏。
 */
@Composable
private fun DraggableFab(
    screenWidthPx: Int,
    screenHeightPx: Int,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val fabSizePx = with(density) { 56.dp.toPx() }
    val marginPx = with(density) { 16.dp.toPx() }
    // 液态玻璃底栏悬浮占用（navigationBars + 64dp 胶囊 + 上下边距）+ 余量
    val bottomReservePx = with(density) { 96.dp.toPx() }

    val saved = SettingsManager.fabPosition.collectAsStateWithLifecycle().value
    val savedOffset = saved?.let { Offset(it.first, it.second) }
    var pos by remember {
        mutableStateOf(
            savedOffset ?: Offset(
                (screenWidthPx - fabSizePx - marginPx).coerceAtLeast(0f),
                (screenHeightPx - bottomReservePx - fabSizePx - marginPx).coerceAtLeast(0f)
            )
        )
    }
    var dragging by remember { mutableStateOf(false) }

    // 外部持久化变化（如从设置恢复）时同步；拖动中不覆盖
    LaunchedEffect(saved) { if (savedOffset != null && !dragging) pos = savedOffset }

    Box(
        modifier = Modifier
            .offset { IntOffset(pos.x.roundToInt(), pos.y.roundToInt()) }
            .graphicsLayer {
                scaleX = if (dragging) 1.08f else 1f
                scaleY = if (dragging) 1.08f else 1f
            }
            .size(56.dp)
            .pointerInput(screenWidthPx, screenHeightPx, fabSizePx, marginPx) {
                detectDragGestures(
                    onDragStart = { dragging = true },
                    onDragEnd = {
                        dragging = false
                        SettingsManager.saveFabPosition(context, pos.x, pos.y)
                    },
                    onDragCancel = { dragging = false },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val minX = marginPx
                        val minY = marginPx
                        val maxX = screenWidthPx - fabSizePx - marginPx
                        val maxY = screenHeightPx - fabSizePx - marginPx
                        pos = Offset(
                            (pos.x + dragAmount.x).coerceIn(minX, maxX),
                            (pos.y + dragAmount.y).coerceIn(minY, maxY)
                        )
                    }
                )
            }
            .shadow(if (dragging) 14.dp else 4.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.primary)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Default.Add,
            contentDescription = "新建事故",
            tint = MaterialTheme.colorScheme.onPrimary
        )
    }
}
