package com.casetracker.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "case_events",
    foreignKeys = [ForeignKey(
        entity = CaseEntity::class,
        parentColumns = ["id"],
        childColumns = ["case_id"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("case_id")]
)
data class CaseEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "case_id") val caseId: Long,
    @ColumnInfo(name = "event_type") val eventType: String,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "description") val description: String,
    @ColumnInfo(name = "occurred_at") val occurredAt: Long,
    @ColumnInfo(name = "is_milestone") val isMilestone: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    // ===== 流程节点字段 =====
    @ColumnInfo(name = "node_code") val nodeCode: String = "",        // N0..N12
    @ColumnInfo(name = "expected_date") val expectedDate: Long = 0L,  // 预计日期（自动计算）
    @ColumnInfo(name = "is_auto") val isAuto: Boolean = false,        // 是否自动计算
    @ColumnInfo(name = "is_required") val isRequired: Boolean = false,
)
