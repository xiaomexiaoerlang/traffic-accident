package com.casetracker.ui.overview

import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.casetracker.data.repository.CaseRepository
import com.casetracker.domain.model.AccidentFlowTemplate
import com.casetracker.domain.model.CalendarUtil
import com.casetracker.domain.model.Case
import com.casetracker.domain.model.CaseStatus
import com.casetracker.domain.model.CaseType
import com.casetracker.domain.model.FormatUtils
import com.casetracker.domain.model.NodeState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/** 总览卡片状态基调：绿=已认定/已结案、蓝=处理中/待处罚、橙=待立案、红=超期 */
enum class OverviewTone { DONE, PENDING, REGISTERED, OVERDUE }

/** 总览底栏字段（label + 显示值 + 是否已填写） */
data class OverviewField(val label: String, val value: String, val filled: Boolean = true)

/** 总览卡片行数据 */
data class OverviewRow(
    val case: Case,
    val title: String,
    val dateText: String,          // 事故时间 / 违法时间
    val partiesText: String,
    val locationText: String,
    val descText: String,
    val statusLabel: String,
    val tone: OverviewTone,
    val fields: List<OverviewField>, // 底栏字段（事故 4 项 / 违法 2 项）
    val overdue: Boolean
)

data class OverviewUiState(
    val year: Int = CalendarUtil.toLocalDate(System.currentTimeMillis()).year,
    val minYear: Int = CalendarUtil.toLocalDate(System.currentTimeMillis()).year,
    val maxYear: Int = CalendarUtil.toLocalDate(System.currentTimeMillis()).year,
    val category: CaseType = CaseType.ACCIDENT,
    val query: String = "",
    val rows: List<OverviewRow> = emptyList(),
    val totalCount: Int = 0,
    val doneCount: Int = 0,
    val pendingCount: Int = 0,
    val overdueCount: Int = 0,
    val isLoading: Boolean = true,
    val message: String? = null,
)

sealed interface OverviewUiEvent {
    data class SelectYear(val year: Int) : OverviewUiEvent
    data class ShiftYear(val delta: Int) : OverviewUiEvent
    data class SelectCategory(val category: CaseType) : OverviewUiEvent
    data class UpdateQuery(val value: String) : OverviewUiEvent
    data object Reload : OverviewUiEvent
    data object ExportCsv : OverviewUiEvent
    /** v1.70 从 CSV 文件导入案件（总览右上角「上传」） */
    data class ImportCsv(val uri: android.net.Uri) : OverviewUiEvent
    data object ConsumeMessage : OverviewUiEvent
}

@HiltViewModel
class OverviewViewModel @Inject constructor(
    private val repository: CaseRepository,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(OverviewUiState())
    val uiState: StateFlow<OverviewUiState> = _uiState.asStateFlow()

    /** 分类 / 年份 / 搜索 全部作为数据流参与 combine：切换后列表与统计自动重算，不再需要手动刷新 */
    private val categoryFlow = MutableStateFlow(CaseType.ACCIDENT)
    private val yearFlow = MutableStateFlow(CalendarUtil.toLocalDate(System.currentTimeMillis()).year)
    private val queryFlow = MutableStateFlow("")

    init {
        // 数据基础流：案件 + 节点事件 + 违法已受案集合（combine 类型化重载最多 5 个，拆两步）
        val baseFlow = combine(
            repository.getAllCases(),
            repository.getAllNodeEvents(),
            repository.getViolationRegisteredCaseIds()
        ) { cases, nodeEvents, vioRegisteredIds ->
            Triple(cases, nodeEvents, vioRegisteredIds)
        }
        viewModelScope.launch {
            combine(
                baseFlow,
                categoryFlow,
                yearFlow,
                queryFlow
            ) { base, category, year, query ->
                val (cases, nodeEvents, vioRegisteredIds) = base
                val now = System.currentTimeMillis()
                val eventsByCase = nodeEvents.groupBy { it.caseId }

                // 每案件节点信息：事故取 N5（认定书出具）判断超期；违法取 V3/V4（处罚告知/决定）日期
                val overdueById = mutableMapOf<Long, Boolean>()
                val v3DateById = mutableMapOf<Long, Long>()
                val v4DateById = mutableMapOf<Long, Long>()
                cases.forEach { c ->
                    val events = eventsByCase[c.id] ?: emptyList()
                    overdueById[c.id] = when (c.caseType) {
                        CaseType.ACCIDENT -> {
                            if (c.status == CaseStatus.CLOSED) false
                            else {
                                val nodes = AccidentFlowTemplate.buildNodes(c, events, now)
                                val n5 = nodes.firstOrNull { it.code == AccidentFlowTemplate.CERTIFICATE_CODE }
                                n5 != null && n5.state == NodeState.OVERDUE
                            }
                        }
                        CaseType.VIOLATION -> {
                            if (c.status == CaseStatus.CLOSED) false else c.isOverdue(now)
                        }
                    }
                    v3DateById[c.id] = events.firstOrNull {
                        it.nodeCode == "V3" && it.occurredAt > 0
                    }?.occurredAt ?: 0L
                    v4DateById[c.id] = events.firstOrNull {
                        it.nodeCode == "V4" && it.occurredAt > 0
                    }?.occurredAt ?: 0L
                }

                // 年份范围：全部案件事故/违法时间的起止年（无案件时=当前年）
                val years = cases.mapNotNull {
                    val ref = if (it.accidentDate > 0) it.accidentDate else it.createdAt
                    if (ref <= 0) null else CalendarUtil.toLocalDate(ref).year
                }
                val minYear = years.minOrNull() ?: CalendarUtil.toLocalDate(now).year
                val maxYear = years.maxOrNull() ?: CalendarUtil.toLocalDate(now).year

                // 按年度 + 分类过滤
                val yearStart = CalendarUtil.toEpochMillis(LocalDate.of(year, 1, 1))
                val yearEnd = CalendarUtil.toEpochMillis(LocalDate.of(year + 1, 1, 1))
                val yearCases = cases.filter { c ->
                    val ref = if (c.accidentDate > 0) c.accidentDate else c.createdAt
                    ref in yearStart until yearEnd
                }
                val categoryCases = yearCases.filter { it.caseType == category }

                // 搜索过滤
                val q = query.trim()
                val filtered = if (q.isEmpty()) categoryCases else categoryCases.filter { c ->
                    listOf(
                        c.caseNumber, c.title, FormatUtils.unifiedTitle(c), c.location, c.description,
                        c.handlingOfficer, c.responsibility, c.selfParty, c.otherParty
                    ).any { it.contains(q, ignoreCase = true) } ||
                        c.parties.any { it.contains(q, ignoreCase = true) }
                }

                val rows = filtered.map { c ->
                    toRow(c, overdueById, v3DateById, v4DateById, vioRegisteredIds)
                }.sortedBy { it.case.accidentDate }

                // 统计（按该年度该分类全量，不随搜索过滤）
                val total = categoryCases.size
                val done = categoryCases.count { it.status == CaseStatus.CLOSED }
                val pending = categoryCases.count {
                    it.status != CaseStatus.CLOSED &&
                        when (category) {
                            CaseType.ACCIDENT -> true
                            CaseType.VIOLATION -> it.id in vioRegisteredIds
                        }
                }
                val overdue = categoryCases.count { overdueById[it.id] == true }

                _uiState.value = OverviewUiState(
                    year = year.coerceIn(minYear, maxYear),
                    minYear = minYear,
                    maxYear = maxYear,
                    category = category,
                    query = query,
                    rows = rows,
                    totalCount = total,
                    doneCount = done,
                    pendingCount = pending,
                    overdueCount = overdue,
                    isLoading = false
                )
            }.collect {}
        }
    }

    fun onEvent(event: OverviewUiEvent) {
        when (event) {
            is OverviewUiEvent.SelectYear -> yearFlow.value = event.year
            is OverviewUiEvent.ShiftYear -> {
                val s = _uiState.value
                yearFlow.value = (s.year + event.delta).coerceIn(s.minYear, s.maxYear)
            }
            is OverviewUiEvent.SelectCategory -> categoryFlow.value = event.category
            is OverviewUiEvent.UpdateQuery -> queryFlow.value = event.value
            is OverviewUiEvent.Reload -> { /* combine 自动更新 */ }
            is OverviewUiEvent.ExportCsv -> exportCsv()
            is OverviewUiEvent.ImportCsv -> importCsv(event.uri)
            is OverviewUiEvent.ConsumeMessage ->
                _uiState.value = _uiState.value.copy(message = null)
        }
    }

    /** 导出当前年度 + 当前分类的案件为 CSV 到下载目录 */
    private fun exportCsv() {
        viewModelScope.launch {
            val s = _uiState.value
            val allCases = repository.getAllCases().first()
            val yearStart = CalendarUtil.toEpochMillis(LocalDate.of(s.year, 1, 1))
            val yearEnd = CalendarUtil.toEpochMillis(LocalDate.of(s.year + 1, 1, 1))
            val list = allCases.filter { c ->
                val ref = if (c.accidentDate > 0) c.accidentDate else c.createdAt
                c.caseType == s.category && ref in yearStart until yearEnd
            }
            val result = withContext(Dispatchers.IO) {
                writeCsv(list, s.year)
            }
            _uiState.value = _uiState.value.copy(
                message = if (result.isBlank()) "导出失败" else "已导出：$result"
            )
        }
    }

    private fun writeCsv(cases: List<Case>, year: Int): String {
        val label = if (cases.firstOrNull()?.caseType == CaseType.VIOLATION) "违法" else "事故"
        val header = "案件类型,案号,时间,时间文本,当事人,地点,案情,责任划分,责任民警,扣车返还,违法处理"
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val sb = StringBuilder(header)
        cases.forEach { c ->
            sb.append('\n').append(
                listOf(
                    if (c.caseType == CaseType.VIOLATION) "违法" else "事故",
                    c.caseNumber,
                    if (c.accidentDate > 0) dateFormat.format(Date(c.accidentDate)) else "",
                    c.accidentTime,
                    c.parties.joinToString("、"),
                    c.location,
                    c.description,
                    liabilityDisplay(c),
                    c.handlingOfficer,
                    when {
                        !c.vehicleSeized -> "未扣车"
                        c.vehicleReturned -> "已返还"
                        else -> "未返还"
                    },
                    if (c.violationHandled) "已处理" else "未处理"
                ).map { it.replace(",", "，") }.joinToString(",")
            )
        }
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = appContext.contentResolver
                val values = android.content.ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, "${label}案件总览_${year}_${System.currentTimeMillis()}.csv")
                    put(MediaStore.Downloads.MIME_TYPE, "text/csv")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: return "导出失败"
                resolver.openOutputStream(uri)?.use { out ->
                    OutputStreamWriter(out).use { it.write(sb.toString()) }
                }
                "下载/${label}案件总览_${year}.csv"
            } else {
                val dir = appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                    ?: return "当前系统版本请使用 Android 10 及以上导出"
                val file = java.io.File(dir, "${label}案件总览_${year}_${System.currentTimeMillis()}.csv")
                file.writeText(sb.toString())
                "应用目录/${file.name}"
            }
        } catch (e: Exception) {
            "导出失败：${e.message}"
        }
    }

    /** 责任划分显示：优先按当事人责任 JSON（v1.70 详情页责任卡片录入），其次旧文本字段 */
    fun liabilityDisplay(c: Case): String {
        if (c.responsibilities.isNotBlank()) {
            return runCatching {
                val arr = JSONArray(c.responsibilities)
                (0 until arr.length()).map { i ->
                    val obj = arr.getJSONObject(i)
                    val party = obj.optString("party", "")
                    val liab = obj.optString("liability", "")
                    if (party.isNotBlank() && liab.isNotBlank()) "$party·$liab" else liab
                }.filter { it.isNotBlank() }.joinToString("、")
            }.getOrDefault("")
        }
        return c.responsibility
    }

    /** v1.70 从 CSV 导入案件：按导出同款列顺序解析，案号重复则跳过 */
    private fun importCsv(uri: android.net.Uri) {
        viewModelScope.launch {
            val existed = repository.getAllCases().first()
            val existedNumbers = existed.map { it.caseNumber }.toSet()
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val sb = StringBuilder()
                    appContext.contentResolver.openInputStream(uri)?.use { input ->
                        input.bufferedReader().forEachLine { line ->
                            if (sb.isNotEmpty()) sb.append('\n')
                            sb.append(line)
                        }
                    }
                    val lines = sb.toString().trim().split('\n').filter { it.isNotBlank() }
                    if (lines.size <= 1) return@runCatching "0 条（文件无数据）"
                    // 表头：案件类型,案号,时间,时间文本,当事人,地点,案情,责任划分,责任民警,扣车返还,违法处理
                    var imported = 0
                    var skipped = 0
                    lines.drop(1).forEach { line ->
                        val cols = line.split(',', limit = 11)
                        if (cols.size < 7) return@forEach
                        val typeName = cols[0].trim()
                        val caseNumber = cols[1].trim()
                        if (caseNumber in existedNumbers) { skipped++; return@forEach }
                        val dateText = cols[2].trim()
                        val timeText = cols[3].trim()
                        val partiesText = cols[4].trim()
                        val location = cols[5].trim()
                        val desc = cols[6].trim()
                        val responsibility = cols.getOrNull(7)?.trim() ?: ""
                        val officer = cols.getOrNull(8)?.trim() ?: ""
                        val vehicleText = cols.getOrNull(9)?.trim() ?: ""
                        val violationHandledText = cols.getOrNull(10)?.trim() ?: ""
                        val dateMs = com.casetracker.domain.model.AccidentParser.parseDate(dateText)
                        if (dateMs <= 0L) { skipped++; return@forEach }
                        val caseType = if (typeName == "违法") CaseType.VIOLATION else CaseType.ACCIDENT
                        val parties = partiesText.split('、', '，').map { it.trim() }.filter { it.isNotBlank() }
                        val now = System.currentTimeMillis()
                        val title = com.casetracker.domain.model.FormatUtils.buildCaseTitle(dateMs, parties.firstOrNull() ?: "", caseType)
                        val newCase = Case(
                            caseNumber = if (caseNumber.isNotBlank()) caseNumber
                                         else com.casetracker.domain.model.FormatUtils.buildCaseNumber(dateMs, now),
                            caseType = caseType,
                            title = title,
                            status = CaseStatus.PENDING_ACCEPTANCE,
                            progress = 0,
                            parties = parties,
                            description = desc,
                            location = location,
                            deadline = com.casetracker.domain.model.LegalDeadlineCalculator.calculateDeadline(caseType, CaseStatus.PENDING_ACCEPTANCE, now),
                            createdAt = 0L,
                            updatedAt = now,
                            accidentDate = dateMs,
                            accidentTime = timeText,
                            handlingOfficer = officer,
                            responsibility = responsibility,
                            vehicleSeized = vehicleText == "未返还" || vehicleText == "已返还",
                            vehicleReturned = vehicleText == "已返还",
                            violationHandled = violationHandledText == "已处理"
                        )
                        repository.createCase(
                            newCase,
                            listOf(
                                com.casetracker.domain.model.CaseEvent(
                                    caseId = 0,
                                    eventType = "CREATED",
                                    title = "案件创建（导入）",
                                    description = "通过总览「上传」导入表格记录",
                                    occurredAt = now,
                                    isMilestone = true
                                )
                            )
                        )
                        imported++
                    }
                    "导入 $imported 条${if (skipped > 0) "，跳过 $skipped 条（案号重复或无日期）" else ""}"
                }.getOrElse { "导入失败：${it.message}" }
            }
            _uiState.value = _uiState.value.copy(message = result)
        }
    }

    private fun toRow(
        c: Case,
        overdueById: Map<Long, Boolean>,
        v3DateById: Map<Long, Long>,
        v4DateById: Map<Long, Long>,
        vioRegisteredIds: Set<Long>
    ): OverviewRow {
        val overdue = overdueById[c.id] == true
        val (statusLabel, tone) = when {
            c.status == CaseStatus.CLOSED -> when (c.caseType) {
                CaseType.ACCIDENT -> "已认定" to OverviewTone.DONE
                CaseType.VIOLATION -> "已结案" to OverviewTone.DONE
            }
            overdue -> "超期" to OverviewTone.OVERDUE
            c.caseType == CaseType.VIOLATION -> {
                // 已受案登记（VR 完成）→ 待处罚；未受案 → 待立案
                if (c.id in vioRegisteredIds) "待处罚" to OverviewTone.PENDING
                else "待立案" to OverviewTone.REGISTERED
            }
            else -> "处理中" to OverviewTone.PENDING
        }
        val fields = when (c.caseType) {
            CaseType.ACCIDENT -> {
                val liability = liabilityDisplay(c)
                listOf(
                    OverviewField("责任划分", liability.ifBlank { "未填" }, liability.isNotBlank()),
                    OverviewField("责任民警", c.handlingOfficer.ifBlank { "未填" }, c.handlingOfficer.isNotBlank()),
                    OverviewField(
                        "扣车返还",
                        when {
                            !c.vehicleSeized -> "未扣车"
                            c.vehicleReturned -> "已返还"
                            else -> "未返还"
                        },
                        c.vehicleSeized
                    ),
                    OverviewField(
                        "违法处理",
                        if (c.violationHandled) "已处理" else "未处理",
                        c.violationHandled
                    )
                )
            }
            CaseType.VIOLATION -> {
                val v3 = v3DateById[c.id] ?: 0L
                val v4 = v4DateById[c.id] ?: 0L
                val hasAdmin = c.violationType == com.casetracker.domain.model.ViolationType.ADMINISTRATIVE
                listOf(
                    OverviewField(
                        "处罚告知",
                        when {
                            !hasAdmin -> "—"
                            v3 > 0 -> "已告知 ${CalendarUtil.toLocalDate(v3)}"
                            else -> "未告知"
                        },
                        v3 > 0
                    ),
                    OverviewField(
                        "处罚决定",
                        when {
                            !hasAdmin -> "—"
                            v4 > 0 -> "已作出 ${CalendarUtil.toLocalDate(v4)}"
                            else -> "未作出"
                        },
                        v4 > 0
                    )
                )
            }
        }
        val partiesText = c.parties.joinToString("、").ifBlank { c.selfParty.ifBlank { "未填" } }
        return OverviewRow(
            case = c,
            title = FormatUtils.unifiedTitle(c),
            dateText = FormatUtils.accidentDateTime(c.accidentDate, c.accidentTime),
            partiesText = partiesText,
            locationText = c.location.ifBlank { "未填" },
            descText = c.description.ifBlank { "暂无案情记录" },
            statusLabel = statusLabel,
            tone = tone,
            fields = fields,
            overdue = overdue
        )
    }
}
