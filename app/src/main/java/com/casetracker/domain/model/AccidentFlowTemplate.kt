package com.casetracker.domain.model

import java.time.LocalDate

/**
 * 道路交通事故（一般程序）处理流程模板（v1.74 分支设计）
 *
 * 主线 7 环节（v1.74 去除受案登记）：事故发生 → 鉴定委托 → 鉴定报告出具 → 鉴定报告送达
 *             → 事故调查报告 → 事故认定书出具 → 事故认定书送达
 * 快捷 A（免鉴定）：选择"不需要委托鉴定"，跳过 鉴定委托/鉴定报告出具/鉴定报告送达/事故调查报告，
 *                  自事故发生之日起 10 个工作日内出具事故认定书（3 环节）
 * 快捷 B（一般程序快速处理）：提交《一般程序快速处理申请书》（节点 NQ），
 *                  跳过 鉴定相关与事故调查报告，自事故发生之日起 10 个工作日内出具事故认定书（4 环节）
 *
 * 时间计算跟随真实日历（工作日跳过周末与法定节假日）：
 *  - 事故发生     = 自动提取案情中的事故时间
 *  - 快速处理申请 = 手动录入申请日期
 *  - 鉴定委托     = 事故发生 + 3 个工作日
 *  - 鉴定报告出具 = 鉴定委托 + 30 个自然日
 *  - 鉴定报告送达 = 鉴定报告出具 + 5 个工作日
 *  - 鉴定报告生效 = 鉴定报告送达后第 4 个工作日（v1.77：送达当天不算，第 1~3 个工作日为异议申请期，第 4 个工作日生效）
 *  - 事故调查报告 = 鉴定报告生效后第 3 个工作日（即送达后第 7 个工作日）
 *  - 事故认定书出具 = 主线：生效起 5 个工作日内（含生效当天，最晚 = 生效后第 4 个工作日，即送达后第 8 个工作日）；快捷：事故发生 + 10 个工作日
 *  - 事故认定书送达 = 事故认定书出具 + 3 个工作日
 */
object AccidentFlowTemplate {

    /** 受案登记节点码（v1.74 事故主线已去除受案登记节点；常量保留兼容历史数据与违法流程引用） */
    const val REGISTER_CODE = "NR"

    /** 一般程序快速处理申请书节点码 */
    const val QUICK_PROCESS_CODE = "NQ"

    /** 鉴定委托节点码 */
    const val INVESTIGATE_CODE = "N1"

    /** 事故认定书出具节点码（v1.66：超期判定按此节点口径） */
    const val CERTIFICATE_CODE = "N5"

    /** 事故调查报告节点码（v1.76：超期不做文字提示，仅节点颜色提醒） */
    const val REPORT_CODE = "N4"

    /** 鉴定报告生效节点码（v1.77：送达后第 4 个工作日生效，生效后才能进入事故调查报告） */
    const val EFFECT_CODE = "N3E"

    /** 节点定义 */
    data class NodeDef(
        val code: String,
        val name: String,
        val hint: String,
        val isAuto: Boolean,
        val isRequired: Boolean
    )

    val NODES: List<NodeDef> = listOf(
        NodeDef("N0", "事故发生", "自动提取案情中的事故时间，也可手动修改", false, true),
        NodeDef(QUICK_PROCESS_CODE, "快速处理申请", "提交《一般程序快速处理申请书》后，10 个工作日内出具认定书，无需鉴定与事故调查报告", false, false),
        NodeDef("N1", "鉴定委托", "是否需要委托鉴定可在此选择", true, false),
        NodeDef("N2", "鉴定报告出具", "鉴定委托后 30 个自然日", true, false),
        NodeDef("N3", "鉴定报告送达", "鉴定报告出具后 5 个工作日；送达后第 4 个工作日生效", true, false),
        NodeDef("N4", "事故调查报告", "鉴定报告生效后第 3 个工作日（送达后第 7 个工作日）", true, false),
        NodeDef("N5", "事故认定书出具", "主线：生效起 5 个工作日内（含生效当天）；免鉴定/快速处理：事故发生 + 10 个工作日", true, false),
        NodeDef("N6", "事故认定书送达", "事故认定书出具后 3 个工作日", true, false),
    )

    /** 是否走"快捷出证"路径（免鉴定 或 已提交快速处理申请）：跳过 N1-N4，认定书 = 事故发生 + 10 工作日 */
    fun Case.isQuickCertificate(): Boolean = flowSkipInvestigation || flowQuickProcess

    /** 当前路径节点定义（主线 7 节点 / 快速处理 4 节点 / 免鉴定 3 节点），按节点码引用避免索引漂移 */
    fun pathNodes(case: Case): List<NodeDef> {
        fun pick(codes: List<String>): List<NodeDef> =
            codes.mapNotNull { code -> NODES.firstOrNull { it.code == code } }
        return when {
            case.flowQuickProcess -> pick(listOf("N0", QUICK_PROCESS_CODE, "N5", "N6"))
            case.flowSkipInvestigation -> pick(listOf("N0", "N5", "N6"))
            else -> NODES.filter { it.code != QUICK_PROCESS_CODE }
        }
    }

    /**
     * 计算某节点的预计日期（毫秒时间戳，当天 0 点）。
     *
     * v1.74 链式推算（主线）：每个环节「生效日期」= 实际日期优先，未录入则用该环节推算日期兜底，
     * 保证「事故认定书出具」无论中间节点是否录入都有确定日期（剩余天数直接显示）：
     *  - 鉴定报告出具 = 鉴定委托 + 30 个自然日
     *  - 鉴定报告送达 = 鉴定报告出具 + 5 个工作日
     *  - 鉴定报告生效（N3E） = 鉴定报告送达后第 4 个工作日（v1.77：送达当天不算，第 1~3 个工作日为异议期）
     *  - 事故调查报告 = 鉴定报告生效后第 3 个工作日（送达后第 7 个工作日）
     *  - 事故认定书出具 = 主线：生效起 5 个工作日内（含生效当天，最晚 = 生效后第 4 个工作日，即送达后第 8 个工作日）；快捷：事故发生 + 10 个工作日
     *  - 事故认定书送达 = 事故认定书出具 + 3 个工作日
     *
     * @param actualByCode 已录入节点的实际日期（code -> 时间戳）
     */
    fun expectedDateFor(
        code: String,
        actualByCode: Map<String, Long>,
        case: Case
    ): Long {
        val accidentDate = CalendarUtil.toLocalDate(case.accidentDate.takeIf { it > 0 } ?: return 0L)

        // 节点生效日期：实际录入日期优先；否则沿链条向前取最近锚点，按后续环节最长法定时限逐级推算
        fun effDate(nodeCode: String): LocalDate? {
            actualByCode[nodeCode]?.takeIf { it > 0 }?.let { return CalendarUtil.toLocalDate(it) }
            return when (nodeCode) {
                "N0" -> accidentDate
                // 鉴定委托 = 事故 + 3 工作日
                "N1" -> effDate("N0")?.let { CalendarUtil.addWorkDays(it, 3) }
                // 鉴定报告出具 = 委托 + 30 自然日
                "N2" -> effDate("N1")?.let { CalendarUtil.addNaturalDays(it, 30) }
                // 鉴定报告送达 = 出具 + 5 工作日
                "N3" -> effDate("N2")?.let { CalendarUtil.addWorkDays(it, 5) }
                // 鉴定报告生效 = 送达后第 4 个工作日（v1.77：送达当天不算，第 1~3 个工作日为异议申请期，第 4 个工作日生效）
                EFFECT_CODE -> effDate("N3")?.let { CalendarUtil.addWorkDays(it, 4) }
                // 事故调查报告 = 生效后第 3 个工作日（生效当天不算）
                "N4" -> effDate(EFFECT_CODE)?.let { CalendarUtil.addWorkDays(it, 3) }
                // 事故认定书出具 = 主线：生效起 5 个工作日内（含生效当天，最晚 = 生效后第 4 个工作日）；快捷：事故 + 10 工作日
                "N5" -> if (case.isQuickCertificate()) {
                    effDate("N0")?.let { CalendarUtil.addWorkDays(it, 10) }
                } else {
                    effDate(EFFECT_CODE)?.let { CalendarUtil.addWorkDays(it, 4) }
                }
                // 事故认定书送达 = 出具 + 3 工作日
                "N6" -> effDate("N5")?.let { CalendarUtil.addWorkDays(it, 3) }
                else -> null
            }
        }
        return effDate(code)?.let { CalendarUtil.toEpochMillis(it) } ?: 0L
    }

    /**
     * 构建当前路径节点列表（结合已存在的事件）。
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
        // v1.77 生效门禁：事故调查报告（N4）须等鉴定报告送达（N3）后第 4 个工作日生效，生效后才能进入下一进程
        if (nodeCode == "N4") {
            val n3 = nodes.firstOrNull { it.code == "N3" }
            if (n3 != null) {
                val n3Date = n3.event?.occurredAt?.takeIf { it > 0 }
                    ?: n3.expectedDate.takeIf { it > 0 }
                    ?: 0L
                if (n3Date > 0L) {
                    val effectDate = CalendarUtil.toEpochMillis(
                        CalendarUtil.addWorkDays(CalendarUtil.toLocalDate(n3Date), 4)
                    )
                    if (System.currentTimeMillis() < effectDate) {
                        return false to "鉴定报告送达后第 4 个工作日（${CalendarUtil.toLocalDate(effectDate)}）生效，生效后方可进入事故调查报告"
                    }
                }
            }
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

/** 是否已选择免鉴定或快速处理（快捷出证） */
fun Case.isQuickCertificate(): Boolean = flowSkipInvestigation || flowQuickProcess

/** 派生状态文字：已认定 / 处理中（v1.74 事故去除受案登记后不再有待受案；违法由调用方按 VR 自行判定） */
fun Case.derivedStatusText(registered: Boolean): String = when {
    status == CaseStatus.CLOSED -> "已认定"
    registered -> "处理中"
    else -> "待受案"
}
