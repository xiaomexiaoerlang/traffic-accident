package com.casetracker.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.casetracker.data.local.entity.CaseEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CaseEventDao {
    @Query("SELECT * FROM case_events WHERE case_id = :caseId ORDER BY occurred_at ASC")
    fun getEventsByCaseId(caseId: Long): Flow<List<CaseEventEntity>>

    @Query("SELECT * FROM case_events WHERE occurred_at = 0 AND node_code != '' ORDER BY case_id ASC")
    fun getAllPendingNodes(): Flow<List<CaseEventEntity>>

    @Insert
    suspend fun insertEvent(event: CaseEventEntity): Long

    @Delete
    suspend fun deleteEvent(event: CaseEventEntity)
}
