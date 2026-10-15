package com.casetracker.ui.edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.casetracker.data.repository.CaseRepository
import com.casetracker.domain.model.AccidentParser
import com.casetracker.domain.model.AccidentProcedure
import com.casetracker.domain.model.AccidentType
import com.casetracker.domain.model.Case
import com.casetracker.domain.model.CaseEvent
import com.casetracker.domain.model.CaseStatus
import com.casetracker.domain.model.CaseType
import com.casetracker.domain.model.FormatUtils
import com.casetracker.domain.model.LegalDeadlineCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EditUiState(
    val isEditing: Boolean = false,
    val caseType: CaseType = CaseType.ACCIDENT,
    // 事故专项
    val accidentDate: Long = 0L,
    val accidentDateText: String = "",
    val accidentTime: String = "",
    val accidentType: AccidentType = AccidentType.PROPERTY,
    val procedure: AccidentProcedure = AccidentProcedure.GENERAL,
    val hitAndRun: Boolean = false,
    val appraisalExtended: Boolean = false,
    val handlingOfficer: String = "",
    // 当事人（动态列表）
    val parties: List<String> = emptyList(),
    val location: String = "",
    val description: String = "",
    val status: CaseStatus = CaseStatus.PENDING_ACCEPTANCE,
    val deadline: Long = 0L,
    val isSaving: Boolean = false,
    val isLoading: Boolean = true,
    val parseMessage: String = "",
)

sealed interface EditUiEvent {
    data class UpdateCaseType(val type: CaseType) : EditUiEvent
    data class UpdateLocation(val value: String) : EditUiEvent
    data class UpdateDescription(val value: String) : EditUiEvent
    data class UpdateStatus(val status: CaseStatus) : EditUiEvent
    data class UpdateAccidentDateText(val value: String) : EditUiEvent
    data class UpdateAccidentTime(val value: String) : EditUiEvent
    data class UpdateAccidentType(val type: AccidentType) : EditUiEvent
    data class UpdateProcedure(val procedure: AccidentProcedure) : EditUiEvent
    data class UpdateHitAndRun(val value: Boolean) : EditUiEvent
    data class UpdateAppraisalExtended(val value: Boolean) : EditUiEvent
    data class UpdateHandlingOfficer(val value: String) : EditUiEvent
    data class UpdateParty(val index: Int, val value: String) : EditUiEvent
    data object AddParty : EditUiEvent
    data class RemoveParty(val index: Int) : EditUiEvent
    data class ParseDescription(val text: String) : EditUiEvent
    data object ClearParseMessage : EditUiEvent
    data object Save : EditUiEvent
    data object NavigateBack : EditUiEvent
}

@HiltViewModel
class CaseEditViewModel @Inject constructor(
    private val repository: CaseRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditUiState())
    val uiState: StateFlow<EditUiState> = _uiState.asStateFlow()

    private val _shouldNavigateBack = MutableStateFlow(false)
    val shouldNavigateBack: StateFlow<Boolean> = _shouldNavigateBack.asStateFlow()

    private var editingCaseId: Long = 0L
    private var editingCaseNumber: String = ""

    fun loadCase(caseId: Long?) {
        if (caseId == null) {
            _uiState.value = _uiState.value.copy(isLoading = false)
            return
        }
        editingCaseId = caseId
        viewModelScope.launch {
            val (case, _) = repository.getCaseDetail(caseId).first()
            case?.let {
                editingCaseNumber = it.caseNumber
                val loadedParties = buildList {
                    if (it.selfParty.isNotBlank()) add(it.selfParty)
                    if (it.otherParty.isNotBlank()) add(it.otherParty)
                    addAll(it.parties)
                }.distinct()
                _uiState.value = EditUiState(
                    isEditing = true,
                    caseType = it.caseType,
                    accidentDate = it.accidentDate,
                    accidentDateText = if (it.accidentDate > 0) FormatUtils.shortDate(it.accidentDate) else "",
                    accidentTime = it.accidentTime,
                    accidentType = it.accidentType,
                    procedure = it.procedure,
                    hitAndRun = it.hitAndRun,
                    appraisalExtended = it.appraisalExtended,
                    handlingOfficer = it.handlingOfficer,
                    parties = loadedParties,
                    location = it.location,
                    description = it.description,
                    status = it.status,
                    deadline = it.deadline,
                    isLoading = false
                )
            }
        }
    }

    fun onEvent(event: EditUiEvent) {
        when (event) {
            is EditUiEvent.UpdateCaseType ->
                _uiState.value = _uiState.value.copy(caseType = event.type)
            is EditUiEvent.UpdateLocation ->
                _uiState.value = _uiState.value.copy(location = event.value)
            is EditUiEvent.UpdateDescription ->
                _uiState.value = _uiState.value.copy(description = event.value)
            is EditUiEvent.UpdateStatus ->
                _uiState.value = _uiState.value.copy(status = event.status)
            is EditUiEvent.UpdateAccidentDateText -> {
                val parsed = AccidentParser.parseDate(event.value)
                _uiState.value = _uiState.value.copy(
                    accidentDateText = event.value,
                    accidentDate = parsed
                )
            }
            is EditUiEvent.UpdateAccidentTime ->
                _uiState.value = _uiState.value.copy(accidentTime = event.value)
            is EditUiEvent.UpdateAccidentType ->
                _uiState.value = _uiState.value.copy(accidentType = event.type)
            is EditUiEvent.UpdateProcedure ->
                _uiState.value = _uiState.value.copy(procedure = event.procedure)
            is EditUiEvent.UpdateHitAndRun ->
                _uiState.value = _uiState.value.copy(hitAndRun = event.value)
            is EditUiEvent.UpdateAppraisalExtended ->
                _uiState.value = _uiState.value.copy(appraisalExtended = event.value)
            is EditUiEvent.UpdateHandlingOfficer ->
                _uiState.value = _uiState.value.copy(handlingOfficer = event.value)
            is EditUiEvent.UpdateParty -> {
                val list = _uiState.value.parties.toMutableList()
                if (event.index in list.indices) list[event.index] = event.value
                _uiState.value = _uiState.value.copy(parties = list)
            }
            is EditUiEvent.AddParty ->
                _uiState.value = _uiState.value.copy(parties = _uiState.value.parties + "")
            is EditUiEvent.RemoveParty -> {
                val list = _uiState.value.parties.toMutableList()
                if (event.index in list.indices) list.removeAt(event.index)
                _uiState.value = _uiState.value.copy(parties = list)
            }
            is EditUiEvent.ParseDescription -> parseDescription(event.text)
            is EditUiEvent.ClearParseMessage ->
                _uiState.value = _uiState.value.copy(parseMessage = "")
            is EditUiEvent.Save -> saveCase()
            is EditUiEvent.NavigateBack -> _shouldNavigateBack.value = true
        }
    }

    fun onNavigationConsumed() { _shouldNavigateBack.value = false }

    /** 智能解析：粘贴事故描述，自动填充字段 */
    private fun parseDescription(text: String) {
        if (text.isBlank()) return
        val result = AccidentParser.parse(text)
        val mergedParties = (_uiState.value.parties + result.parties)
            .filter { it.isNotBlank() }
            .distinct()
        val parts = buildString {
            if (result.accidentDate > 0) append("✓ 识别日期：").append(FormatUtils.chineseDate(result.accidentDate)).append('\n')
            if (result.accidentTime.isNotBlank()) append("✓ 识别时间：").append(result.accidentTime).append('\n')
            if (result.location.isNotBlank()) append("✓ 识别地点：").append(result.location).append('\n')
            if (result.parties.isNotEmpty()) append("✓ 识别当事人：").append(result.parties.joinToString("、")).append('\n')
            if (result.handlingOfficer.isNotBlank()) append("✓ 识别民警：").append(result.handlingOfficer).append('\n')
            if (result.hitAndRun) append("✓ 识别逃逸\n")
        }.trimEnd()
        _uiState.value = _uiState.value.copy(
            accidentDate = if (result.accidentDate > 0) result.accidentDate else _uiState.value.accidentDate,
            accidentDateText = if (result.accidentDate > 0) FormatUtils.shortDate(result.accidentDate) else _uiState.value.accidentDateText,
            accidentTime = if (result.accidentTime.isNotBlank()) result.accidentTime else _uiState.value.accidentTime,
            location = if (result.location.isNotBlank()) result.location else _uiState.value.location,
            parties = mergedParties,
            handlingOfficer = if (result.handlingOfficer.isNotBlank()) result.handlingOfficer else _uiState.value.handlingOfficer,
            hitAndRun = _uiState.value.hitAndRun || result.hitAndRun,
            description = text,
            parseMessage = parts.ifBlank { "未能从文本中识别出结构化信息，请手动填写" }
        )
    }

    private fun saveCase() {
        val state = _uiState.value

        // 校验：事故案件必须有事故日期
        if (state.caseType == CaseType.ACCIDENT && state.accidentDate <= 0L) {
            _uiState.value = _uiState.value.copy(
                parseMessage = "请先填写事故日期（格式如 2026-09-10）"
            )
            return
        }

        val now = System.currentTimeMillis()
        val cleanParties = state.parties.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        val firstParty = cleanParties.firstOrNull() ?: ""
        val title = FormatUtils.buildCaseTitle(state.accidentDate, firstParty, state.caseType)
        val caseNumber = if (state.isEditing) editingCaseNumber
                         else FormatUtils.buildCaseNumber(state.accidentDate, now)
        val deadline = if (state.deadline > 0) state.deadline
                       else LegalDeadlineCalculator.calculateDeadline(state.caseType, state.status, now)

        val case = Case(
            id = if (state.isEditing) editingCaseId else 0,
            caseNumber = caseNumber,
            caseType = state.caseType,
            title = title,
            status = state.status,
            progress = state.status.minProgress,
            parties = cleanParties,
            description = state.description,
            location = state.location,
            deadline = deadline,
            createdAt = 0L,
            updatedAt = now,
            accidentDate = state.accidentDate,
            accidentTime = state.accidentTime,
            accidentType = state.accidentType,
            procedure = state.procedure,
            handlerUnit = "",
            hitAndRun = state.hitAndRun,
            appraisalExtended = state.appraisalExtended,
            handlingOfficer = state.handlingOfficer,
            selfParty = if (cleanParties.isNotEmpty()) cleanParties.first() else "",
            otherParty = ""
        )

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true)
            if (state.isEditing) {
                repository.updateCase(case)
            } else {
                val initialEvent = CaseEvent(
                    caseId = 0,
                    eventType = "CREATED",
                    title = "案件创建",
                    description = "案件已创建，状态：${state.status.displayName}",
                    occurredAt = now,
                    isMilestone = true
                )
                repository.createCase(case, listOf(initialEvent))
            }
            _shouldNavigateBack.value = true
        }
    }
}
