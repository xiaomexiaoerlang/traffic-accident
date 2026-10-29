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
    @ColumnInfo(name = "flow_archived") val flowArchived: Boolean = false,
    @ColumnInfo(name = "injury_detail") val injuryDetail: String = "",
    @ColumnInfo(name = "flow_skip_investigation") val flowSkipInvestigation: Boolean = false,
    @ColumnInfo(name = "flow_quick_process") val flowQuickProcess: Boolean = false,
    @ColumnInfo(name = "violation_type") val violationType: String = "ADMINISTRATIVE", // ADMINISTRATIVE/CRIMINAL
    // ===== 年度总览专项字段（v1.69）=====
    @ColumnInfo(name = "responsibility") val responsibility: String = "",
    // v1.70：按当事人责任划分 JSON（认定书出具后录入）
    @ColumnInfo(name = "responsibilities") val responsibilities: String = "",
    @ColumnInfo(name = "vehicle_seized") val vehicleSeized: Boolean = false,
    @ColumnInfo(name = "vehicle_returned") val vehicleReturned: Boolean = false,
    @ColumnInfo(name = "violation_handled") val violationHandled: Boolean = false,
)
