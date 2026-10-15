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
