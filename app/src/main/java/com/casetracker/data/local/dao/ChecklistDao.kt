package com.casetracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.casetracker.data.local.entity.ChecklistItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChecklistDao {

    @Query("SELECT * FROM checklist_items WHERE caseId = :caseId")
    fun observeByCase(caseId: Long): Flow<List<ChecklistItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: ChecklistItemEntity)

    @Query(
        "UPDATE checklist_items SET done = :done " +
            "WHERE caseId = :caseId AND nodeCode = :nodeCode AND itemIndex = :itemIndex"
    )
    suspend fun setDone(caseId: Long, nodeCode: String, itemIndex: Int, done: Boolean)

    @Query("DELETE FROM checklist_items WHERE caseId = :caseId")
    suspend fun clearCase(caseId: Long)
}
