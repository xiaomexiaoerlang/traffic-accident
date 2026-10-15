package com.casetracker.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cases")
data class CaseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "case_number") val caseNumber: String,
    @ColumnInfo(name = "case_type") val caseType: String,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "status") val status: String,
    @ColumnInfo(name = "progress") val progress: Int,
    @ColumnInfo(name = "parties") val parties: String,
    @ColumnInfo(name = "description") val description: String,
    @ColumnInfo(name = "location") val location: String,
    @ColumnInfo(name = "deadline") val deadline: Long,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    // ===== 事故专项字段 =====
    @ColumnInfo(name = "accident_date") val accidentDate: Long = 0L,
    @ColumnInfo(name = "accident_time") val accidentTime: String = "",
    @ColumnInfo(name = "accident_type") val accidentType: String = "PROPERTY", // PROPERTY/INJURY/DEATH
    @ColumnInfo(name = "procedure") val procedure: String = "GENERAL", // SIMPLE / GENERAL
    @ColumnInfo(name = "handler_unit") val handlerUnit: String = "",
    @ColumnInfo(name = "hit_and_run") val hitAndRun: Boolean = false,
    @ColumnInfo(name = "appraisal_extended") val appraisalExtended: Boolean = false,
    @ColumnInfo(name = "handling_officer") val handlingOfficer: String = "",
    @ColumnInfo(name = "self_party") val selfParty: String = "",
    @ColumnInfo(name = "other_party") val otherParty: String = "",
)
