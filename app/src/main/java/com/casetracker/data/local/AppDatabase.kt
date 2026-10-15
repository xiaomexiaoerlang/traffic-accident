package com.casetracker.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.casetracker.data.local.dao.CaseDao
import com.casetracker.data.local.dao.CaseEventDao
import com.casetracker.data.local.entity.CaseEntity
import com.casetracker.data.local.entity.CaseEventEntity

@Database(entities = [CaseEntity::class, CaseEventEntity::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun caseDao(): CaseDao
    abstract fun caseEventDao(): CaseEventDao
}
