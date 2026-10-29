package com.casetracker.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.casetracker.data.local.entity.CaseEventEntity
import kotlinx.coroutines.flow.Flow

/** 每案件已完成节点数统计（Room 聚合查询映射） */
data class CaseNodeCount(
    val caseId: Long,
    val doneCount: Int
)

/** 每案件按节点码统计的已完成数（用于分支路径进度计算） */
data class CaseNodeCodeCount(
    val caseId: Long,
    val nodeCode: String,
    val doneCount: Int
)

/** 每案件已完成节点的实际日期（用于超期判定与认定书日期推算） */
data class CaseNodeDate(
    val caseId: Long,
    val nodeCode: String,
    val occurredAt: Long
)

@Dao
interface CaseEventDao {
    @Query("SELECT * FROM case_events WHERE case_id = :caseId ORDER BY occurred_at ASC")
    fun getEventsByCaseId(caseId: Long): Flow<List<CaseEventEntity>>

    @Query("SELECT * FROM case_events WHERE occurred_at = 0 AND node_code != '' ORDER BY case_id ASC")
    fun getAllPendingNodes(): Flow<List<CaseEventEntity>>

    /** 全部流程节点事件（含未录入的占位行），供首页按模板与详情页同口径计算下一节点（v1.67） */
    @Query("SELECT * FROM case_events ORDER BY case_id ASC, occurred_at ASC")
    fun getAllEvents(): Flow<List<CaseEventEntity>>

    /** 每案件按节点码统计已完成数（occurred_at>0），供列表进度按当前路径（主线/快捷）计算 */
    @Query("SELECT case_id AS caseId, node_code AS nodeCode, COUNT(*) AS doneCount FROM case_events WHERE node_code IN (:codes) AND occurred_at > 0 GROUP BY case_id, node_code")
    fun getDoneNodeCounts(codes: List<String>): Flow<List<CaseNodeCodeCount>>

    /** 每案件已完成节点的实际日期（v1.66：超期按「事故认定书出具」节点口径） */
    @Query("SELECT case_id AS caseId, node_code AS nodeCode, occurred_at AS occurredAt FROM case_events WHERE node_code != '' AND occurred_at > 0")
    fun getDoneNodeDates(): Flow<List<CaseNodeDate>>

    /** 已完成受案登记（NR 节点已有实际日期）的案件 ID 列表，用于「受案登记」筛选 */
    @Query("SELECT DISTINCT case_id FROM case_events WHERE node_code = 'NR' AND occurred_at > 0")
    fun getRegisteredCaseIds(): Flow<List<Long>>

    /** 已完成违法受案登记（VR 节点已有实际日期）的案件 ID 列表，用于违法「已受案」统计与筛选（v1.68） */
    @Query("SELECT DISTINCT case_id FROM case_events WHERE node_code = 'VR' AND occurred_at > 0")
    fun getViolationRegisteredCaseIds(): Flow<List<Long>>

    @Insert
    suspend fun insertEvent(event: CaseEventEntity): Long

    /** 按 caseId+nodeCode 更新节点实际日期并标记为手动设定（后续自动顺延不再覆盖），返回受影响行数 */
    @Query("UPDATE case_events SET occurred_at = :dateMs, manual_override = 1 WHERE case_id = :caseId AND node_code = :nodeCode")
    suspend fun updateNodeOccurredAt(caseId: Long, nodeCode: String, dateMs: Long): Int

    /** 自动顺延：仅更新未被手动标记的已完成自动节点实际日期（不修改 manual_override） */
    @Query("UPDATE case_events SET occurred_at = :dateMs WHERE case_id = :caseId AND node_code = :nodeCode AND occurred_at > 0 AND manual_override = 0")
    suspend fun updateNodeAutoOccurredAt(caseId: Long, nodeCode: String, dateMs: Long): Int

    /** 实时重算：更新未完成（occurred_at=0）占位节点的预计日期；已完成节点不改动 */
    @Query("UPDATE case_events SET expected_date = :expected WHERE case_id = :caseId AND node_code = :nodeCode AND occurred_at = 0")
    suspend fun updateNodeExpectedDate(caseId: Long, nodeCode: String, expected: Long)

    @Delete
    suspend fun deleteEvent(event: CaseEventEntity)
}
