package com.casetracker.domain.model

import java.time.LocalDate

/**
 * 道路交通安全违法行为案件处理流程模板（v1.66 新增）
 *
 * 依据《道路交通安全违法行为处理程序规定》（公安部令第 105 号发布、第 157 号修正）、《行政处罚法》、《刑事诉讼法》：
 * - 行政案件（行政处罚一般程序）8 节点：
 *   违法查处 → 受案登记 → 立案受理 → 调查取证 → 处罚前告知 → 作出处罚决定 → 决定书送达 → 执行
 * - 刑事案件（涉嫌犯罪移送程序）6 节点：
 *   违法查处 → 受案登记 → 刑事立案 → 侦查取证 → 侦查终结 → 移送审查起诉
 *
 * 时间计算跟随真实日历（工作日跳过周末与法定节假日）：
 *  - 违法查处     = 自动提取案情中的违法时间（编辑页「事故日期」字段），也可手动修改
 *  - 受案登记     = 手动录入
 *  - 立案受理     = 手动录入
 *  - 调查取证     = 立案受理 + 15 个工作日（参照《公安机关办理行政案件程序规定》调查期限）
 *  - 处罚前告知   = 调查取证完成后（+1 个工作日）
 *  - 作出处罚决定 = 告知后 3 个工作日（含陈述申辩复核，参照《程序规定》第五十一条）
 *  - 决定书送达   = 作出处罚决定 + 7 个自然日（《行政处罚法》第六十一条）
 *  - 执行         = 决定书送达 + 15 个自然日（缴纳罚款/执行到位期限）
 *  - 刑事立案     = 受案登记 + 3 个自然日（《刑事诉讼法》第一百一十二条）
 *  - 侦查取证     = 刑事立案后（+1 个工作日）
 *  - 侦查终结     = 侦查取证 + 60 个自然日（侦查羁押期限一般不超过 2 个月，《刑事诉讼法》第一百五十六条）
 *  - 移送审查起诉 = 侦查终结后（+1 个工作日）
 */
object ViolationFlowTemplate {

    /** 违法查处节点码（自动提取违法时间） */
    const val VIOLATION_CODE = "V0"

    /** 受案登记节点码 */
    const val REGISTER_CODE = "VR"

    /** 节点定义 */
    data class NodeDef(
        val code: String,
        val name: String,
        val hint: String,
        val isAuto: Boolean,
        val isRequired: Boolean
    )

    /** 行政案件（行政处罚一般程序）8 节点 */
    val ADMIN_NODES: List<NodeDef> = listOf(
        NodeDef(VIOLATION_CODE, "违法查处", "自动提取案情中的违法时间，也可手动修改", false, true),
        NodeDef(REGISTER_CODE, "受案登记", "录入受案登记日期", false, false),
        NodeDef("V1", "立案受理", "录入立案受理日期；受理后进入调查取证", false, false),
        NodeDef("V2", "调查取证", "立案受理后 15 个工作日内完成调查取证（参照《公安机关办理行政案件程序规定》）", true, false),
        NodeDef("V3", "处罚前告知", "调查取证完成后，告知拟作出的行政处罚、理由、依据及依法享有的权利", true, false),
        NodeDef("V4", "作出处罚决定", "告知后 3 个工作日（含陈述申辩复核）作出处罚决定", true, false),
        NodeDef("V5", "决定书送达", "作出处罚决定后 7 日内送达（《行政处罚法》第六十一条）", true, false),
        NodeDef("V6", "执行", "送达后 15 日内履行（缴纳罚款/执行到位）", true, false),
    )

    /** 刑事案件（涉嫌犯罪移送）6 节点 */
    val CRIMINAL_NODES: List<NodeDef> = listOf(
        NodeDef(VIOLATION_CODE, "违法查处", "自动提取案情中的违法时间，也可手动修改", false, true),
        NodeDef(REGISTER_CODE, "受案登记", "录入受案登记日期", false, false),
        NodeDef("VC1", "刑事立案", "受案后 3 日内审查决定是否刑事立案（《刑事诉讼法》第一百一十二条）", true, false),
        NodeDef("VC2", "侦查取证", "立案后开展侦查取证", true, false),
        NodeDef("VC3", "侦查终结", "侦查羁押期限一般不超过 2 个月（《刑事诉讼法》第一百五十六条）", true, false),
        NodeDef("VC4", "移送审查起诉", "侦查终结后移送人民检察院审查起诉", true, false),
    )

    /** 全部节点（用于占位事件初始化与节点日期查找） */
    val ALL_NODES: List<NodeDef> = (ADMIN_NODES + CRIMINAL_NODES)
        .distinctBy { it.code }

    /** 当前案型路径节点定义（行政 8 节点 / 刑事 6 节点） */
    fun pathNodes(case: Case): List<NodeDef> = when (case.violationType) {
        ViolationType.CRIMINAL -> CRIMINAL_NODES
        else -> ADMIN_NODES
    }

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

        val violationDate = CalendarUtil.toLocalDate(case.accidentDate.takeIf { it > 0 } ?: return 0L)
        return when (code) {
            VIOLATION_CODE -> CalendarUtil.toEpochMillis(violationDate)
            // 受案登记 / 立案受理：手动录入，无推算日期
            REGISTER_CODE, "V1" -> 0L
            // 调查取证 = 立案受理 + 15 个工作日
            "V2" -> get("V1")?.let { CalendarUtil.addWorkDays(it, 15) }?.let { CalendarUtil.toEpochMillis(it) } ?: 0L
            // 处罚前告知 = 调查取证完成后 + 1 个工作日
            "V3" -> get("V2")?.let { CalendarUtil.addWorkDays(it, 1) }?.let { CalendarUtil.toEpochMillis(it) } ?: 0L
            // 作出处罚决定 = 告知后 + 3 个工作日（含陈述申辩复核）
            "V4" -> get("V3")?.let { CalendarUtil.addWorkDays(it, 3) }?.let { CalendarUtil.toEpochMillis(it) } ?: 0L
            // 决定书送达 = 作出处罚决定 + 7 个自然日
            "V5" -> get("V4")?.let { CalendarUtil.addNaturalDays(it, 7) }?.let { CalendarUtil.toEpochMillis(it) } ?: 0L
            // 执行 = 送达 + 15 个自然日
            "V6" -> get("V5")?.let { CalendarUtil.addNaturalDays(it, 15) }?.let { CalendarUtil.toEpochMillis(it) } ?: 0L
            // 刑事立案 = 受案登记 + 3 个自然日
            "VC1" -> get(REGISTER_CODE)?.let { CalendarUtil.addNaturalDays(it, 3) }?.let { CalendarUtil.toEpochMillis(it) } ?: 0L
            // 侦查取证 = 刑事立案后 + 1 个工作日
            "VC2" -> get("VC1")?.let { CalendarUtil.addWorkDays(it, 1) }?.let { CalendarUtil.toEpochMillis(it) } ?: 0L
            // 侦查终结 = 侦查取证 + 60 个自然日
            "VC3" -> get("VC2")?.let { CalendarUtil.addNaturalDays(it, 60) }?.let { CalendarUtil.toEpochMillis(it) } ?: 0L
            // 移送审查起诉 = 侦查终结后 + 1 个工作日
            "VC4" -> get("VC3")?.let { CalendarUtil.addWorkDays(it, 1) }?.let { CalendarUtil.toEpochMillis(it) } ?: 0L
            else -> 0L
        }
    }

    /**
     * 构建当前案型路径节点列表（结合已存在的事件）。
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
        return pathNodes(case).map { def ->
            val event = eventByCode[def.code]
            val expected = expectedDateFor(def.code, actualByCode, case)

            val state = when {
                event != null && event.occurredAt > 0 -> NodeState.DONE
                !firstPendingPassed -> {
                    firstPendingPassed = true
                    // v1.75：到期日当天仍属期限内（之日起不包括当天），次日才开始显示超期
                    if (expected > 0 && CalendarUtil.toLocalDate(now) > CalendarUtil.toLocalDate(expected))
                        NodeState.OVERDUE
                    else NodeState.PENDING
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

    /** 按已完成节点数计算进度（0-100），按当前路径总节点数 */
    fun calculateProgress(nodes: List<FlowNode>): Int {
        if (nodes.isEmpty()) return 0
        val done = nodes.count { it.state == NodeState.DONE }
        return (done.toFloat() / nodes.size * 100).toInt().coerceIn(0, 100)
    }
}
