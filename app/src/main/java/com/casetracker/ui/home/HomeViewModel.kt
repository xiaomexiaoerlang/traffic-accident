package com.casetracker.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.casetracker.data.repository.CaseRepository
import com.casetracker.domain.model.AccidentFlowTemplate
import com.casetracker.domain.model.CalendarUtil
import com.casetracker.domain.model.Case
import com.casetracker.domain.model.CaseStatus
import com.casetracker.domain.model.CaseType
import com.casetracker.domain.model.FlowNode
import com.casetracker.domain.model.FormatUtils
import com.casetracker.domain.model.NodeState
import com.casetracker.domain.model.ViolationFlowTemplate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 案件页筛选模式（v1.68：按案件分类提供两套口径）
 * - 事故分类：待处理事故 / 已认定事故 / 本年度事故（受案登记筛选已按用户要求取消）
 * - 违法分类：已受案 / 待立案 / 待处罚 / 已结案 / 本年度违法
 */
enum class HomeFilter(val displayName: String) {
    // 事故
    ACC_PENDING("待处理事故"),
    ACC_CLOSED("已认定事故"),
    ACC_THIS_YEAR("本年度事故"),
    // 违法
    VIO_REGISTERED("已受案"),
    VIO_PENDING("待立案"),
    VIO_PENALTY("待处罚"),
    VIO_CLOSED("已结案"),
    VIO_THIS_YEAR("本年度违法");

    companion object {
        fun forCategory(category: CaseType): List<HomeFilter> = when (category) {
            CaseType.ACCIDENT -> listOf(ACC_PENDING, ACC_CLOSED, ACC_THIS_YEAR)
            CaseType.VIOLATION -> listOf(VIO_REGISTERED, VIO_PENDING, VIO_PENALTY, VIO_CLOSED)
        }

        fun defaultFor(category: CaseType): HomeFilter = when (category) {
            CaseType.ACCIDENT -> ACC_PENDING
            CaseType.VIOLATION -> VIO_PENDING
        }
    }
}

data class HomeUiState(
    val category: CaseType = CaseType.ACCIDENT,
    val cases: List<Case> = emptyList(),
    val isLoading: Boolean = true,
    val filterMode: HomeFilter = HomeFilter.ACC_PENDING,
    // 已完成受案登记的案件 ID 集合（事故 NR / 违法 VR）
    val registeredCaseIds: Set<Long> = emptySet(),
    val violationRegisteredCaseIds: Set<Long> = emptySet(),
    // 每案件下一待办节点（与详情页同一套流程模板计算，兼容占位节点缺节点码的存量案件）
    val nextNodeByCase: Map<Long, FlowNode> = emptyMap(),
    // 超期基准日期（事故案件=认定书出具节点预计日期，0=已出证/无法推算；违法案件=法定截止）
    val overdueBaseByCase: Map<Long, Long> = emptyMap(),
    // 事故统计卡片
    val pendingCount: Int = 0,       // 待处理事故（未认定）
    val dueSoonCount: Int = 0,       // 将超期事故（未认定且 0~3 天）
    val closedCount: Int = 0,        // 已认定事故
    val thisYearCount: Int = 0,      // 本年度事故
    // 违法统计卡片（v1.68：已受案 / 待立案 / 待处罚 / 已结案 / 本年度违法）
    val vioRegisteredCount: Int = 0, // 已受案（违法受案登记 VR 已完成）
    val vioPendingCount: Int = 0,    // 待立案（未受案登记且未结案）
    val vioPenaltyCount: Int = 0,    // 待处罚（已受案登记且未结案）
    val vioClosedCount: Int = 0,     // 已结案
    val vioThisYearCount: Int = 0,   // 本年度违法
    // v1.76 进入软件提醒：每个处理中事故案件的下一节点与剩余天数（仅事故、未认定、有下一节点）
    val reminderItems: List<AccidentReminder> = emptyList(),
    // 长按操作目标
    val longPressCase: Case? = null,
)

/** v1.76 主界面启动弹窗提醒条目 */
data class AccidentReminder(
    val caseId: Long,
    val caseTitle: String,      // 统一格式案件名称
    val nodeName: String,       // 下一待办节点名称
    val expectedDate: Long,     // 下一节点预计日期
    val daysLeft: Int,          // 剩余天数（>0 剩余 / =0 今天到期 / <0 已超期）
    val isN4Overdue: Boolean    // 下一节点为事故调查报告（N4）且已超期：仅颜色提醒不显示超期文字
)

sealed interface HomeUiEvent {
    data object LoadCases : HomeUiEvent
    data class CategoryChange(val category: CaseType) : HomeUiEvent
    data class FilterBy(val mode: HomeFilter) : HomeUiEvent
    data class OpenCaseDetail(val caseId: Long) : HomeUiEvent
    data object NavigateToCreate : HomeUiEvent
    data class LongPressCase(val case: Case) : HomeUiEvent
    data object DismissLongPress : HomeUiEvent
    data class EditCase(val caseId: Long) : HomeUiEvent
    data class DeleteCase(val caseId: Long) : HomeUiEvent
    data class CloseCase(val caseId: Long) : HomeUiEvent
}

sealed interface HomeNavigation {
    data class ToDetail(val caseId: Long) : HomeNavigation
    data class ToEdit(val caseId: Long) : HomeNavigation
    data object ToCreate : HomeNavigation
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: CaseRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _navigation = MutableStateFlow<HomeNavigation?>(null)
    val navigation: StateFlow<HomeNavigation?> = _navigation.asStateFlow()

    private var category: CaseType = CaseType.ACCIDENT
    private var filterMode: HomeFilter = HomeFilter.ACC_PENDING

    init {
        loadCases()
    }

    fun onEvent(event: HomeUiEvent) {
        when (event) {
            is HomeUiEvent.LoadCases -> loadCases()
            is HomeUiEvent.CategoryChange -> {
                category = event.category
                filterMode = HomeFilter.defaultFor(category)
                _uiState.value = _uiState.value.copy(category = category, filterMode = filterMode)
                loadCases()
            }
            is HomeUiEvent.FilterBy -> {
                filterMode = event.mode
                _uiState.value = _uiState.value.copy(filterMode = filterMode)
                loadCases()
            }
            is HomeUiEvent.OpenCaseDetail -> {
                _navigation.value = HomeNavigation.ToDetail(event.caseId)
            }
            is HomeUiEvent.NavigateToCreate -> {
                _navigation.value = HomeNavigation.ToCreate
            }
            is HomeUiEvent.LongPressCase ->
                _uiState.value = _uiState.value.copy(longPressCase = event.case)
            is HomeUiEvent.DismissLongPress ->
                _uiState.value = _uiState.value.copy(longPressCase = null)
            is HomeUiEvent.EditCase -> {
                _uiState.value = _uiState.value.copy(longPressCase = null)
                _navigation.value = HomeNavigation.ToEdit(event.caseId)
            }
            is HomeUiEvent.DeleteCase -> {
                _uiState.value = _uiState.value.copy(longPressCase = null)
                viewModelScope.launch { repository.deleteCase(event.caseId) }
            }
            is HomeUiEvent.CloseCase -> {
                _uiState.value = _uiState.value.copy(longPressCase = null)
                viewModelScope.launch {
                    repository.updateStatus(event.caseId, CaseStatus.CLOSED, 100)
                }
            }
        }
    }

    fun onNavigationConsumed() { _navigation.value = null }

    private fun loadCases() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val allFlow = repository.getAllCases()
            val nodeEventsFlow = repository.getAllNodeEvents()
            val doneCountFlow = repository.getDoneNodeCounts()
            val registeredFlow = repository.getRegisteredCaseIds()
            val vioRegisteredFlow = repository.getViolationRegisteredCaseIds()
            combine(allFlow, nodeEventsFlow, doneCountFlow, registeredFlow, vioRegisteredFlow) { cases, nodeEvents, doneCounts, registeredIds, vioRegisteredIds ->
                val now = System.currentTimeMillis()
                val updatedCases = cases.map { c ->
                    val pathCodes = when (c.caseType) {
                        CaseType.ACCIDENT -> AccidentFlowTemplate.pathNodes(c).map { it.code }
                        CaseType.VIOLATION -> ViolationFlowTemplate.pathNodes(c).map { it.code }
                    }
                    val doneByCode = doneCounts[c.id] ?: emptyMap()
                    val done = pathCodes.sumOf { doneByCode[it] ?: 0 }
                    val nodeProgress =
                        (done.toFloat() / pathCodes.size.coerceAtLeast(1) * 100).toInt().coerceIn(0, 100)
                    c.copy(progress = if (c.status == CaseStatus.CLOSED) 100 else nodeProgress)
                }

                // 每案件按流程模板构建节点（与详情页完全同口径，兼容存量案件占位节点缺节点码），
                // 下一待办节点 = 首个未完成节点；超期基准 = 事故认定书出具（N5）节点（已完成=0 不再显示超期）
                val eventsByCase = nodeEvents.groupBy { it.caseId }
                val nodesByCase = updatedCases.associate { c ->
                    val events = eventsByCase[c.id] ?: emptyList()
                    c.id to when (c.caseType) {
                        CaseType.ACCIDENT -> AccidentFlowTemplate.buildNodes(c, events, now)
                        CaseType.VIOLATION -> ViolationFlowTemplate.buildNodes(c, events, now)
                    }
                }
                val nextMap = nodesByCase.mapNotNull { (caseId, nodes) ->
                    AccidentFlowTemplate.nextPendingNode(nodes)?.let { caseId to it }
                }.toMap()
                val overdueBaseByCase = updatedCases.associate { c ->
                    val base = when {
                        c.status == CaseStatus.CLOSED -> 0L
                        c.caseType == CaseType.ACCIDENT -> {
                            val n5 = nodesByCase[c.id]?.firstOrNull {
                                it.code == AccidentFlowTemplate.CERTIFICATE_CODE
                            }
                            if (n5 != null && n5.state != NodeState.DONE) {
                                n5.expectedDate.takeIf { it > 0 } ?: 0L
                            } else 0L
                        }
                        else -> c.deadline.takeIf { it > 0 } ?: 0L
                    }
                    c.id to base
                }

                // 按案件分类拆分组
                val accidentCases = updatedCases.filter { it.caseType == CaseType.ACCIDENT }
                val violationCases = updatedCases.filter { it.caseType == CaseType.VIOLATION }

                // v1.76 启动提醒：处理中的事故案件（未认定且有下一待办节点）的下一节点 + 剩余天数
                val reminderItems = accidentCases.mapNotNull { c ->
                    if (c.status == CaseStatus.CLOSED) return@mapNotNull null
                    val node = nextMap[c.id] ?: return@mapNotNull null
                    val expected = node.expectedDate
                    if (expected <= 0) return@mapNotNull null
                    val daysLeft = CalendarUtil.daysBetween(now, expected)
                    AccidentReminder(
                        caseId = c.id,
                        caseTitle = FormatUtils.unifiedTitle(c),
                        nodeName = node.name,
                        expectedDate = expected,
                        daysLeft = daysLeft,
                        isN4Overdue = node.code == AccidentFlowTemplate.REPORT_CODE && daysLeft < 0
                    )
                }

                // 事故统计
                val pending = accidentCases.count { it.status != CaseStatus.CLOSED }
                val dueSoon = accidentCases.count { c ->
                    if (c.status == CaseStatus.CLOSED) return@count false
                    val expected = overdueBaseByCase[c.id] ?: 0L
                    expected > 0 && CalendarUtil.daysBetween(now, expected) in 0..3
                }
                val closed = accidentCases.count { it.status == CaseStatus.CLOSED }

                // 违法统计（v1.68）
                val vioRegistered = violationCases.count { it.id in vioRegisteredIds }
                val vioPending = violationCases.count {
                    it.status != CaseStatus.CLOSED && it.id !in vioRegisteredIds
                }
                val vioPenalty = violationCases.count {
                    it.id in vioRegisteredIds && it.status != CaseStatus.CLOSED
                }
                val vioClosed = violationCases.count { it.status == CaseStatus.CLOSED }

                // 本年度案件数量：按案发日期（事故/违法时间）所在年份统计（无案发日期时按创建时间）
                fun caseYear(c: Case): Int {
                    val ref = if (c.accidentDate > 0) c.accidentDate else c.createdAt
                    return if (ref <= 0) -1 else CalendarUtil.toLocalDate(ref).year
                }
                val thisYear = CalendarUtil.toLocalDate(now).year
                val thisYearCount = accidentCases.count { caseYear(it) == thisYear }
                val vioThisYearCount = violationCases.count { caseYear(it) == thisYear }

                // 当前分类下展示的案件池
                val categoryCases = when (category) {
                    CaseType.ACCIDENT -> accidentCases
                    CaseType.VIOLATION -> violationCases
                }

                // 筛选（事故：待处理/已认定/本年度；违法：已受案/待立案/待处罚/已结案/本年度）
                val filtered = when (filterMode) {
                    HomeFilter.ACC_PENDING -> categoryCases.filter { it.status != CaseStatus.CLOSED }
                    HomeFilter.ACC_CLOSED -> categoryCases.filter { it.status == CaseStatus.CLOSED }
                    HomeFilter.ACC_THIS_YEAR -> categoryCases.filter { caseYear(it) == thisYear }
                    HomeFilter.VIO_REGISTERED -> categoryCases.filter { it.id in vioRegisteredIds }
                    HomeFilter.VIO_PENDING -> categoryCases.filter {
                        it.status != CaseStatus.CLOSED && it.id !in vioRegisteredIds
                    }
                    HomeFilter.VIO_PENALTY -> categoryCases.filter {
                        it.id in vioRegisteredIds && it.status != CaseStatus.CLOSED
                    }
                    HomeFilter.VIO_CLOSED -> categoryCases.filter { it.status == CaseStatus.CLOSED }
                    HomeFilter.VIO_THIS_YEAR -> categoryCases.filter { caseYear(it) == thisYear }
                }

                _uiState.value = _uiState.value.copy(
                    cases = filtered,
                    registeredCaseIds = registeredIds,
                    violationRegisteredCaseIds = vioRegisteredIds,
                    nextNodeByCase = nextMap,
                    overdueBaseByCase = overdueBaseByCase,
                    reminderItems = reminderItems,
                    pendingCount = pending,
                    dueSoonCount = dueSoon,
                    closedCount = closed,
                    thisYearCount = thisYearCount,
                    vioRegisteredCount = vioRegistered,
                    vioPendingCount = vioPending,
                    vioPenaltyCount = vioPenalty,
                    vioClosedCount = vioClosed,
                    vioThisYearCount = vioThisYearCount,
                    isLoading = false
                )
            }.collect {}
        }
    }
}
