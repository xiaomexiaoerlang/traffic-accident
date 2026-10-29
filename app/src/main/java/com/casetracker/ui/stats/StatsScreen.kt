package com.casetracker.ui.stats

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.casetracker.domain.model.CaseStatus
import com.casetracker.theme.DangerColor
import com.casetracker.theme.SuccessColor
import com.casetracker.theme.WarningColor
import com.casetracker.ui.components.MiuiCard
import com.casetracker.ui.components.MiuiProgressBar
import com.casetracker.ui.components.MiuiTopAppBar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun StatsScreen(
    viewModel: StatsViewModel = hiltViewModel(),
    onBack: () -> Unit,
    onCaseClick: (Long) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    Scaffold(
        topBar = {
            MiuiTopAppBar(
                title = { Text("统计", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
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
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 总览卡片
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatCard(
                            label = "案件总数",
                            value = uiState.totalCount.toString(),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        StatCard(
                            label = "已结案",
                            value = uiState.closedCount.toString(),
                            color = SuccessColor,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatCard(
                            label = "已超期",
                            value = uiState.overdueCount.toString(),
                            color = DangerColor,
                            modifier = Modifier.weight(1f)
                        )
                        StatCard(
                            label = "进行中",
                            value = (uiState.totalCount - uiState.closedCount).toString(),
                            color = WarningColor,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // 预警统计（三色）
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        WarnMiniCard("正常", uiState.normalCount, SuccessColor, Modifier.weight(1f))
                        WarnMiniCard("即将到期", uiState.dueSoonCount, WarningColor, Modifier.weight(1f))
                        WarnMiniCard("已超期", uiState.warnOverdueCount, DangerColor, Modifier.weight(1f))
                    }
                }

                // 状态分布
                item {
                    MiuiCard {
                        Text("状态分布", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(12.dp))
                        CaseStatus.entries.forEach { status ->
                            val count = uiState.statusCounts[status] ?: 0
                            val ratio = if (uiState.totalCount > 0) count.toFloat() / uiState.totalCount else 0f
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    // 状态跟随流程：已认定 / 待受案 / 处理中（旧中间状态数据归入处理中）
                                    text = when (status) {
                                        CaseStatus.CLOSED -> "已认定"
                                        CaseStatus.PENDING_ACCEPTANCE -> "待受案"
                                        else -> "处理中"
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.width(64.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                MiuiProgressBar(
                                    progress = ratio,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "$count",
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.width(32.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }

                // 结案率
                item {
                    MiuiCard {
                        Text("结案率", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(12.dp))
                        val closeRate = if (uiState.totalCount > 0)
                            (uiState.closedCount.toFloat() / uiState.totalCount * 100).toInt() else 0
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "$closeRate%",
                                style = MaterialTheme.typography.displaySmall,
                                color = SuccessColor
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = "${uiState.closedCount} / ${uiState.totalCount} 件已结案",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // 7天内到期提醒
                item {
                    MiuiCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Notifications,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("近期到期提醒（7天内）", style = MaterialTheme.typography.titleMedium)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        if (uiState.dueReminders.isEmpty()) {
                            Text(
                                text = "未来7天内没有节点到期",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            uiState.dueReminders.forEach { reminder ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { onCaseClick(reminder.case.id) }
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(
                                                when {
                                                    reminder.isOverdue -> DangerColor
                                                    reminder.daysLeft <= 3 -> WarningColor
                                                    else -> MaterialTheme.colorScheme.primary
                                                }
                                            )
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${reminder.case.caseNumber} · ${reminder.nodeTitle}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = dateFormat.format(Date(reminder.expectedDate)),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = when {
                                            reminder.isOverdue -> "已超期 ${-reminder.daysLeft}天"
                                            reminder.daysLeft == 0 -> "今天"
                                            else -> "${reminder.daysLeft}天后"
                                        },
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (reminder.isOverdue) DangerColor
                                                else if (reminder.daysLeft <= 3) WarningColor
                                                else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    MiuiCard(modifier = modifier) {
        Text(
            text = value,
            style = MaterialTheme.typography.displaySmall,
            color = color
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun WarnMiniCard(
    label: String,
    value: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    MiuiCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.titleLarge,
                color = color
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
