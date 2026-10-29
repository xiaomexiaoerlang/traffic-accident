package com.casetracker.domain.model

/**
 * v1.82 办案节点清单模板：每个流程节点预置「应办事项」勾选清单与「关注点」提示。
 * 案件详情页通过右滑抽屉展示，勾选状态按案件独立保存在 checklist_items 表。
 * 清单内容参照《道路交通事故处理程序规定》《行政处罚法》《刑事诉讼法》等编写。
 */
object CheckListTemplates {

    data class NodeCheckList(
        val items: List<String>,
        val tip: String
    )

    /** 事故流程清单（8 节点） */
    val ACCIDENT: Map<String, NodeCheckList> = mapOf(
        "N0" to NodeCheckList(
            listOf("记录事故时间、地点", "记录双方车辆与当事人信息", "拍摄现场照片、登记车辆损失", "伤人事故记录伤情"),
            "是否走快捷出证在此选择；鉴定委托限 3 个工作日"
        ),
        "N1" to NodeCheckList(
            listOf("委托有资质的鉴定机构", "填写委托书、附事故材料", "留存委托回执"),
            "事故 + 3 个工作日内完成委托；涉人伤 / 车损争议大时必委托"
        ),
        "N2" to NodeCheckList(
            listOf("接收鉴定报告", "核对结论（车辆检验 / 车速 / 酒检等）"),
            "委托 + 30 个自然日；对结论有异议可申请重新鉴定"
        ),
        "N3" to NodeCheckList(
            listOf("送达当事人并签收", "记录送达时间、方式", "确认异议期内无重新鉴定申请", "第 4 个工作日登记生效日期"),
            "送达当天不算，第 1~3 个工作日为异议期，第 4 个工作日生效"
        ),
        "N3E" to NodeCheckList(
            listOf("确认异议期满无异议", "记录生效日期"),
            "生效后才可进入事故调查报告"
        ),
        "N4" to NodeCheckList(
            listOf("汇总现场图、笔录、鉴定等材料", "完成事故成因分析", "形成事故调查报告"),
            "生效后 3 个工作日内出具；超期仅节点颜色提醒"
        ),
        "N5" to NodeCheckList(
            listOf("制作事故认定书", "载明事实、成因、责任划分"),
            "生效起 5 个工作日内（含生效当天）"
        ),
        "N6" to NodeCheckList(
            listOf("送达各方当事人并签收", "告知 3 日内可申请复核"),
            "认定书 + 3 个工作日；送达后可标记已认定"
        )
    )

    /** 违法流程——行政程序清单（V0 违法查处自动完成，不列入勾选） */
    val VIOLATION_ADMIN: Map<String, NodeCheckList> = mapOf(
        "VR" to NodeCheckList(
            listOf("录入受案登记日期", "留存受案登记表"),
            "查获后应及时受理并制作受案登记"
        ),
        "V1" to NodeCheckList(
            listOf("审查是否属本单位管辖", "决定立案并记录"),
            ""
        ),
        "V2" to NodeCheckList(
            listOf("询问当事人 / 证人", "调取监控、酒精检测等证据", "制作笔录"),
            "立案后 15 个工作日内完成调查取证"
        ),
        "V3" to NodeCheckList(
            listOf("告知拟处罚内容、理由、依据", "告知陈述申辩权利"),
            ""
        ),
        "V4" to NodeCheckList(
            listOf("听取陈述申辩并复核", "制作处罚决定书"),
            "告知后 3 个工作日（含复核）"
        ),
        "V5" to NodeCheckList(
            listOf("送达并签收", "记录送达方式"),
            "决定后 7 日内送达"
        ),
        "V6" to NodeCheckList(
            listOf("督促缴纳罚款 / 执行到位", "记录执行结果"),
            "送达后 15 日内履行"
        )
    )

    /** 违法流程——刑事程序清单（醉驾等涉嫌犯罪） */
    val VIOLATION_CRIMINAL: Map<String, NodeCheckList> = mapOf(
        "VR" to NodeCheckList(
            listOf("录入受案登记日期", "留存受案登记表"),
            "查获后应及时受理并制作受案登记"
        ),
        "VC1" to NodeCheckList(
            listOf("受案后审查是否立案", "制作立案决定书"),
            "受案后 3 日内决定（刑诉法 112 条）"
        ),
        "VC2" to NodeCheckList(
            listOf("酒精检验、血液送检", "讯问、调取证据"),
            "血检结果直接影响定罪标准（80mg/100ml）"
        ),
        "VC3" to NodeCheckList(
            listOf("整理卷宗、形成起诉意见书"),
            "羁押期限一般不超过 2 个月"
        ),
        "VC4" to NodeCheckList(
            listOf("案件及证据移送检察院"),
            "侦查终结后移送"
        )
    )

    // ===== v1.84 事故台账式流程清单（10 节点，点左侧圆点 = 完成）=====
    val ACCIDENT_LEDGER: List<String> = listOf(
        "事故受案",
        "现场勘察信息录入",
        "委托鉴定",
        "材料上传",
        "鉴定报告送达",
        "物品返还",
        "调查报告书",
        "事故认定书",
        "事故认定送达",
        "违法行为处罚"
    )

    /** 按案件类型取对应清单模板 */
    fun templateFor(case: Case): Map<String, NodeCheckList> = when (case.caseType) {
        CaseType.ACCIDENT -> ACCIDENT
        CaseType.VIOLATION ->
            if (case.violationType == ViolationType.CRIMINAL) VIOLATION_CRIMINAL else VIOLATION_ADMIN
    }

    // ===== v1.83/v1.85 案件专属板块 =====
    // 案卷目录双版本：日常案件版（默认，12 项）+ 执法考评版（31 项）；勾选 = 已归档入卷
    const val VOLUME_DAILY_CODE = "VOL_D"
    const val VOLUME_APPRAISAL_CODE = "VOL_A"
    const val VOLUME_TITLE = "道路交通事故案卷目录"
    // v1.85 日常案件版（《交通事故案卷目录》12 项）
    val ACCIDENT_VOLUME_DAILY: List<String> = listOf(
        "受理交通事故案件登记表、受案回执",
        "道路交通现场勘查笔录",
        "道路交通事故现场图",
        "道路交通事故现场照片",
        "公安交通管理行政强制措施凭证",
        "物品返还凭证",
        "当事人驾驶证、行驶证、身份证复印件等",
        "当事人陈述、询问笔录（探视记录）",
        "道路交通事故认定书",
        "公安交通管理行政告知笔录、公安交通管理行政处罚决定书",
        "送达回执",
        "公安网上查询记录单（接处警单、归案经过等材料）"
    )
    // v1.83 执法考评版（《道路交通事故案卷目录（正卷）》31 项）
    val ACCIDENT_VOLUME_APPRAISAL: List<String> = listOf(
        "受案登记表、受案回执",
        "接受证据清单",
        "道路交通事故现场勘查笔录",
        "道路交通事故现场图",
        "道路交通事故照片",
        "公安交通管理行政强制措施凭证",
        "返还物品凭证",
        "当事人身份证明、驾驶证、行驶证复印件和查询记录等其他材料",
        "声像资料提取登记表",
        "道路交通事故视听材料目录",
        "当事人陈述、询问笔录（探视记录）",
        "检验鉴定委托书",
        "检验鉴定报告",
        "领取车辆通知书",
        "道路交通事故认定书",
        "道路交通安全违法行为处理通知书",
        "行政处罚告知笔录",
        "公安行政处罚决定书",
        "道路交通事故抢救费支付（垫付）通知书",
        "道路交通事故处理法律文书送达地址确认书",
        "道路交通事故处理送达文书回执",
        "伤人事故快速处理申请书",
        "其他行政法律文书",
        "各种呈请审批报告",
        "道路交通事故调查报告",
        "道路交通事故集体研究意见",
        "道路交通事故专家会商意见",
        "道路交通事故处理工作记录",
        "道路交通事故公开证据记录",
        "道路交通事故深度调查报告、消除隐患通知书",
        "其他文书材料"
    )

    // 醉驾（刑事违法）案件抽屉：醉驾案件材料审核表，相关材料 24 项 + 办案系统 3 项
    const val DRUNK_MATERIAL_CODE = "ZC_M"
    const val DRUNK_SYSTEM_CODE = "ZC_S"
    const val DRUNK_TITLE = "醉驾案件材料审核"
    val DRUNK_MATERIALS: List<String> = listOf(
        "接警单",
        "受案登记表、受案回执",
        "立案决定书、立案告知书",
        "当事人户籍信息、相关证人户籍信息",
        "到案经过",
        "当事人询问笔录、权利义务告知书",
        "证人询问笔录、证人权利义务告知书",
        "现场示意图、现场勘查笔录或检查笔录、现场照片",
        "酒精呼气测试单",
        "现场执法视频、抽血视频及视频说明",
        "强制措施审批表、抽血强凭",
        "血液样本提取笔录",
        "鉴定聘请书、血样鉴定意见书、鉴定意见通知书及送达回执",
        "驾驶证、行驶证、车辆信息查询单及复印件",
        "车辆鉴定聘请书、委托书、车辆鉴定意见书、送达回执/鉴定意见通知书",
        "伤情鉴定聘请书、委托书、伤情鉴定意见书、送达回执/鉴定意见通知书",
        "事故认定书及调解协议",
        "前科查询",
        "限制办证处罚决定书或者吊销驾驶证处罚决定书",
        "案件是否超期受案、立案（如超期需要说明）",
        "相关材料的接受证据清单、调取证据通知书及清单",
        "是否构成共同犯罪",
        "神眼搜车系统行车轨迹截图打印",
        "酒精测试仪的检验鉴定证书"
    )
    val DRUNK_SYSTEM: List<String> = listOf(
        "案件网上是否移交",
        "办案系统材料是否上传",
        "当事人询问时是否录入办案区执法办案平台"
    )

    /** 取节点名称（用于抽屉节点标题），无则返回节点码 */
    fun nodeNameOf(case: Case, code: String): String = when (case.caseType) {
        CaseType.ACCIDENT ->
            AccidentFlowTemplate.pathNodes(case).firstOrNull { it.code == code }?.name ?: code
        CaseType.VIOLATION ->
            ViolationFlowTemplate.pathNodes(case).firstOrNull { it.code == code }?.name ?: code
        else -> code
    }
}
