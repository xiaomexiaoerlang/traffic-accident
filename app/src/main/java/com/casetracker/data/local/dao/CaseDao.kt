package com.casetracker.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.casetracker.data.local.entity.CaseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CaseDao {
    @Query("SELECT * FROM cases ORDER BY updated_at DESC")
    fun getAllCases(): Flow<List<CaseEntity>>

    @Query("SELECT * FROM cases WHERE status = :status ORDER BY updated_at DESC")
    fun getCasesByStatus(status: String): Flow<List<CaseEntity>>

    @Query("SELECT * FROM cases WHERE id = :id")
    fun getCaseById(id: Long): Flow<CaseEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCase(caseEntity: CaseEntity): Long

    @Update
    suspend fun updateCase(caseEntity: CaseEntity)

    @Delete
    suspend fun deleteCase(caseEntity: CaseEntity)

    @Query("UPDATE cases SET status = :status, progress = :progress, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, progress: Int, updatedAt: Long)

    /** 归档事故流程：仅隐藏「事故」Tab 中的流程卡片，案件记录保留 */
    @Query("UPDATE cases SET flow_archived = 1, updated_at = :now WHERE id = :id")
    suspend fun archiveFlow(id: Long, now: Long)

    /** 免鉴定快捷出证：跳过 鉴定委托/鉴定报告出具/鉴定报告送达/事故调查报告 */
    @Query("UPDATE cases SET flow_skip_investigation = 1, updated_at = :now WHERE id = :id")
    suspend fun setSkipInvestigation(id: Long, now: Long)

    /** 一般程序快速处理：提交《快速处理申请书》，跳过 鉴定相关与事故调查报告 */
    @Query("UPDATE cases SET flow_quick_process = 1, updated_at = :now WHERE id = :id")
    suspend fun setQuickProcess(id: Long, now: Long)

    /** v1.67 修改鉴定选择：按参数重设 免鉴定 / 快速处理 标记（可改回主线） */
    @Query("UPDATE cases SET flow_skip_investigation = :skip, flow_quick_process = :quick, updated_at = :now WHERE id = :id")
    suspend fun setInvestigationMode(id: Long, skip: Boolean, quick: Boolean, now: Long)

    /** 更新法定截止时间（快捷路径：事故发生 + 10 个工作日，v1.74） */
    @Query("UPDATE cases SET deadline = :deadline, updated_at = :now WHERE id = :id")
    suspend fun updateDeadline(id: Long, deadline: Long, now: Long)

    @Query("SELECT COUNT(*) FROM cases WHERE status = :status")
    fun countByStatus(status: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM cases")
    fun getTotalCount(): Flow<Int>

    @Query("DELETE FROM cases")
    suspend fun clearAll()

    @Query("""
        SELECT * FROM cases 
        WHERE case_number LIKE '%' || :query || '%'
           OR title LIKE '%' || :query || '%'
           OR location LIKE '%' || :query || '%'
           OR parties LIKE '%' || :query || '%'
           OR self_party LIKE '%' || :query || '%'
           OR other_party LIKE '%' || :query || '%'
           OR handler_unit LIKE '%' || :query || '%'
           OR handling_officer LIKE '%' || :query || '%'
        ORDER BY updated_at DESC
    """)
    fun searchCases(query: String): Flow<List<CaseEntity>>
}
