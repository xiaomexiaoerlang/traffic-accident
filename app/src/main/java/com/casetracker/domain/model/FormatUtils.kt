package com.casetracker.domain.model

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 显示格式工具：中文日期 / 事故时间 / 案件名称自动生成
 */
object FormatUtils {

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
     */
    fun buildCaseTitle(dateTs: Long, firstParty: String, caseType: CaseType): String {
        val datePart = chineseDate(dateTs).ifBlank { "未注明日期" }
        val party = firstParty.trim()
        return when (caseType) {
            CaseType.ACCIDENT ->
                if (party.isNotBlank()) "${datePart}${party}道路交通事故"
                else "${datePart}道路交通事故"
            CaseType.VIOLATION ->
                if (party.isNotBlank()) "${datePart}${party}交通违法案件"
                else "${datePart}交通违法案件"
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
}
