package com.casetracker.data.local.entity

import androidx.room.Entity

/**
 * v1.82 办案清单勾选状态：按案件 + 节点 + 条目保存，清单内容来自 CheckListTemplates 模板。
 */
@Entity(tableName = "checklist_items", primaryKeys = ["caseId", "nodeCode", "itemIndex"])
data class ChecklistItemEntity(
    val caseId: Long,
    val nodeCode: String,
    val itemIndex: Int,
    val itemText: String,
    val done: Boolean = false
)
