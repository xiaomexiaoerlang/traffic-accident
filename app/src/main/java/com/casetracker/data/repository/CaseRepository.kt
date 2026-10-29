package com.casetracker.data.repository

import com.casetracker.data.local.dao.CaseDao
import com.casetracker.data.local.dao.CaseEventDao
import com.casetracker.data.mapper.toDomain
import com.casetracker.data.mapper.toEntity
import com.casetracker.domain.model.AccidentFlowTemplate
import com.casetracker.domain.model.AccidentProcedure
import com.casetracker.domain.model.CalendarUtil
import com.casetracker.domain.model.Case
import com.casetracker.domain.model.CaseEvent
import com.casetracker.domain.model.CaseStatus
import com.casetracker.domain.model.CaseType
import com.casetracker.domain.model.FormatUtils
import com.casetracker.domain.model.ViolationFlowTemplate
import com.casetracker.domain.model.isQuickCertificate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CaseRepository @Inject constructor(
    private val caseDao: CaseDao,
    private val eventDao: CaseEventDao
) {
    fun getAllCases(): Flow<List<Case>> =
        caseDao.getAllCases().map { list -> list.map { it.toDomain() } }

    fun searchCases(query: String): Flow<List<Case>> =
        caseDao.searchCases(query).map { list -> list.map { it.toDomain() } }

    fun getCasesByStatus(status: CaseStatus): Flow<List<Case>> =
        caseDao.getCasesByStatus(status.name).map { list -> list.map { it.toDomain() } }

    fun getCaseDetail(id: Long): Flow<Pair<Case?, List<CaseEvent>>> =
        combine(caseDao.getCaseById(id), eventDao.getEventsByCaseId(id)) { case, events ->
            case?.toDomain() to events.map { it.toDomain() }
        }

    /** 所有未完成节点事件（用于首页显示"下一待办节点"） */
    fun getAllPendingNodes(): Flow<List<CaseEvent>> =
        eventDao.getAllPendingNodes().map { list -> list.map { it.toDomain() } }

    /** 全部流程节点事件（含未录入的占位行），供首页按模板与详情页同口径计算下一节点（v1.67） */
    fun getAllNodeEvents(): Flow<List<CaseEvent>> =
        eventDao.getAllEvents().map { list -> list.map { it.toDomain() } }

    /** 每案件按节点码的已完成数（用于案件列表进度条，按当前路径：事故主线/免鉴定/快速处理 / 违法行政/刑事 计算） */
    fun getDoneNodeCounts(): Flow<Map<Long, Map<String, Int>>> =
        eventDao.getDoneNodeCounts(allNodeCodes())
            .map { list ->
                list.groupBy({ it.caseId }, { it.nodeCode to it.doneCount })
                    .mapValues { (_, v) -> v.toMap() }
            }

    /** 每案件已完成节点的实际日期（v1.66：超期按「事故认定书出具」节点判定） */
    fun getDoneNodeDates(): Flow<Map<Long, Map<String, Long>>> =
        eventDao.getDoneNodeDates().map { list ->
            list.groupBy({ it.caseId }, { it.nodeCode to it.occurredAt })
                .mapValues { (_, v) -> v.toMap() }
        }

    /** 事故 + 违法行为 全部流程节点码（进度统计与节点查找共用） */
    private fun allNodeCodes(): List<String> =
        (AccidentFlowTemplate.NODES.map { it.code } + ViolationFlowTemplate.ALL_NODES.map { it.code }).distinct()

    /** 已完成受案登记的案件 ID 集合（「受案登记」筛选） */
    fun getRegisteredCaseIds(): Flow<Set<Long>> =
        eventDao.getRegisteredCaseIds().map { it.toSet() }

    /** 已完成违法受案登记（VR 节点）的案件 ID 集合，用于违法「已受案」统计与筛选（v1.68） */
    fun getViolationRegisteredCaseIds(): Flow<Set<Long>> =
        eventDao.getViolationRegisteredCaseIds().map { it.toSet() }

    suspend fun createCase(case: Case, events: List<CaseEvent>): Long {
        val now = System.currentTimeMillis()
        val caseId = caseDao.insertCase(case.toEntity(now))
        val allEvents = events + buildInitialFlowNodes(case, events)
        allEvents.forEach { event ->
            eventDao.insertEvent(event.toEntity(caseId, now))
        }
        // 事故发生/违法查处：直接提取案情中的时间，创建时自动录入
        when (case.caseType) {
            CaseType.ACCIDENT -> if (case.accidentDate > 0) {
                recordNodeDate(caseId, "N0", case.accidentDate)
            }
            CaseType.VIOLATION -> if (case.accidentDate > 0) {
                recordNodeDate(caseId, ViolationFlowTemplate.VIOLATION_CODE, case.accidentDate)
            }
        }
        return caseId
    }

    suspend fun updateCase(case: Case) {
        caseDao.updateCase(case.toEntity(System.currentTimeMillis()))
        // 编辑案件后同步「事故发生/违法查处」节点日期（案情中的时间）
        when (case.caseType) {
            CaseType.ACCIDENT -> if (case.accidentDate > 0) {
                recordNodeDate(case.id, "N0", case.accidentDate)
            }
            CaseType.VIOLATION -> if (case.accidentDate > 0) {
                recordNodeDate(case.id, ViolationFlowTemplate.VIOLATION_CODE, case.accidentDate)
            }
        }
    }

    suspend fun updateStatus(id: Long, status: CaseStatus, progress: Int) {
        caseDao.updateStatus(id, status.name, progress, System.currentTimeMillis())
    }

    suspend fun addEvent(caseId: Long, event: CaseEvent) {
        eventDao.insertEvent(event.toEntity(caseId, System.currentTimeMillis()))
    }

    /** 免鉴定快捷出证：跳过 鉴定委托/鉴定报告出具/鉴定报告送达/事故调查报告，认定书 = 事故发生 + 10 工作日 */
    suspend fun setSkipInvestigation(caseId: Long) {
        val now = System.currentTimeMillis()
        caseDao.setSkipInvestigation(caseId, now)
        refreshQuickDeadline(caseId)
    }

    /** v1.67 修改鉴定选择：可改回主线（需要委托鉴定）/ 免鉴定 / 快速处理，切换后节点预计日期同步重算 */
    suspend fun setInvestigationMode(caseId: Long, skip: Boolean, quick: Boolean) {
        val now = System.currentTimeMillis()
        caseDao.setInvestigationMode(caseId, skip, quick, now)
        refreshNodeExpectedDates(caseId)
        if (skip || quick) refreshQuickDeadline(caseId)
        maybeAutoClose(caseId)
    }

    /** 一般程序快速处理：提交《快速处理申请书》（申请日 = 今天），跳过鉴定与调查报告 */
    suspend fun setQuickProcess(caseId: Long) {
        val now = System.currentTimeMillis()
        caseDao.setQuickProcess(caseId, now)
        recordNodeDate(caseId, AccidentFlowTemplate.QUICK_PROCESS_CODE, now)
        refreshQuickDeadline(caseId)
    }

    /** 快捷路径下刷新法定截止 = 事故发生 + 10 个工作日（v1.74 去除受案登记后以事故发生为锚点） */
    private suspend fun refreshQuickDeadline(caseId: Long) {
        val entity = caseDao.getCaseById(caseId).first() ?: return
        val case = entity.toDomain()
        if (!case.isQuickCertificate()) return
        val events = eventDao.getEventsByCaseId(caseId).first().map { it.toDomain() }
        val deadline = AccidentFlowTemplate.expectedDateFor(
            "N5",
            events.filter { it.nodeCode.isNotBlank() }.associate { it.nodeCode to it.occurredAt },
            case
        )
        if (deadline > 0) {
            caseDao.updateDeadline(caseId, deadline, System.currentTimeMillis())
        }
    }

    /**
     * 录入流程节点实际日期（upsert：优先更新预置占位事件；
     * 若该案件还没有此节点的占位行，则插入新行）。
     * 更新后 getAllPendingNodes 自动排除该节点，案件页待办与事故流程页同步；
     * 若全部 8 个节点均已完成，自动将案件置为「已认定」终态（进度 100%）。
     */
    suspend fun recordNodeDate(caseId: Long, nodeCode: String, dateMs: Long) {
        val now = System.currentTimeMillis()
        val updated = eventDao.updateNodeOccurredAt(caseId, nodeCode, dateMs)
        if (updated == 0) {
            val def = allNodeDefs().firstOrNull { it.code == nodeCode }
            eventDao.insertEvent(
                CaseEvent(
                    caseId = caseId,
                    eventType = "FLOW_NODE",
                    title = def?.name ?: nodeCode,
                    description = def?.hint ?: "",
                    occurredAt = dateMs,
                    isMilestone = def?.isRequired ?: true,
                    nodeCode = nodeCode,
                    isAuto = def?.isAuto ?: false,
                    isRequired = def?.isRequired ?: false,
                    // 用户手动录入的新节点日期视为手动设定，自动顺延不覆盖
                    manualOverride = true
                ).toEntity(caseId, now)
            )
        }
        // 快捷路径：事故发生（N0）录入后刷新法定截止 = 事故发生 + 10 个工作日
        refreshQuickDeadline(caseId)
        // 剩余天数同步 + 已完成自动节点时间顺延：录入后实时重算该案件全部节点并落库
        refreshNodeExpectedDates(caseId)
        maybeAutoClose(caseId)
    }

    /**
     * v1.74 撤销误点完成：删除某流程节点的实际日期（保留占位事件，恢复为未完成）。
     * 若案件此前因全部节点完成被自动置为「已认定」，撤销后恢复为处理中并重算剩余节点预计日期。
     */
    suspend fun undoNodeComplete(caseId: Long, nodeCode: String) {
        eventDao.updateNodeOccurredAt(caseId, nodeCode, 0L)
        val entity = caseDao.getCaseById(caseId).first() ?: return
        val case = entity.toDomain()
        if (case.status == CaseStatus.CLOSED) {
            caseDao.updateStatus(caseId, CaseStatus.IN_PROGRESS.name, 50, System.currentTimeMillis())
        }
        refreshNodeExpectedDates(caseId)
        refreshQuickDeadline(caseId)
    }

    /**
     * 实时重算该案件全部流程节点并落库：
     *  - 未完成节点：更新预计日期（expectedDate）；
     *  - 已完成且为「自动」节点（鉴定委托/鉴定报告出具/鉴定报告送达/事故调查报告/事故认定书出具/送达）：
     *    若未被用户手动设定过日期（manualOverride=0），其实际日期自动顺延至最新推算值，
     *    实现「修改已完成流程时间 → 后续流程自动更新时间」；
     *  - 用户手动设定过日期的节点（manualOverride=1）保持用户日期不被覆盖。
     * 顺序遍历并同步更新内存 actual，保证后续节点按最新前置日期推算。
     */
    private suspend fun refreshNodeExpectedDates(caseId: Long) {
        val entity = caseDao.getCaseById(caseId).first() ?: return
        val case = entity.toDomain()
        val templateNodes: List<NodeInfo> = when (case.caseType) {
            CaseType.ACCIDENT -> AccidentFlowTemplate.NODES.map {
                NodeInfo(it.code, it.name, it.hint, it.isAuto, it.isRequired)
            }
            CaseType.VIOLATION -> ViolationFlowTemplate.pathNodes(case).map {
                NodeInfo(it.code, it.name, it.hint, it.isAuto, it.isRequired)
            }
        }
        val events = eventDao.getEventsByCaseId(caseId).first().map { it.toDomain() }
        val mutableActual = events
            .filter { it.nodeCode.isNotBlank() }
            .associateBy({ it.nodeCode }, { it.occurredAt })
            .toMutableMap()
        templateNodes.forEach { def ->
            val expected = when (case.caseType) {
                CaseType.ACCIDENT -> AccidentFlowTemplate.expectedDateFor(def.code, mutableActual, case)
                CaseType.VIOLATION -> ViolationFlowTemplate.expectedDateFor(def.code, mutableActual, case)
            }
            if (expected <= 0L) return@forEach
            val node = events.firstOrNull { it.nodeCode == def.code } ?: return@forEach
            if (node.occurredAt > 0 && def.isAuto && !node.manualOverride) {
                // 自动节点已完成且未被手动设定：实际日期跟随推算顺延
                if (node.occurredAt != expected) {
                    eventDao.updateNodeAutoOccurredAt(caseId, def.code, expected)
                    mutableActual[def.code] = expected
                }
            } else if (node.occurredAt == 0L) {
                // 未完成节点：更新预计日期
                eventDao.updateNodeExpectedDate(caseId, def.code, expected)
            }
        }
    }

    /** 全部流程节点定义（事故 + 违法行为，用于节点日期查找与占位事件初始化；统一内部结构避免两模板 NodeDef 类型冲突） */
    private data class NodeInfo(
        val code: String,
        val name: String,
        val hint: String,
        val isAuto: Boolean,
        val isRequired: Boolean
    )

    private fun allNodeDefs(): List<NodeInfo> =
        (AccidentFlowTemplate.NODES.map {
            NodeInfo(it.code, it.name, it.hint, it.isAuto, it.isRequired)
        } + ViolationFlowTemplate.ALL_NODES.map {
            NodeInfo(it.code, it.name, it.hint, it.isAuto, it.isRequired)
        })

    /** 全部流程节点完成时自动置为「已认定」终态（进度 100%） */
    private suspend fun maybeAutoClose(caseId: Long) {
        val entity = caseDao.getCaseById(caseId).first() ?: return
        val case = entity.toDomain()
        if (case.status == CaseStatus.CLOSED) return
        val events = eventDao.getEventsByCaseId(caseId).first().map { it.toDomain() }
        val nodes = when (case.caseType) {
            CaseType.ACCIDENT -> AccidentFlowTemplate.buildNodes(case, events)
            CaseType.VIOLATION -> ViolationFlowTemplate.buildNodes(case, events)
        }
        if (nodes.isNotEmpty() && nodes.all { it.state == com.casetracker.domain.model.NodeState.DONE }) {
            caseDao.updateStatus(caseId, CaseStatus.CLOSED.name, 100, System.currentTimeMillis())
        }
    }

    suspend fun deleteCase(id: Long) {
        caseDao.getCaseById(id).first()?.let { caseDao.deleteCase(it) }
    }

    /** 归档事故流程（左滑移除「事故」Tab 卡片），案件记录保留在「案件」Tab */
    suspend fun archiveFlow(id: Long) {
        caseDao.archiveFlow(id, System.currentTimeMillis())
    }

    suspend fun clearAll() {
        caseDao.clearAll()
    }

    fun countByStatus(status: CaseStatus): Flow<Int> = caseDao.countByStatus(status.name)

    fun getTotalCount(): Flow<Int> = caseDao.getTotalCount()

    /**
     * v1.85 事故台账批量导入：每行生成一个「已认定」事故（进度 100，计入年度总览已认定事故），
     * 台账流程列有日期的写入对应流程节点完成日期（√/备注等无日期项按事故日期视为已办），
     * 全流程节点均标记完成；同一事故时间 + 首当事人已存在则跳过。
     * @return Pair(成功导入条数, 跳过条数)
     */
    suspend fun importAccidentLedger(rows: List<com.casetracker.data.importer.LedgerRow>): Pair<Int, Int> {
        val existing = caseDao.getAllCases().first()
        var imported = 0
        var skipped = 0
        for (row in rows) {
            val accidentDate = CalendarUtil.parseLedgerDate(row.accidentTime)
            if (accidentDate <= 0L) {
                skipped++
                continue
            }
            val parties = row.parties.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
            val firstParty = parties.firstOrNull() ?: ""
            // 防重复：同事故日期 + 首当事人
            val duplicated = existing.any {
                it.accidentDate == accidentDate &&
                    firstParty.isNotBlank() && it.title.contains(firstParty)
            }
            if (duplicated) {
                skipped++
                continue
            }
            val case = Case(
                caseNumber = FormatUtils.buildCaseNumber(accidentDate),
                caseType = CaseType.ACCIDENT,
                title = FormatUtils.buildCaseTitle(accidentDate, firstParty, CaseType.ACCIDENT),
                status = CaseStatus.CLOSED,
                progress = 100,
                parties = parties,
                description = "",
                location = row.location,
                deadline = accidentDate,
                createdAt = accidentDate,
                updatedAt = accidentDate,
                accidentDate = accidentDate,
                handlingOfficer = row.officer,
                procedure = AccidentProcedure.GENERAL
            )
            val id = createCase(case, emptyList())
            // 台账列 → 流程节点完成日期（有日期用台账日期，否则按事故日期视为已办）
            recordNodeDate(id, AccidentFlowTemplate.REGISTER_CODE, dateOrFallback(accidentDate, row.cols[3]))   // 受案
            recordNodeDate(id, AccidentFlowTemplate.INVESTIGATE_CODE, dateOrFallback(accidentDate, row.cols[5])) // 鉴定
            recordNodeDate(id, "N2", accidentDate)                                                               // 鉴定报告出具
            recordNodeDate(id, "N3", dateOrFallback(accidentDate, row.cols[7]))                                  // 鉴定报告送达
            recordNodeDate(id, AccidentFlowTemplate.EFFECT_CODE, accidentDate)                                   // 生效
            recordNodeDate(id, AccidentFlowTemplate.REPORT_CODE, dateOrFallback(accidentDate, row.cols[9]))      // 调查报告
            recordNodeDate(id, AccidentFlowTemplate.CERTIFICATE_CODE, dateOrFallback(accidentDate, row.cols[10]))// 事故认定书
            recordNodeDate(id, "N6", dateOrFallback(accidentDate, row.cols[11]))                                 // 认定书送达
            imported++
        }
        return imported to skipped
    }

    /** 台账流程列值：月.日 → 补年时间戳；√/未还/备注等非日期 → 事故日期兜底 */
    private fun dateOrFallback(accidentDate: Long, value: String?): Long {
        val resolved = value?.let { CalendarUtil.resolveMonthDay(accidentDate, it) } ?: 0L
        return if (resolved > 0L) resolved else accidentDate
    }

    /** 为案件初始化全部流程节点事件（无实际日期，预计日期自动计算）：
     *  事故案件 = 事故 9 节点（N0/NR/NQ/N1~N6）；违法行为案件 = 违法 13 节点（V0/VR/V1~V6/VC1~VC4，按案型路径展示） */
    private fun buildInitialFlowNodes(case: Case, events: List<CaseEvent>): List<CaseEvent> {
        val existedCodes = events.mapNotNull { it.nodeCode.takeIf { c -> c.isNotBlank() } }.toSet()
        val actualByCode = events.filter { it.nodeCode.isNotBlank() }.associateBy({ it.nodeCode }, { it.occurredAt })
        val nodes: List<NodeInfo> = when (case.caseType) {
            CaseType.ACCIDENT -> AccidentFlowTemplate.NODES.map {
                NodeInfo(it.code, it.name, it.hint, it.isAuto, it.isRequired)
            }
            CaseType.VIOLATION -> ViolationFlowTemplate.ALL_NODES.map {
                NodeInfo(it.code, it.name, it.hint, it.isAuto, it.isRequired)
            }
        }
        return nodes
            .filter { it.code !in existedCodes }
            .map { def ->
                val expected = when (case.caseType) {
                    CaseType.ACCIDENT -> AccidentFlowTemplate.expectedDateFor(def.code, actualByCode, case)
                    CaseType.VIOLATION -> ViolationFlowTemplate.expectedDateFor(def.code, actualByCode, case)
                }
                CaseEvent(
                    caseId = 0,
                    eventType = "FLOW_NODE",
                    title = def.name,
                    description = def.hint,
                    occurredAt = 0L,
                    isMilestone = def.isRequired,
                    nodeCode = def.code,
                    expectedDate = expected,
                    isAuto = def.isAuto,
                    isRequired = def.isRequired
                )
            }
    }
}
