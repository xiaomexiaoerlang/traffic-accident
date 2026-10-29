package com.casetracker.domain.model

enum class CaseType(val displayName: String) {
    ACCIDENT("交通事故"),
    VIOLATION("违法行为")
}

/** 违法行为案件类型（v1.66）：行政案件 / 刑事案件，决定详情流程节点 */
enum class ViolationType(val displayName: String) {
    ADMINISTRATIVE("行政案件"),
    CRIMINAL("刑事案件")
}

enum class AccidentType(val displayName: String) {
    PROPERTY("财产损失"),
    INJURY("伤人"),
    DEATH("死亡")
}

enum class AccidentProcedure(val displayName: String) {
    SIMPLE("简易程序"),
    GENERAL("一般程序"),
    QUICK("一般程序快速处理")
}

enum class CaseStatus(
    val displayName: String,
    val minProgress: Int,
    val maxProgress: Int
) {
    PENDING_ACCEPTANCE("待受理", 0, 20),
    UNDER_INVESTIGATION("调查中", 20, 50),
    IN_PROGRESS("处理中", 50, 80),
    REVIEW("审核中", 80, 95),
    CLOSED("已认定", 95, 100);

    fun next(): CaseStatus? = when (this) {
        PENDING_ACCEPTANCE -> UNDER_INVESTIGATION
        UNDER_INVESTIGATION -> IN_PROGRESS
        IN_PROGRESS -> REVIEW
        REVIEW -> CLOSED
        CLOSED -> null
    }

    companion object {
        fun fromProgress(progress: Int): CaseStatus = entries.firstOrNull {
            progress in it.minProgress..it.maxProgress
        } ?: PENDING_ACCEPTANCE
    }
}

data class Case(
    val id: Long = 0,
    val caseNumber: String,
    val caseType: CaseType,
    val title: String,
    val status: CaseStatus,
    val progress: Int,
    val parties: List<String>,
    val description: String,
    val location: String,
    val deadline: Long,
    val createdAt: Long,
    val updatedAt: Long,
    // 事故专项字段
    val accidentDate: Long = 0L,
    val accidentTime: String = "",
    val accidentType: AccidentType = AccidentType.PROPERTY,
    val procedure: AccidentProcedure = AccidentProcedure.GENERAL,
    val handlerUnit: String = "",
    val hitAndRun: Boolean = false,
    val appraisalExtended: Boolean = false,
    val handlingOfficer: String = "",
    val selfParty: String = "",   // 本方当事人
    val otherParty: String = "",  // 对方当事人
    // 流程归档：事故流程卡片被左滑移除后置 true（案件记录仍保留在「案件」页）
    val flowArchived: Boolean = false,
    // 伤情情况（伤人事故）
    val injuryDetail: String = "",
    // 免鉴定：不需要委托鉴定时置 true，跳过 鉴定委托/鉴定报告出具/鉴定报告送达/事故调查报告，
    // 自事故发生之日起 10 个工作日内出具事故认定书（v1.74 锚点由受案登记改为事故发生）
    val flowSkipInvestigation: Boolean = false,
    // 一般程序快速处理：当事人提交《一般程序快速处理申请书》后置 true，
    // 跳过 鉴定相关与事故调查报告，自事故发生之日起 10 个工作日内出具事故认定书（v1.74）
    val flowQuickProcess: Boolean = false,
    // 违法行为案件类型（v1.66）：行政案件 / 刑事案件
    val violationType: ViolationType = ViolationType.ADMINISTRATIVE,
    // ===== 年度总览专项字段（v1.69，事故案件）=====
    // 责任划分：如「同等责任」「曾某主责」等，录入认定结论
    val responsibility: String = "",
    // 责任划分（v1.70 结构化）：事故认定书出具后按当事人逐人录入，JSON：[{"party":"王静","liability":"主责"},...]
    val responsibilities: String = "",
    // 是否扣留车辆（扣车）
    val vehicleSeized: Boolean = false,
    // 扣留车辆是否已返还
    val vehicleReturned: Boolean = false,
    // 事故中当事人违法行为是否已处理（另行处罚）
    val violationHandled: Boolean = false
) {
    fun isOverdue(now: Long = System.currentTimeMillis()): Boolean =
        status != CaseStatus.CLOSED && deadline > 0 && now > deadline

    fun daysRemaining(now: Long = System.currentTimeMillis()): Int {
        if (deadline <= 0L) return -1
        val diff = deadline - now
        return (diff / (1000 * 60 * 60 * 24)).toInt()
    }

    fun calculateProgress(now: Long = System.currentTimeMillis()): Int {
        if (status == CaseStatus.CLOSED) return 100
        if (deadline <= 0L) return status.minProgress
        val totalDuration = (deadline - createdAt).coerceAtLeast(1L)
        val elapsed = (now - createdAt).coerceIn(0L, totalDuration)
        val timeProgress = ((elapsed.toFloat() / totalDuration) * 100).toInt()
        return maxOf(timeProgress, status.minProgress).coerceAtMost(status.maxProgress)
    }
}

data class CaseEvent(
    val id: Long = 0,
    val caseId: Long,
    val eventType: String,
    val title: String,
    val description: String,
    val occurredAt: Long,
    val isMilestone: Boolean = false,
    // 流程节点字段
    val nodeCode: String = "",
    val expectedDate: Long = 0L,
    val isAuto: Boolean = false,
    val isRequired: Boolean = false,
    // 用户是否手动设定过该节点日期（true 后自动顺延不再覆盖）
    val manualOverride: Boolean = false
)

/** 节点状态：未开始 / 待办(可录入) / 已完成 / 已超期 */
enum class NodeState { NOT_STARTED, PENDING, DONE, OVERDUE }

/** 流程节点（含状态判断） */
data class FlowNode(
    val code: String,
    val name: String,
    val hint: String,               // 法定时限说明
    val isAuto: Boolean,
    val isRequired: Boolean,
    val event: CaseEvent?,
    val state: NodeState,
    val expectedDate: Long
)
