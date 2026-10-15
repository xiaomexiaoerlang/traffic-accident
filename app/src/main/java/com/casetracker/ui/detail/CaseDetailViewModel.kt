package com.casetracker.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.casetracker.data.repository.CaseRepository
import com.casetracker.domain.model.AccidentFlowTemplate
import com.casetracker.domain.model.Case
import com.casetracker.domain.model.CaseEvent
import com.casetracker.domain.model.CaseStatus
import com.casetracker.domain.model.CaseType
import com.casetracker.domain.model.FlowNode
import com.casetracker.domain.model.NodeState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DetailUiState(
    val case: Case? = null,
    val events: List<CaseEvent> = emptyList(),
    val nodes: List<FlowNode> = emptyList(),
    val isLoading: Boolean = true,
    val showAddEventDialog: Boolean = false,
    val recordNodeCode: String? = null,   // 正在录入日期的节点
    val toastMessage: String? = null,     // 校验提示等
)

sealed interface DetailUiEvent {
    data object Load : DetailUiEvent
    data object AdvanceStatus : DetailUiEvent
    data class AddEvent(val title: String, val description: String, val isMilestone: Boolean) : DetailUiEvent
    data object ShowAddEventDialog : DetailUiEvent
    data object DismissAddEventDialog : DetailUiEvent
    data class ShowRecordDialog(val nodeCode: String) : DetailUiEvent
    data class RecordNodeDate(val nodeCode: String, val dateText: String) : DetailUiEvent
    data object DismissRecordDialog : DetailUiEvent
    data object ConsumeToast : DetailUiEvent
    data object DeleteCase : DetailUiEvent
    data object CloseCase : DetailUiEvent
    data object NavigateBack : DetailUiEvent
    data object NavigateToEdit : DetailUiEvent
}

@HiltViewModel
class CaseDetailViewModel @Inject constructor(
    private val repository: CaseRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DetailUiState())
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    private val _shouldNavigateBack = MutableStateFlow(false)
    val shouldNavigateBack: StateFlow<Boolean> = _shouldNavigateBack.asStateFlow()

    private val _shouldNavigateToEdit = MutableStateFlow(false)
    val shouldNavigateToEdit: StateFlow<Boolean> = _shouldNavigateToEdit.asStateFlow()

    private var currentCaseId: Long = 0L

    fun loadCase(caseId: Long) {
        currentCaseId = caseId
        viewModelScope.launch {
            repository.getCaseDetail(caseId).collect { (case, events) ->
                val now = System.currentTimeMillis()
                val updatedCase = case?.copy(progress = case.calculateProgress(now))
                val nodes = if (case != null && case.caseType == CaseType.ACCIDENT) {
                    AccidentFlowTemplate.buildNodes(case, events, now)
                } else emptyList()
                _uiState.value = _uiState.value.copy(
                    case = updatedCase,
                    events = events,
                    nodes = nodes,
                    isLoading = false
                )
            }
        }
    }

    fun onEvent(event: DetailUiEvent) {
        when (event) {
            is DetailUiEvent.Load -> loadCase(currentCaseId)
            is DetailUiEvent.AdvanceStatus -> advanceStatus()
            is DetailUiEvent.AddEvent -> addEvent(event.title, event.description, event.isMilestone)
            is DetailUiEvent.ShowAddEventDialog ->
                _uiState.value = _uiState.value.copy(showAddEventDialog = true)
            is DetailUiEvent.DismissAddEventDialog ->
                _uiState.value = _uiState.value.copy(showAddEventDialog = false)
            is DetailUiEvent.ShowRecordDialog -> showRecordDialog(event.nodeCode)
            is DetailUiEvent.RecordNodeDate -> recordNodeDate(event.nodeCode, event.dateText)
            is DetailUiEvent.DismissRecordDialog ->
                _uiState.value = _uiState.value.copy(recordNodeCode = null)
            is DetailUiEvent.ConsumeToast ->
                _uiState.value = _uiState.value.copy(toastMessage = null)
            is DetailUiEvent.DeleteCase -> deleteCase()
            is DetailUiEvent.CloseCase -> closeCase()
            is DetailUiEvent.NavigateBack -> _shouldNavigateBack.value = true
            is DetailUiEvent.NavigateToEdit -> _shouldNavigateToEdit.value = true
        }
    }

    fun onNavigationConsumed() {
        _shouldNavigateBack.value = false
        _shouldNavigateToEdit.value = false
    }

    private fun showRecordDialog(nodeCode: String) {
        val nodes = _uiState.value.nodes
        val (allowed, reason) = AccidentFlowTemplate.canRecord(nodeCode, nodes)
        if (!allowed) {
            _uiState.value = _uiState.value.copy(toastMessage = reason)
            return
        }
        _uiState.value = _uiState.value.copy(recordNodeCode = nodeCode)
    }

    private fun recordNodeDate(nodeCode: String, dateText: String) {
        val date = com.casetracker.domain.model.AccidentParser.parseDate(dateText)
        if (date <= 0L) {
            _uiState.value = _uiState.value.copy(
                toastMessage = "日期格式不正确，请输入如 2026-09-15",
                recordNodeCode = null
            )
            return
        }
        val node = _uiState.value.nodes.firstOrNull { it.code == nodeCode } ?: return
        viewModelScope.launch {
            val newEvent = CaseEvent(
                id = node.event?.id ?: 0,
                caseId = currentCaseId,
                eventType = "FLOW_NODE",
                title = node.name,
                description = "节点 ${node.code}：实际日期",
                occurredAt = date,
                isMilestone = node.isRequired,
                nodeCode = node.code,
                expectedDate = node.expectedDate,
                isAuto = node.isAuto,
                isRequired = node.isRequired
            )
            repository.recordNodeDate(currentCaseId, newEvent)
            _uiState.value = _uiState.value.copy(recordNodeCode = null)
        }
    }

    private fun advanceStatus() {
        val case = _uiState.value.case ?: return
        val nextStatus = case.status.next() ?: return
        val newProgress = nextStatus.minProgress
        viewModelScope.launch {
            repository.updateStatus(currentCaseId, nextStatus, newProgress)
            repository.addEvent(
                currentCaseId,
                CaseEvent(
                    caseId = currentCaseId,
                    eventType = "STATUS_CHANGE",
                    title = "状态变更：${case.status.displayName} → ${nextStatus.displayName}",
                    description = "案件状态推进至${nextStatus.displayName}",
                    occurredAt = System.currentTimeMillis(),
                    isMilestone = true
                )
            )
        }
    }

    private fun addEvent(title: String, description: String, isMilestone: Boolean) {
        if (title.isBlank()) return
        viewModelScope.launch {
            repository.addEvent(
                currentCaseId,
                CaseEvent(
                    caseId = currentCaseId,
                    eventType = "CUSTOM",
                    title = title,
                    description = description,
                    occurredAt = System.currentTimeMillis(),
                    isMilestone = isMilestone
                )
            )
            _uiState.value = _uiState.value.copy(showAddEventDialog = false)
        }
    }

    private fun deleteCase() {
        viewModelScope.launch {
            repository.deleteCase(currentCaseId)
            _shouldNavigateBack.value = true
        }
    }

    /** 标记结案（仅全部节点完成后可用） */
    private fun closeCase() {
        val nodes = _uiState.value.nodes
        if (nodes.isNotEmpty() && nodes.any { it.state != NodeState.DONE }) {
            _uiState.value = _uiState.value.copy(toastMessage = "还有节点未完成，无法结案")
            return
        }
        viewModelScope.launch {
            repository.updateStatus(currentCaseId, CaseStatus.CLOSED, 100)
            repository.addEvent(
                currentCaseId,
                CaseEvent(
                    caseId = currentCaseId,
                    eventType = "STATUS_CHANGE",
                    title = "案件已结案",
                    description = "全部流程节点已完成，标记结案",
                    occurredAt = System.currentTimeMillis(),
                    isMilestone = true
                )
            )
        }
    }
}
