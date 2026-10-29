package com.casetracker.domain.model

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 显示格式工具：中文日期 / 事故时间 / 案件名称自动生成
 */
object FormatUtils {

    /** v1.81 醉驾关键词：命中任一 → 案件名称「某某涉嫌危险驾驶案」 */
    private val DRUNK_KEYWORDS = listOf("醉酒", "醉驾")

    /** v1.81 无证驾驶关键词：命中任一 → 案件名称「某某无证驾驶案」 */
    private val NO_LICENSE_KEYWORDS = listOf(
        "无证驾驶", "未取得驾驶证", "未取得机动车驾驶证", "无驾驶证", "未取得驾驶资格"
    )

    /** 时间戳 → "2026年9月10日" */
    fun chineseDate(ts: Long): String {
        if (ts <= 0) return ""
        val cal = Calendar.getInstance()
        cal.timeInMillis = ts
        return "${cal.get(Calendar.YEAR)}年${cal.get(Calendar.MONTH) + 1}月${cal.get(Calendar.DAY_OF_MONTH)}日"
    }

    /** 事故日期+时间 → "2026年9月10日5时10分许" */
    fun accidentDateTime(dateTs: Long, timeText: String): String {
        val datePart = chineseDate(dateTs)
        val timePart = timeText.trim()
        return when {
            datePart.isBlank() && timePart.isBlank() -> ""
            datePart.isBlank() -> timePart
            timePart.isBlank() -> datePart
            else -> "$datePart$timePart"
        }
    }

    /** 简短日期 "2026-09-10" */
    fun shortDate(ts: Long): String =
        if (ts > 0) SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(ts)) else ""

    /**
     * 自动生成案件名称："2026年9月10日张三道路交通事故"
     * @param dateTs 事故日期时间戳
     * @param firstParty 第一当事人（无则省略）
     * @param caseType 案件类型（事故/违法）
     * @param description 描述原文（v1.81：用于判断醉驾/无证驾驶等特殊违法名称）
     */
    fun buildCaseTitle(dateTs: Long, firstParty: String, caseType: CaseType, description: String = ""): String {
        val datePart = chineseDate(dateTs).ifBlank { "未注明日期" }
        val party = firstParty.trim()
        return when (caseType) {
            CaseType.ACCIDENT ->
                if (party.isNotBlank()) "${datePart}${party}道路交通事故"
                else "${datePart}道路交通事故"
            CaseType.VIOLATION -> {
                // v1.81 特殊违法名称（不带日期）：醉驾 →「某某涉嫌危险驾驶案」；无证驾驶 →「某某无证驾驶案」
                when {
                    DRUNK_KEYWORDS.any { description.contains(it) } && party.isNotBlank() ->
                        "${party}涉嫌危险驾驶案"
                    NO_LICENSE_KEYWORDS.any { description.contains(it) } && party.isNotBlank() ->
                        "${party}无证驾驶案"
                    party.isNotBlank() -> "${datePart}${party}交通违法案件"
                    else -> "${datePart}交通违法案件"
                }
            }
        }
    }

    /** 自动生成案号："SG-20260910-143025"（事故+日期+时间戳） */
    fun buildCaseNumber(dateTs: Long, now: Long = System.currentTimeMillis()): String {
        val datePart = if (dateTs > 0)
            SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date(dateTs))
        else SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date(now))
        val timePart = SimpleDateFormat("HHmmss", Locale.getDefault()).format(Date(now))
        return "SG-$datePart-$timePart"
    }

    /**
     * v1.76 统一案件显示名称：事故案件统一按「事故日期 + 当事人 + 道路交通事故」自动生成
     * （与新建案件名称生成规则一致，存量案件展示也走此格式）；违法案件保持原标题。
     * v1.81 事故案件支持长按自定义名称：已自定义（title 非空且与自动生成不同）时显示自定义名称。
     */
    fun unifiedTitle(c: Case): String {
        val auto = if (c.caseType == CaseType.ACCIDENT) {
            val party = c.parties.firstOrNull { it.isNotBlank() } ?: ""
            buildCaseTitle(c.accidentDate, party, CaseType.ACCIDENT)
        } else ""
        return when {
            c.caseType == CaseType.ACCIDENT && c.title.isNotBlank() && c.title != auto -> c.title
            c.caseType == CaseType.ACCIDENT -> auto
            else -> c.title
        }
    }
}
