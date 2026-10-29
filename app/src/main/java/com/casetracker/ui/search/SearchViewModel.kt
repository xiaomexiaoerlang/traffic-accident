package com.casetracker.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.casetracker.data.repository.CaseRepository
import com.casetracker.domain.model.Case
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchUiState(
    val query: String = "",
    val results: List<Case> = emptyList(),
    val isSearching: Boolean = false,
    /** 已完成受案登记的案件 id 集合（v1.74 事故已去除受案登记，仅违法 VR 用于卡片状态） */
    val registeredCaseIds: Set<Long> = emptySet(),
    /** 已完成违法受案登记（VR）的案件 id 集合 */
    val violationRegisteredCaseIds: Set<Long> = emptySet()
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: CaseRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val _query = MutableStateFlow("")

    init {
        viewModelScope.launch {
            _query
                .debounce(300)
                .flatMapLatest { q ->
                    if (q.isBlank()) {
                        kotlinx.coroutines.flow.flowOf(emptyList<Case>())
                    } else {
                        repository.searchCases(q.trim())
                    }
                }
                .onStart { _uiState.value = _uiState.value.copy(isSearching = true) }
                .collect { results ->
                    val registered = repository.getRegisteredCaseIds().first()
                    val vioRegistered = repository.getViolationRegisteredCaseIds().first()
                    _uiState.value = _uiState.value.copy(
                        results = results,
                        isSearching = false,
                        registeredCaseIds = registered,
                        violationRegisteredCaseIds = vioRegistered
                    )
                }
        }
    }

    fun onQueryChange(value: String) {
        _uiState.value = _uiState.value.copy(query = value)
        _query.value = value
    }
}
