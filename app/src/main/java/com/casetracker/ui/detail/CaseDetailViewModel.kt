package com.casetracker.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.casetracker.data.local.dao.ChecklistDao
import com.casetracker.data.local.entity.ChecklistItemEntity
import com.casetracker.data.repository.CaseRepository
import com.casetracker.domain.model.AccidentFlowTemplate
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
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DetailUiState(
    val case: Case? = null,
    val events: List<CaseEvent> = emptyList(),
    val nodes: List<FlowNode> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,      // v1.67 下拉刷新
    val showAddEventDialog: Boolean = false,
    val recordNodeCode: String? = null,   // 正在录入日期的节点
    val showBranchDialog: Boolean = false, // 「鉴定委托」是否需要委托鉴定提示
    val toastMessage: String? = null,     // 校验提示等
    // v1.70 责任划分：事故认定书出具后按当事人逐人录入（party → 责任）
    val partyLiabilities: Map<String, String> = emptyMap(),
    // v1.70 认定书刚出具时高亮提示责任划分卡片
    val showLiabilityHint: Boolean = false,
    // v1.82 办案清单：nodeCode → 已勾选条目下标集合
    val checklistDone: Map<String, Set<Int>> = emptyMap(),
)

/** 责任划分可选值（v1.70） */
val LIABILITY_OPTIONS = listOf("全责", "主责", "次责", "同等", "无责", "事故证明")

sealed interface DetailUiEvent {
    data object Load : DetailUiEvent
    data object Refresh : DetailUiEvent
    data class AddEvent(val title: String, val description: String, val isMilestone: Boolean) : DetailUiEvent
    data object ShowAddEventDialog : DetailUiEvent
    data object DismissAddEventDialog : DetailUiEvent
    data class ShowRecordDialog(val nodeCode: String) : DetailUiEvent
    data class RecordNodeDate(val nodeCode: String, val dateText: String) : DetailUiEvent
    data object DismissRecordDialog : DetailUiEvent
    /** 鉴定委托：弹出「是否需要委托鉴定」选择 */
    data object ShowBranchDialog : DetailUiEvent
    /** 不需要委托鉴定：跳过 鉴定委托/鉴定报告/事故调查报告，事故发生 + 10 个工作日出认定书（v1.74） */
    data object ChooseSkipInvestigation : DetailUiEvent
    /** 受案登记时提交《一般程序快速处理申请书》（申请日 = 今天） */
    data object ChooseQuickProcess : DetailUiEvent
    /** v1.67 改回主线：恢复「需要委托鉴定」 */
    data object RestoreMainline : DetailUiEvent
    data object DismissBranchDialog : DetailUiEvent
    data object ConsumeToast : DetailUiEvent
    data object DeleteCase : DetailUiEvent
    data object CloseCase : DetailUiEvent
    data object NavigateBack : DetailUiEvent
    data object NavigateToEdit : DetailUiEvent
    /** v1.70 责任划分：设置某当事人责任 */
    data class SetPartyLiability(val party: String, val liability: String) : DetailUiEvent
    /** v1.70 责任划分卡片内的扣车 / 返还 / 违法处理（认定书出具后可修改） */
    data class SetVehicleSeized(val value: Boolean) : DetailUiEvent
    data class SetVehicleReturned(val value: Boolean) : DetailUiEvent
    data class SetViolationHandled(val value: Boolean) : DetailUiEvent
    /** v1.70 关闭责任划分高亮提示 */
    data object DismissLiabilityHint : DetailUiEvent
    /** v1.81：案件名称长按修改 */
    data class UpdateTitle(val value: String) : DetailUiEvent
    /** v1.82 办案清单：勾选 / 取消勾选某清单条目 */
    data class ToggleChecklistItem(
        val nodeCode: String,
        val itemIndex: Int,
        val itemText: String,
        val done: Boolean
    ) : DetailUiEvent
}

@HiltViewModel
class CaseDetailViewModel @Inject constructor(
    private val repository: CaseRepository,
    private val checklistDao: ChecklistDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(DetailUiState())
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    private val _shouldNavigateBack = MutableStateFlow(false)
    val shouldNavigateBack: StateFlow<Boolean> = _shouldNavigateBack.asStateFlow()

    private val _shouldNavigateToEdit = MutableStateFlow(false)
    val shouldNavigateToEdit: StateFlow<Boolean> = _shouldNavigateToEdit.asStateFlow()

    private var currentCaseId: Long = 0L

    fun loadCase(caseId: Long, refreshing: Boolean = false) {
        currentCaseId = caseId
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = !refreshing,
                isRefreshing = refreshing
            )
            repository.getCaseDetail(caseId).collect { (case, events) ->
                val now = System.currentTimeMillis()
                val updatedCase = case?.copy(progress = case.calculateProgress(now))
                val nodes = when {
                    case == null -> emptyList()
                    case.caseType == CaseType.ACCIDENT ->
                        AccidentFlowTemplate.buildNodes(case, events, now)
                    case.caseType == CaseType.VIOLATION ->
                        ViolationFlowTemplate.buildNodes(case, events, now)
                    else -> emptyList()
                }
                // v1.70：解析当事人责任划分 JSON
                val liabilities = parseLiabilities(case?.responsibilities.orEmpty())
                _uiState.value = _uiState.value.copy(
                    case = updatedCase,
                    events = events,
                    nodes = nodes,
                    partyLiabilities = liabilities,
                    isLoading = false,
                    isRefreshing = false
                )
            }
        }
        // v1.82：办案清单勾选状态（每案独立本地保存）
        viewModelScope.launch {
            checklistDao.observeByCase(caseId).collect { items ->
                val done = items
                    .filter { it.done }
                    .groupBy({ it.nodeCode }, { it.itemIndex })
                    .mapValues { it.value.toSet() }
                _uiState.value = _uiState.value.copy(checklistDone = done)
            }
        }
    }

    fun onEvent(event: DetailUiEvent) {
        when (event) {
            is DetailUiEvent.Load -> loadCase(currentCaseId)
            is DetailUiEvent.Refresh -> loadCase(currentCaseId, refreshing = true)
            is DetailUiEvent.AddEvent -> addEvent(event.title, event.description, event.isMilestone)
            is DetailUiEvent.ShowAddEventDialog ->
                _uiState.value = _uiState.value.copy(showAddEventDialog = true)
            is DetailUiEvent.DismissAddEventDialog ->
                _uiState.value = _uiState.value.copy(showAddEventDialog = false)
            is DetailUiEvent.ShowRecordDialog -> showRecordDialog(event.nodeCode)
            is DetailUiEvent.RecordNodeDate -> recordNodeDate(event.nodeCode, event.dateText)
            is DetailUiEvent.DismissRecordDialog ->
                _uiState.value = _uiState.value.copy(recordNodeCode = null)
            is DetailUiEvent.ShowBranchDialog ->
                _uiState.value = _uiState.value.copy(showBranchDialog = true)
            is DetailUiEvent.ChooseSkipInvestigation -> skipInvestigation()
            is DetailUiEvent.ChooseQuickProcess -> quickProcess()
            is DetailUiEvent.RestoreMainline -> restoreMainline()
            is DetailUiEvent.DismissBranchDialog ->
                _uiState.value = _uiState.value.copy(showBranchDialog = false)
            is DetailUiEvent.ConsumeToast ->
                _uiState.value = _uiState.value.copy(toastMessage = null)
            is DetailUiEvent.DeleteCase -> deleteCase()
            is DetailUiEvent.CloseCase -> closeCase()
            is DetailUiEvent.SetPartyLiability -> setPartyLiability(event.party, event.liability)
            is DetailUiEvent.SetVehicleSeized ->
                updateLiabilityExtras(vehicleSeized = event.value)
            is DetailUiEvent.SetVehicleReturned ->
                updateLiabilityExtras(vehicleReturned = event.value)
            is DetailUiEvent.SetViolationHandled ->
                updateLiabilityExtras(violationHandled = event.value)
            is DetailUiEvent.DismissLiabilityHint ->
                _uiState.value = _uiState.value.copy(showLiabilityHint = false)
            is DetailUiEvent.UpdateTitle -> updateTitle(event.value)
            is DetailUiEvent.ToggleChecklistItem ->
                toggleChecklistItem(event.nodeCode, event.itemIndex, event.itemText, event.done)
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
            repository.recordNodeDate(currentCaseId, node.code, date)
            // v1.70：事故认定书（N5）出具后自动提示进入责任划分
            val isCertificate = node.code == com.casetracker.domain.model.AccidentFlowTemplate.CERTIFICATE_CODE
            val isAccident = _uiState.value.case?.caseType == CaseType.ACCIDENT
            _uiState.value = _uiState.value.copy(
                recordNodeCode = null,
                showLiabilityHint = isAccident && isCertificate
            )
        }
    }

    /** v1.70 解析当事人责任划分 JSON：[{"party":"王静","liability":"主责"}] */
    private fun parseLiabilities(json: String): Map<String, String> {
        if (json.isBlank()) return emptyMap()
        return runCatching {
            val arr = org.json.JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                val obj = arr.getJSONObject(i)
                val party = obj.optString("party", "").trim()
                val liab = obj.optString("liability", "").trim()
                if (party.isNotBlank() && liab.isNotBlank()) party to liab else null
            }.toMap()
        }.getOrDefault(emptyMap())
    }

    /** v1.70 设置某当事人责任并保存 */
    private fun setPartyLiability(party: String, liability: String) {
        val current = _uiState.value
        val case = current.case ?: return
        val updated = current.partyLiabilities.toMutableMap().apply {
            if (liability.isBlank()) remove(party) else put(party, liability)
        }
        val json = org.json.JSONArray().apply {
            updated.forEach { (p, l) ->
                put(org.json.JSONObject().put("party", p).put("liability", l))
            }
        }.toString()
        val newCase = case.copy(
            responsibilities = json,
            responsibility = updated.values.joinToString("、")
        )
        saveLiabilityCase(newCase)
        _uiState.value = _uiState.value.copy(
            partyLiabilities = updated,
            case = newCase
        )
    }

    /** v1.70 责任卡片内的扣车 / 返还 / 违法处理 */
    private fun updateLiabilityExtras(
        vehicleSeized: Boolean? = null,
        vehicleReturned: Boolean? = null,
        violationHandled: Boolean? = null
    ) {
        val case = _uiState.value.case ?: return
        val newCase = case.copy(
            vehicleSeized = vehicleSeized ?: case.vehicleSeized,
            vehicleReturned = (vehicleReturned ?: case.vehicleReturned) &&
                (vehicleSeized ?: case.vehicleSeized),
            violationHandled = violationHandled ?: case.violationHandled
        )
        saveLiabilityCase(newCase)
        _uiState.value = _uiState.value.copy(case = newCase)
    }

    private fun saveLiabilityCase(newCase: Case) {
        viewModelScope.launch {
            repository.updateCase(newCase)
        }
    }

    /** v1.81：案件名称长按修改（事故/违法均可） */
    private fun updateTitle(value: String) {
        val trimmed = value.trim()
        if (trimmed.isBlank()) return
        val current = _uiState.value.case ?: return
        val newCase = current.copy(title = trimmed)
        _uiState.value = _uiState.value.copy(case = newCase)
        viewModelScope.launch {
            repository.updateCase(newCase)
        }
    }

    /** v1.82 办案清单：勾选 / 取消勾选某条目（每案独立本地保存）；v1.86 加异常防护 */
    private fun toggleChecklistItem(
        nodeCode: String,
        itemIndex: Int,
        itemText: String,
        done: Boolean
    ) {
        viewModelScope.launch {
            runCatching {
                checklistDao.upsert(
                    ChecklistItemEntity(
                        caseId = currentCaseId,
                        nodeCode = nodeCode,
                        itemIndex = itemIndex,
                        itemText = itemText,
                        done = done
                    )
                )
            }.onFailure {
                _uiState.value = _uiState.value.copy(toastMessage = "勾选保存失败：${it.message ?: "数据库错误"}")
            }
        }
    }

    /** 免鉴定快捷出证 */
    private fun skipInvestigation() {
        viewModelScope.launch {
            repository.setSkipInvestigation(currentCaseId)
            _uiState.value = _uiState.value.copy(
                showBranchDialog = false,
                toastMessage = "已选择免鉴定：事故发生之日起 10 个工作日内出具事故认定书"
            )
        }
    }

    /** 一般程序快速处理：提交《快速处理申请书》（申请日 = 今天） */
    private fun quickProcess() {
        viewModelScope.launch {
            repository.setQuickProcess(currentCaseId)
            _uiState.value = _uiState.value.copy(
                recordNodeCode = null,
                toastMessage = "已提交快速处理申请：事故发生之日起 10 个工作日内出具事故认定书"
            )
        }
    }

    /** v1.67 修改鉴定选择：改回主线（需要委托鉴定），后续节点预计日期同步重算 */
    private fun restoreMainline() {
        viewModelScope.launch {
            repository.setInvestigationMode(currentCaseId, skip = false, quick = false)
            _uiState.value = _uiState.value.copy(
                showBranchDialog = false,
                toastMessage = "已改回主线：需要委托鉴定，按 鉴定委托 → 鉴定报告 → 调查报告 → 认定书 流程办理"
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

    /** 标记已认定（仅全部节点完成后可用；节点全部完成时流程页录入也会自动置为已认定） */
    private fun closeCase() {
        val nodes = _uiState.value.nodes
        if (nodes.isNotEmpty() && nodes.any { it.state != NodeState.DONE }) {
            _uiState.value = _uiState.value.copy(toastMessage = "还有节点未完成，无法标记已认定")
            return
        }
        viewModelScope.launch {
            repository.updateStatus(currentCaseId, CaseStatus.CLOSED, 100)
            repository.addEvent(
                currentCaseId,
                CaseEvent(
                    caseId = currentCaseId,
                    eventType = "STATUS_CHANGE",
                    title = "案件已认定",
                    description = "全部流程节点已完成，标记已认定",
                    occurredAt = System.currentTimeMillis(),
                    isMilestone = true
                )
            )
        }
    }
}
