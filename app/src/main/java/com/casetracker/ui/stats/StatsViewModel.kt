package com.casetracker.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.casetracker.data.repository.CaseRepository
import com.casetracker.domain.model.Case
import com.casetracker.domain.model.CaseStatus
import com.casetracker.domain.model.CaseType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 预警级别 */
enum class WarnLevel { NORMAL, DUE_SOON, OVERDUE }

/** 到期提醒项 */
data class DueReminder(
    val case: Case,
    val nodeTitle: String,
    val expectedDate: Long,
    val daysLeft: Int,
    val isOverdue: Boolean
)

data class StatsUiState(
    val totalCount: Int = 0,
    val statusCounts: Map<CaseStatus, Int> = emptyMap(),
    val overdueCount: Int = 0,
    val closedCount: Int = 0,
    // 预警统计（三色）
    val normalCount: Int = 0,
    val dueSoonCount: Int = 0,
    val warnOverdueCount: Int = 0,
    // 7天内到期提醒
    val dueReminders: List<DueReminder> = emptyList(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val repository: CaseRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(StatsUiState())
    val uiState: StateFlow<StatsUiState> = _uiState.asStateFlow()

    init {
        loadStats()
    }

    private fun loadStats() {
        viewModelScope.launch {
            val casesFlow = repository.getAllCases()
            val pendingFlow = repository.getAllPendingNodes()
            kotlinx.coroutines.flow.combine(casesFlow, pendingFlow) { cases, pendingNodes ->
                val now = System.currentTimeMillis()
                val dayMs = 24 * 60 * 60 * 1000L
                val statusCounts = CaseStatus.entries.associateWith { status ->
                    // v1.80 事故不再有待受案：历史事故若为 PENDING_ACCEPTANCE 归入「处理中」
                    cases.count { c ->
                        val effective = if (c.caseType == CaseType.ACCIDENT &&
                            c.status == CaseStatus.PENDING_ACCEPTANCE) CaseStatus.IN_PROGRESS else c.status
                        effective == status
                    }
                }
                val overdue = cases.count { it.isOverdue(now) }
                val closed = cases.count { it.status == CaseStatus.CLOSED }

                // 预警：正常（未到期>3天）/ 即将到期（3天内）/ 已超期
                val normal = cases.count { c ->
                    c.status != CaseStatus.CLOSED && !c.isOverdue(now) &&
                        (c.deadline <= 0 || c.daysRemaining(now) > 3)
                }
                val dueSoon = cases.count { c ->
                    c.status != CaseStatus.CLOSED && !c.isOverdue(now) &&
                        c.deadline > 0 && c.daysRemaining(now) in 0..3
                }

                // 7天内到期提醒：基于流程节点预计日期
                val reminders = buildList {
                    pendingNodes.forEach { node ->
                        val case = cases.firstOrNull { it.id == node.caseId } ?: return@forEach
                        if (case.status == CaseStatus.CLOSED) return@forEach
                        if (node.expectedDate <= 0) return@forEach
                        val daysLeft = ((node.expectedDate - now) / dayMs).toInt()
                        if (daysLeft <= 7) {
                            add(DueReminder(
                                case = case,
                                nodeTitle = node.title,
                                expectedDate = node.expectedDate,
                                daysLeft = daysLeft,
                                isOverdue = daysLeft < 0
                            ))
                        }
                    }
                }.sortedBy { it.expectedDate }

                _uiState.value = StatsUiState(
                    totalCount = cases.size,
                    statusCounts = statusCounts,
                    overdueCount = overdue,
                    closedCount = closed,
                    normalCount = normal,
                    dueSoonCount = dueSoon,
                    warnOverdueCount = overdue,
                    dueReminders = reminders.take(20),
                    isLoading = false
                )
            }.collect {}
        }
    }
}
