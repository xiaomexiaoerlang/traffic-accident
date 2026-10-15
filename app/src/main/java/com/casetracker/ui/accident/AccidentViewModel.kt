package com.casetracker.ui.accident

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.casetracker.data.repository.CaseRepository
import com.casetracker.domain.model.AccidentFlowTemplate
import com.casetracker.domain.model.AccidentParser
import com.casetracker.domain.model.AccidentProcedure
import com.casetracker.domain.model.Case
import com.casetracker.domain.model.CaseEvent
import com.casetracker.domain.model.CaseType
import com.casetracker.domain.model.FlowNode
import com.casetracker.domain.model.NodeState
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
    val timelines: List<AccidentTimeline> = emptyList(),
    val isLoading: Boolean = true,
    val message: String = ""
)

@HiltViewModel
class AccidentViewModel @Inject constructor(
    private val repository: CaseRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AccidentUiState())
    val uiState: StateFlow<AccidentUiState> = _uiState.asStateFlow()

    init {
        loadTimelines()
    }

    private fun loadTimelines() {
        viewModelScope.launch {
            repository.getAllCases()
                .flatMapLatest { cases ->
                    val accidentCases = cases.filter {
                        it.caseType == CaseType.ACCIDENT && it.procedure == AccidentProcedure.GENERAL
                    }
                    if (accidentCases.isEmpty()) flowOf(emptyList())
                    else combine(accidentCases.map { repository.getCaseDetail(it.id) }) { details ->
                        details.mapNotNull { detail ->
                            val case = detail.first ?: return@mapNotNull null
                            case to detail.second
                        }
                    }
                }
                .collect { pairs ->
                    val timelines = pairs.map { (case, events) ->
                        AccidentTimeline(case, AccidentFlowTemplate.buildNodes(case, events))
                    }
                    // 未完成流程在上（与案件页排序一致，按更新时间降序）；
                    // 全部完成（7/7）的流程收纳下沉到底部
                    val active = timelines.filter { tl ->
                        tl.nodes.any { it.state != NodeState.DONE }
                    }.sortedByDescending { it.case.updatedAt }
                    val completed = timelines.filter { tl ->
                        tl.nodes.isNotEmpty() && tl.nodes.all { it.state == NodeState.DONE }
                    }.sortedByDescending { it.case.updatedAt }
                    _uiState.value = AccidentUiState(
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
            repository.recordNodeDate(
                caseId = caseId,
                event = CaseEvent(
                    caseId = caseId,
                    nodeCode = node.code,
                    eventType = "FLOW",
                    title = node.name,
                    description = desc,
                    occurredAt = dateMs,
                    isMilestone = true
                )
            )
        }
    }

    fun onMessageShown() {
        _uiState.value = _uiState.value.copy(message = "")
    }
}
