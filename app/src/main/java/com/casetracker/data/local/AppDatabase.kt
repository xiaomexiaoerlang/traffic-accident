package com.casetracker.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.casetracker.data.local.dao.CaseDao
import com.casetracker.data.local.dao.CaseEventDao
import com.casetracker.data.local.dao.ChecklistDao
import com.casetracker.data.local.entity.CaseEntity
import com.casetracker.data.local.entity.CaseEventEntity
import com.casetracker.data.local.entity.ChecklistItemEntity

@Database(
    entities = [CaseEntity::class, CaseEventEntity::class, ChecklistItemEntity::class],
    version = 11,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun caseDao(): CaseDao
    abstract fun caseEventDao(): CaseEventDao
    abstract fun checklistDao(): ChecklistDao

    companion object {
        /**
         * 清理 v1.1.0 之前 recordNodeDate 误插导致的重复行：
         * 对每个案件的每个 nodeCode，若已有 occurred_at>0 的实际事件，
         * 删除残留的 occurred_at=0 占位行。
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    DELETE FROM case_events
                    WHERE occurred_at = 0
                      AND node_code != ''
                      AND EXISTS (
                        SELECT 1 FROM case_events e2
                        WHERE e2.case_id = case_events.case_id
                          AND e2.node_code = case_events.node_code
                          AND e2.occurred_at > 0
                      )
                    """.trimIndent()
                )
            }
        }

        /**
         * v1.4.0：
         *  - cases.flow_archived：事故流程是否已从「事故」Tab 移除（案件记录保留）
         *  - cases.injury_detail：伤人事故的伤情情况
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE cases ADD COLUMN flow_archived INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE cases ADD COLUMN injury_detail TEXT NOT NULL DEFAULT ''")
            }
        }

        /**
         * v1.6.0：
         *  - cases.flow_skip_investigation：免鉴定快捷出证（跳过鉴定委托/鉴定报告/事故调查报告，受案登记+10 个工作日）
         *  - cases.flow_quick_process：一般程序快速处理（提交《快速处理申请书》后跳过鉴定与调查报告）
         */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE cases ADD COLUMN flow_skip_investigation INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE cases ADD COLUMN flow_quick_process INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * v1.63.0：
         *  - case_events.manual_override：节点日期是否由用户手动设定（true 后自动顺延不再覆盖其实际日期）
         */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE case_events ADD COLUMN manual_override INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * v1.66：
         *  - cases.violation_type：违法行为案件类型（行政案件 / 刑事案件），默认行政案件
         */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE cases ADD COLUMN violation_type TEXT NOT NULL DEFAULT 'ADMINISTRATIVE'")
            }
        }

        /**
         * v1.69：年度案件总览专项字段（事故案件记录）
         *  - cases.responsibility：责任划分（如「同等责任」「曾某主责」）
         *  - cases.vehicle_seized：是否扣留车辆
         *  - cases.vehicle_returned：扣留车辆是否已返还
         *  - cases.violation_handled：事故中当事人违法行为是否已处理
         */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE cases ADD COLUMN responsibility TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE cases ADD COLUMN vehicle_seized INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE cases ADD COLUMN vehicle_returned INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE cases ADD COLUMN violation_handled INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * v1.70：责任划分结构化（认定书出具后按当事人逐人录入）
         *  - cases.responsibilities：JSON 字符串 [{"party":"王静","liability":"主责"},...]
         */
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE cases ADD COLUMN responsibilities TEXT NOT NULL DEFAULT ''")
            }
        }

        /**
         * v1.82：办案清单勾选状态（右滑抽屉）
         *  - checklist_items：caseId + nodeCode + itemIndex 复合主键，done 标记已办
         */
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `checklist_items` (
                        `caseId` INTEGER NOT NULL,
                        `nodeCode` TEXT NOT NULL,
                        `itemIndex` INTEGER NOT NULL,
                        `itemText` TEXT NOT NULL,
                        `done` INTEGER NOT NULL,
                        PRIMARY KEY(`caseId`, `nodeCode`, `itemIndex`)
                    )
                    """.trimIndent()
                )
            }
        }
    }
}
