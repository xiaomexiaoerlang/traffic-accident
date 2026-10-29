package com.casetracker.ui.edit

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.casetracker.data.SettingsManager
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
import com.casetracker.domain.model.ViolationType
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EditUiState(
    val isEditing: Boolean = false,
    val caseType: CaseType = CaseType.ACCIDENT,
    // 违法行为案件类型（行政案件 / 刑事案件，v1.66）
    val violationType: ViolationType = ViolationType.ADMINISTRATIVE,
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
    // 伤情情况（伤人事故，智能录入可自动识别）
    val injuryDetail: String = "",
    // 流程是否已归档（编辑时需保留，避免误清除）
    val flowArchived: Boolean = false,
    // 一般程序快速处理：选择后保存即自动走免鉴定流程（跳过鉴定与事故调查报告，受案登记+10 个工作日）
    val flowQuickProcess: Boolean = false,
    // 年度总览专项（v1.69，事故案件）：责任划分 / 扣车与返还 / 违法行为处理
    val responsibility: String = "",
    val vehicleSeized: Boolean = false,
    val vehicleReturned: Boolean = false,
    val violationHandled: Boolean = false,
    // v1.70：办案民警历史记忆（输入过即记住，可点选复用、可删除）
    val officerOptions: List<String> = emptyList(),
    val status: CaseStatus = CaseStatus.PENDING_ACCEPTANCE,
    val deadline: Long = 0L,
    val isSaving: Boolean = false,
    val isLoading: Boolean = true,
    val parseMessage: String = "",
    // v1.81：案件名称长按自定义（长按自动生成名称弹出修改框），保存时优先使用自定义名称
    val titleOverride: String = "",
)

sealed interface EditUiEvent {
    data class UpdateCaseType(val type: CaseType) : EditUiEvent
    data class UpdateViolationType(val type: ViolationType) : EditUiEvent
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
    data class UpdateInjuryDetail(val value: String) : EditUiEvent
    data class UpdateResponsibility(val value: String) : EditUiEvent
    data class UpdateVehicleSeized(val value: Boolean) : EditUiEvent
    data class UpdateVehicleReturned(val value: Boolean) : EditUiEvent
    data class UpdateViolationHandled(val value: Boolean) : EditUiEvent
    /** v1.70 办案民警记忆：点选历史民警 */
    data class SelectOfficer(val name: String) : EditUiEvent
    /** v1.70 删除一个历史记忆的民警 */
    data class ForgetOfficer(val name: String) : EditUiEvent
    data class UpdateParty(val index: Int, val value: String) : EditUiEvent
    data object AddParty : EditUiEvent
    data class RemoveParty(val index: Int) : EditUiEvent
    /** v1.81：案件名称长按自定义修改 */
    data class UpdateTitleOverride(val value: String) : EditUiEvent
    data class ParseDescription(val text: String) : EditUiEvent
    data object ClearParseMessage : EditUiEvent
    data object Save : EditUiEvent
    data object NavigateBack : EditUiEvent
}

@HiltViewModel
class CaseEditViewModel @Inject constructor(
    private val repository: CaseRepository,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditUiState())
    val uiState: StateFlow<EditUiState> = _uiState.asStateFlow()

    private val _shouldNavigateBack = MutableStateFlow(false)
    val shouldNavigateBack: StateFlow<Boolean> = _shouldNavigateBack.asStateFlow()

    private var editingCaseId: Long = 0L
    private var editingCaseNumber: String = ""

    init {
        // v1.70：办案民警历史记忆列表，随时保持最新（点选/删除即时生效）
        viewModelScope.launch {
            SettingsManager.officers.collect { list ->
                _uiState.value = _uiState.value.copy(officerOptions = list)
            }
        }
    }

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
                    violationType = it.violationType,
                    accidentDate = it.accidentDate,
                    accidentDateText = if (it.accidentDate > 0) FormatUtils.shortDate(it.accidentDate) else "",
                    accidentTime = it.accidentTime,
                    accidentType = it.accidentType,
                    // v1.63：简易程序已取消，存量简易案件编辑时自动转为一般程序
                    procedure = if (it.procedure == AccidentProcedure.SIMPLE) AccidentProcedure.GENERAL else it.procedure,
                    hitAndRun = it.hitAndRun,
                    appraisalExtended = it.appraisalExtended,
                    handlingOfficer = it.handlingOfficer,
                    parties = loadedParties,
                    location = it.location,
                    description = it.description,
                    injuryDetail = it.injuryDetail,
                    flowArchived = it.flowArchived,
                    status = it.status,
                    deadline = it.deadline,
                    flowQuickProcess = it.flowQuickProcess,
                    responsibility = it.responsibility,
                    vehicleSeized = it.vehicleSeized,
                    vehicleReturned = it.vehicleReturned,
                    violationHandled = it.violationHandled,
                    // v1.81：编辑时回填现有名称作为自定义候选（自动生成名称预览仍按规则重算）
                    titleOverride = it.title,
                    isLoading = false
                )
            }
        }
    }

    fun onEvent(event: EditUiEvent) {
        when (event) {
            is EditUiEvent.UpdateCaseType ->
                _uiState.value = _uiState.value.copy(caseType = event.type)
            is EditUiEvent.UpdateViolationType ->
                _uiState.value = _uiState.value.copy(violationType = event.type)
            is EditUiEvent.UpdateLocation ->
                _uiState.value = _uiState.value.copy(location = event.value)
            is EditUiEvent.UpdateDescription ->
                _uiState.value = _uiState.value.copy(description = event.value)
            is EditUiEvent.UpdateStatus ->
                // v1.80 事故案件状态跟随流程自动派生（处理中→已认定），不再手动选择中间状态
                if (_uiState.value.caseType != CaseType.ACCIDENT) {
                    _uiState.value = _uiState.value.copy(status = event.status)
                }
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
                // 选择「一般程序快速处理」→ 保存后自动走免鉴定流程；切回一般程序则关闭
                _uiState.value = _uiState.value.copy(
                    procedure = event.procedure,
                    flowQuickProcess = event.procedure == AccidentProcedure.QUICK
                )
            is EditUiEvent.UpdateHitAndRun ->
                _uiState.value = _uiState.value.copy(hitAndRun = event.value)
            is EditUiEvent.UpdateAppraisalExtended ->
                _uiState.value = _uiState.value.copy(appraisalExtended = event.value)
            is EditUiEvent.UpdateHandlingOfficer ->
                _uiState.value = _uiState.value.copy(handlingOfficer = event.value)
            is EditUiEvent.UpdateInjuryDetail ->
                _uiState.value = _uiState.value.copy(injuryDetail = event.value)
            is EditUiEvent.UpdateResponsibility ->
                _uiState.value = _uiState.value.copy(responsibility = event.value)
            is EditUiEvent.UpdateVehicleSeized -> {
                // 关闭「扣车」时自动清掉「已返还」状态，避免出现未扣车但已返还的矛盾记录
                val returned = if (event.value) _uiState.value.vehicleReturned else false
                _uiState.value = _uiState.value.copy(vehicleSeized = event.value, vehicleReturned = returned)
            }
            is EditUiEvent.UpdateVehicleReturned ->
                _uiState.value = _uiState.value.copy(vehicleReturned = event.value)
            is EditUiEvent.UpdateViolationHandled ->
                _uiState.value = _uiState.value.copy(violationHandled = event.value)
            is EditUiEvent.SelectOfficer ->
                _uiState.value = _uiState.value.copy(handlingOfficer = event.name)
            is EditUiEvent.ForgetOfficer ->
                SettingsManager.forgetOfficer(appContext, event.name)
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
            is EditUiEvent.UpdateTitleOverride ->
                _uiState.value = _uiState.value.copy(titleOverride = event.value)
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
            if (result.isInjury) append("✓ 识别为伤人事故\n")
            if (result.injuryDetail.isNotBlank()) append("✓ 识别伤情：").append(result.injuryDetail).append('\n')
        }.trimEnd()
        _uiState.value = _uiState.value.copy(
            accidentDate = if (result.accidentDate > 0) result.accidentDate else _uiState.value.accidentDate,
            accidentDateText = if (result.accidentDate > 0) FormatUtils.shortDate(result.accidentDate) else _uiState.value.accidentDateText,
            accidentTime = if (result.accidentTime.isNotBlank()) result.accidentTime else _uiState.value.accidentTime,
            location = if (result.location.isNotBlank()) result.location else _uiState.value.location,
            parties = mergedParties,
            handlingOfficer = if (result.handlingOfficer.isNotBlank()) result.handlingOfficer else _uiState.value.handlingOfficer,
            hitAndRun = _uiState.value.hitAndRun || result.hitAndRun,
            // 有伤情描述 → 自动识别为伤人事故；伤情短语可自动填入
            accidentType = if (result.isInjury) AccidentType.INJURY else _uiState.value.accidentType,
            injuryDetail = if (result.injuryDetail.isNotBlank()) result.injuryDetail else _uiState.value.injuryDetail,
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
        // v1.81：案件名称长按自定义优先；未自定义时自动生成（事故/违法均支持，违法含醉驾/无证驾驶特殊格式）
        val autoTitle = FormatUtils.buildCaseTitle(state.accidentDate, firstParty, state.caseType, state.description)
        val title = if (state.titleOverride.isNotBlank()) state.titleOverride else autoTitle
        val caseNumber = if (state.isEditing) editingCaseNumber
                         else FormatUtils.buildCaseNumber(state.accidentDate, now)
        // v1.80 事故不再有待受案：创建/保存事故时状态直接为「处理中」，避免「待受案」出现在界面与统计
        val effectiveStatus = if (state.caseType == CaseType.ACCIDENT &&
            state.status == CaseStatus.PENDING_ACCEPTANCE) CaseStatus.IN_PROGRESS else state.status
        val deadline = if (state.deadline > 0) state.deadline
                       else LegalDeadlineCalculator.calculateDeadline(state.caseType, effectiveStatus, now)

        val case = Case(
            id = if (state.isEditing) editingCaseId else 0,
            caseNumber = caseNumber,
            caseType = state.caseType,
            title = title,
            status = effectiveStatus,
            progress = effectiveStatus.minProgress,
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
            injuryDetail = state.injuryDetail,
            flowArchived = state.flowArchived,
            flowQuickProcess = state.flowQuickProcess,
            violationType = state.violationType,
            responsibility = state.responsibility,
            vehicleSeized = state.vehicleSeized,
            vehicleReturned = state.vehicleReturned,
            violationHandled = state.violationHandled,
            selfParty = if (cleanParties.isNotEmpty()) cleanParties.first() else "",
            otherParty = ""
        )

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true)
            // v1.70：保存时记忆办案民警姓名，下次编辑可点选
            if (state.handlingOfficer.isNotBlank()) {
                SettingsManager.rememberOfficer(appContext, state.handlingOfficer)
            }
            if (state.isEditing) {
                repository.updateCase(case)
            } else {
                val initialEvent = CaseEvent(
                    caseId = 0,
                    eventType = "CREATED",
                    title = "案件创建",
                    // v1.80 事故不再有待受案：创建事件描述事故为「处理中」
                    description = "案件已创建，状态：" + if (state.caseType == CaseType.ACCIDENT) "处理中" else "待受案",
                    occurredAt = now,
                    isMilestone = true
                )
                repository.createCase(case, listOf(initialEvent))
            }
            _shouldNavigateBack.value = true
        }
    }
}
