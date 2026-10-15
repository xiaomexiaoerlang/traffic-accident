package com.casetracker.domain.model

import java.time.LocalDate

/**
 * 道路交通事故（一般程序）处理流程 7 环节模板
 * 时间计算跟随真实日历：
 *  - 鉴定委托     = 事故发生时间 + 3 个工作日
 *  - 鉴定报告出具 = 鉴定委托 + 30 个自然日
 *  - 鉴定报告送达 = 鉴定报告出具 + 5 个工作日
 *  - 事故调查报告 = 鉴定报告送达 + 3 个工作日
 *  - 事故认定书出具 = 鉴定报告送达 + 5 个工作日
 *  - 事故认定书送达 = 事故认定书出具 + 3 个工作日
 */
object AccidentFlowTemplate {

    /** 节点定义 */
    data class NodeDef(
        val code: String,
        val name: String,
        val hint: String,
        val isAuto: Boolean,
        val isRequired: Boolean
    )

    val NODES: List<NodeDef> = listOf(
        NodeDef("N0", "事故发生时间", "用户输入事故发生日期", false, true),
        NodeDef("N1", "鉴定委托时间", "事故发生时间后 3 个工作日", true, false),
        NodeDef("N2", "鉴定报告出具时间", "鉴定委托后 30 个自然日", true, false),
        NodeDef("N3", "鉴定报告送达时间", "鉴定报告出具后 5 个工作日", true, false),
        NodeDef("N4", "事故调查报告时间", "鉴定报告送达后 3 个工作日", true, false),
        NodeDef("N5", "事故认定书出具时间", "鉴定报告送达后 5 个工作日", true, false),
        NodeDef("N6", "事故认定书送达时间", "事故认定书出具后 3 个工作日", true, false),
    )

    /**
     * 计算某节点的预计日期（毫秒时间戳，当天 0 点）。
     * @param actualByCode 已录入节点的实际日期（code -> 时间戳）
     */
    fun expectedDateFor(
        code: String,
        actualByCode: Map<String, Long>,
        case: Case
    ): Long {
        fun get(vararg codes: String): LocalDate? =
            codes.firstNotNullOfOrNull { actualByCode[it]?.takeIf { d -> d > 0 } }
                ?.let { CalendarUtil.toLocalDate(it) }

        val accidentDate = CalendarUtil.toLocalDate(case.accidentDate.takeIf { it > 0 } ?: return 0L)
        return when (code) {
            "N0" -> CalendarUtil.toEpochMillis(accidentDate)
            // 鉴定委托 = 事故 + 3 工作日
            "N1" -> get("N0")?.let { CalendarUtil.addWorkDays(it, 3) }?.let { CalendarUtil.toEpochMillis(it) } ?: 0L
            // 鉴定报告出具 = 委托 + 30 自然日
            "N2" -> get("N1")?.let { CalendarUtil.addNaturalDays(it, 30) }?.let { CalendarUtil.toEpochMillis(it) } ?: 0L
            // 鉴定报告送达 = 出具 + 5 工作日
            "N3" -> get("N2")?.let { CalendarUtil.addWorkDays(it, 5) }?.let { CalendarUtil.toEpochMillis(it) } ?: 0L
            // 事故调查报告 = 送达 + 3 工作日
            "N4" -> get("N3")?.let { CalendarUtil.addWorkDays(it, 3) }?.let { CalendarUtil.toEpochMillis(it) } ?: 0L
            // 事故认定书出具 = 送达 + 5 工作日
            "N5" -> get("N3")?.let { CalendarUtil.addWorkDays(it, 5) }?.let { CalendarUtil.toEpochMillis(it) } ?: 0L
            // 事故认定书送达 = 出具 + 3 工作日
            "N6" -> get("N5")?.let { CalendarUtil.addWorkDays(it, 3) }?.let { CalendarUtil.toEpochMillis(it) } ?: 0L
            else -> 0L
        }
    }

    /**
     * 构建节点列表（结合已存在的事件）。
     * 状态规则：已录入=完成；首个未录入=待办；后续=未开始；待办且超期=已超期。
     */
    fun buildNodes(
        case: Case,
        events: List<CaseEvent>,
        now: Long = System.currentTimeMillis()
    ): List<FlowNode> {
        val eventByCode = events.filter { it.nodeCode.isNotBlank() }.associateBy { it.nodeCode }
        val actualByCode = eventByCode.mapValues { it.value.occurredAt }

        var firstPendingPassed = false
        return NODES.map { def ->
            val event = eventByCode[def.code]
            val expected = expectedDateFor(def.code, actualByCode, case)

            val state = when {
                event != null && event.occurredAt > 0 -> NodeState.DONE
                !firstPendingPassed -> {
                    firstPendingPassed = true
                    if (expected > 0 && now > expected) NodeState.OVERDUE else NodeState.PENDING
                }
                else -> NodeState.NOT_STARTED
            }
            FlowNode(
                code = def.code,
                name = def.name,
                hint = def.hint,
                isAuto = def.isAuto,
                isRequired = def.isRequired,
                event = event,
                state = state,
                expectedDate = expected
            )
        }
    }

    /** 下一待办节点 */
    fun nextPendingNode(nodes: List<FlowNode>): FlowNode? =
        nodes.firstOrNull { it.state != NodeState.DONE }

    /** 前置节点是否全部完成 */
    fun canRecord(nodeCode: String, nodes: List<FlowNode>): Pair<Boolean, String> {
        val idx = nodes.indexOfFirst { it.code == nodeCode }
        if (idx <= 0) return true to ""
        val prev = nodes[idx - 1]
        if (prev.state != NodeState.DONE) {
            return false to "请先完成前置节点：${prev.name}"
        }
        return true to ""
    }

    /** 按已完成节点数计算进度（0-100） */
    fun calculateProgress(nodes: List<FlowNode>): Int {
        if (nodes.isEmpty()) return 0
        val done = nodes.count { it.state == NodeState.DONE }
        return (done.toFloat() / nodes.size * 100).toInt().coerceIn(0, 100)
    }
}
