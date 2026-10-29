package com.casetracker.ui.accident

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.casetracker.data.repository.CaseRepository
import com.casetracker.domain.model.AccidentFlowTemplate
import com.casetracker.domain.model.AccidentParser
import com.casetracker.domain.model.AccidentProcedure
import com.casetracker.domain.model.Case
import com.casetracker.domain.model.CaseEvent
import com.casetracker.domain.model.CaseStatus
import com.casetracker.domain.model.CaseType
import com.casetracker.domain.model.FlowNode
import com.casetracker.domain.model.NodeState
import com.casetracker.domain.model.ViolationFlowTemplate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 一般程序事故时间线条目 */
data class AccidentTimeline(
    val case: Case,
    val nodes: List<FlowNode>
)

data class AccidentUiState(
    val category: CaseType = CaseType.ACCIDENT,
    val timelines: List<AccidentTimeline> = emptyList(),
    val isLoading: Boolean = true,
    val message: String = "",
    /** 长按事故流程卡片 → 打开案件详情 */
    val navigateToCaseId: Long? = null
)

@HiltViewModel
class AccidentViewModel @Inject constructor(
    private val repository: CaseRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AccidentUiState())
    val uiState: StateFlow<AccidentUiState> = _uiState.asStateFlow()

    private var category: CaseType = CaseType.ACCIDENT

    init {
        loadTimelines()
    }

    /** v1.68 联动：进程页类型分栏切换（交通事故 / 违法行为），列表按分类加载对应流程 */
    fun onCategoryChange(category: CaseType) {
        if (this.category == category) return
        this.category = category
        _uiState.value = _uiState.value.copy(category = category)
        loadTimelines()
    }

    private fun loadTimelines() {
        viewModelScope.launch {
            repository.getAllCases()
                .flatMapLatest { cases ->
                    // 按当前分类展示：事故 = 事故 + 一般程序 + 未归档；违法 = 违法案件 + 未归档
                    val typed = when (category) {
                        CaseType.ACCIDENT -> cases.filter {
                            it.caseType == CaseType.ACCIDENT &&
                                it.procedure == AccidentProcedure.GENERAL &&
                                !it.flowArchived
                        }
                        CaseType.VIOLATION -> cases.filter {
                            it.caseType == CaseType.VIOLATION && !it.flowArchived
                        }
                    }
                    if (typed.isEmpty()) flowOf(emptyList())
                    else combine(typed.map { repository.getCaseDetail(it.id) }) { details ->
                        details.mapNotNull { detail ->
                            val case = detail.first ?: return@mapNotNull null
                            case to detail.second
                        }
                    }
                }
                .collect { pairs ->
                    val timelines = pairs.map { (case, events) ->
                        val nodes = when (case.caseType) {
                            CaseType.ACCIDENT -> AccidentFlowTemplate.buildNodes(case, events)
                            CaseType.VIOLATION -> ViolationFlowTemplate.buildNodes(case, events)
                        }
                        AccidentTimeline(case, nodes)
                    }
                    // 未完成流程在上（与案件页排序一致，按更新时间降序）；
                    // 已认定终态或全部完成的流程收纳下沉到底部
                    val active = timelines.filter { tl ->
                        tl.case.status != CaseStatus.CLOSED &&
                            tl.nodes.any { it.state != NodeState.DONE }
                    }.sortedByDescending { it.case.updatedAt }
                    val completed = timelines.filter { tl ->
                        tl.case.status == CaseStatus.CLOSED ||
                            (tl.nodes.isNotEmpty() && tl.nodes.all { it.state == NodeState.DONE })
                    }.sortedByDescending { it.case.updatedAt }
                    _uiState.value = AccidentUiState(
                        category = category,
                        timelines = active + completed,
                        isLoading = false
                    )
                }
        }
    }

    /** 点「完成」：录入今天为该节点实际日期，后续节点预计日期自动重算 */
    fun completeNode(caseId: Long, node: FlowNode) {
        val now = System.currentTimeMillis()
        recordNode(caseId, node, now, "已完成")
    }

    /** 修改/补录节点日期（用户输入日期文本），后续节点预计日期自动重算 */
    fun recordNodeByDate(caseId: Long, node: FlowNode, dateText: String) {
        val parsed = AccidentParser.parseDate(dateText)
        if (parsed <= 0L) {
            _uiState.value = _uiState.value.copy(message = "日期格式无法识别，请用 2026-09-10 或 2026年9月10日")
            return
        }
        recordNode(caseId, node, parsed, "手动录入")
    }

    private fun recordNode(caseId: Long, node: FlowNode, dateMs: Long, desc: String) {
        viewModelScope.launch {
            repository.recordNodeDate(caseId, node.code, dateMs)
        }
    }

    fun onMessageShown() {
        _uiState.value = _uiState.value.copy(message = "")
    }

    /** 左滑移除已完成的事故流程卡片：仅归档流程，案件记录保留在「案件」页 */
    fun archiveFlow(caseId: Long) {
        viewModelScope.launch {
            repository.archiveFlow(caseId)
            _uiState.value = _uiState.value.copy(message = "流程已移除，案件记录保留在「案件」页")
        }
    }

    /** 长按流程卡片：打开案件详情 */
    fun openCaseDetail(caseId: Long) {
        _uiState.value = _uiState.value.copy(navigateToCaseId = caseId)
    }

    fun onNavigationConsumed() {
        _uiState.value = _uiState.value.copy(navigateToCaseId = null)
    }

    /** 免鉴定快捷出证：跳过 鉴定委托/鉴定报告出具/鉴定报告送达/事故调查报告，认定书 = 事故发生 + 10 工作日 */
    fun skipInvestigation(caseId: Long) {
        viewModelScope.launch {
            repository.setSkipInvestigation(caseId)
            _uiState.value = _uiState.value.copy(message = "已选择免鉴定：事故发生之日起 10 个工作日内出具事故认定书")
        }
    }

    /** 一般程序快速处理：提交《快速处理申请书》（申请日 = 今天） */
    fun submitQuickProcess(caseId: Long) {
        viewModelScope.launch {
            repository.setQuickProcess(caseId)
            _uiState.value = _uiState.value.copy(message = "已提交快速处理申请：事故发生之日起 10 个工作日内出具事故认定书")
        }
    }

    /** v1.74 撤销误点完成：长按已完成节点或「已完成」按钮 → 该节点恢复为进行中，后续预计日期自动重算 */
    fun undoNode(caseId: Long, node: FlowNode) {
        viewModelScope.launch {
            repository.undoNodeComplete(caseId, node.code)
            _uiState.value = _uiState.value.copy(message = "已撤销「${node.name}」完成，该节点恢复为进行中")
        }
    }
}
