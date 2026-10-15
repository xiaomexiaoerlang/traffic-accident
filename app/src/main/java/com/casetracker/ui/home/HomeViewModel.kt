package com.casetracker.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.casetracker.data.repository.CaseRepository
import com.casetracker.domain.model.AccidentFlowTemplate
import com.casetracker.domain.model.Case
import com.casetracker.domain.model.CaseEvent
import com.casetracker.domain.model.CaseStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val cases: List<Case> = emptyList(),
    val isLoading: Boolean = true,
    val filterStatus: CaseStatus? = null,
    // 每案件下一待办节点
    val nextNodeByCase: Map<Long, CaseEvent> = emptyMap(),
    // 统计卡片
    val processingCount: Int = 0,
    val dueSoonCount: Int = 0,
    val overdueCount: Int = 0,
    val closedCount: Int = 0,
    // 长按操作目标
    val longPressCase: Case? = null,
)

sealed interface HomeUiEvent {
    data object LoadCases : HomeUiEvent
    data class FilterByStatus(val status: CaseStatus?) : HomeUiEvent
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

    private var filterStatus: CaseStatus? = null

    init {
        loadCases()
    }

    fun onEvent(event: HomeUiEvent) {
        when (event) {
            is HomeUiEvent.LoadCases -> loadCases()
            is HomeUiEvent.FilterByStatus -> {
                filterStatus = event.status
                _uiState.value = _uiState.value.copy(filterStatus = event.status)
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
            val pendingFlow = repository.getAllPendingNodes()
            combine(allFlow, pendingFlow) { cases, pendingNodes ->
                val now = System.currentTimeMillis()
                val updatedCases = cases.map { it.copy(progress = it.calculateProgress(now)) }

                // 每个案件取顺序最靠前的未完成节点
                val nextMap = pendingNodes
                    .filter { it.caseId > 0 }
                    .groupBy { it.caseId }
                    .mapValues { (_, nodes) ->
                        nodes.minByOrNull { node ->
                            AccidentFlowTemplate.NODES.indexOfFirst { it.code == node.nodeCode }
                                .let { if (it < 0) Int.MAX_VALUE else it }
                        }!!
                    }

                val processing = updatedCases.count { it.status != CaseStatus.CLOSED }
                val dueSoon = updatedCases.count { c ->
                    c.status != CaseStatus.CLOSED && c.deadline > 0 &&
                        c.daysRemaining(now) in 0..3
                }
                val overdue = updatedCases.count { it.isOverdue(now) }
                val closed = updatedCases.count { it.status == CaseStatus.CLOSED }

                val filtered = if (filterStatus == null) updatedCases
                               else updatedCases.filter { it.status == filterStatus }

                _uiState.value = _uiState.value.copy(
                    cases = filtered,
                    nextNodeByCase = nextMap,
                    processingCount = processing,
                    dueSoonCount = dueSoon,
                    overdueCount = overdue,
                    closedCount = closed,
                    isLoading = false
                )
            }.collect {}
        }
    }
}
