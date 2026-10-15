package com.casetracker.data.repository

import com.casetracker.data.local.dao.CaseDao
import com.casetracker.data.local.dao.CaseEventDao
import com.casetracker.data.mapper.toDomain
import com.casetracker.data.mapper.toEntity
import com.casetracker.domain.model.AccidentFlowTemplate
import com.casetracker.domain.model.Case
import com.casetracker.domain.model.CaseEvent
import com.casetracker.domain.model.CaseStatus
import com.casetracker.domain.model.CaseType
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

    suspend fun createCase(case: Case, events: List<CaseEvent>): Long {
        val now = System.currentTimeMillis()
        val caseId = caseDao.insertCase(case.toEntity(now))
        val allEvents = if (case.caseType == CaseType.ACCIDENT) {
            events + buildInitialFlowNodes(case, events)
        } else events
        allEvents.forEach { event ->
            eventDao.insertEvent(event.toEntity(caseId, now))
        }
        return caseId
    }

    suspend fun updateCase(case: Case) {
        caseDao.updateCase(case.toEntity(System.currentTimeMillis()))
    }

    suspend fun updateStatus(id: Long, status: CaseStatus, progress: Int) {
        caseDao.updateStatus(id, status.name, progress, System.currentTimeMillis())
    }

    suspend fun addEvent(caseId: Long, event: CaseEvent) {
        eventDao.insertEvent(event.toEntity(caseId, System.currentTimeMillis()))
    }

    /** 录入流程节点实际日期（若已存在则更新） */
    suspend fun recordNodeDate(caseId: Long, event: CaseEvent) {
        eventDao.insertEvent(event.toEntity(caseId, System.currentTimeMillis()))
    }

    suspend fun deleteCase(id: Long) {
        caseDao.getCaseById(id).first()?.let { caseDao.deleteCase(it) }
    }

    suspend fun clearAll() {
        caseDao.clearAll()
    }

    fun countByStatus(status: CaseStatus): Flow<Int> = caseDao.countByStatus(status.name)

    fun getTotalCount(): Flow<Int> = caseDao.getTotalCount()

    /** 为事故案件初始化 11 个流程节点事件（无实际日期，预计日期自动计算） */
    private fun buildInitialFlowNodes(case: Case, events: List<CaseEvent>): List<CaseEvent> {
        val existedCodes = events.mapNotNull { it.nodeCode.takeIf { c -> c.isNotBlank() } }.toSet()
        val actualByCode = events.filter { it.nodeCode.isNotBlank() }.associateBy({ it.nodeCode }, { it.occurredAt })
        return AccidentFlowTemplate.NODES
            .filter { it.code !in existedCodes }
            .map { def ->
                CaseEvent(
                    caseId = 0,
                    eventType = "FLOW_NODE",
                    title = def.name,
                    description = def.hint,
                    occurredAt = 0L,
                    isMilestone = def.isRequired,
                    nodeCode = def.code,
                    expectedDate = AccidentFlowTemplate.expectedDateFor(def.code, actualByCode, case),
                    isAuto = def.isAuto,
                    isRequired = def.isRequired
                )
            }
    }
}
