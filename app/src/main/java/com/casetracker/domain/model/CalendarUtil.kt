package com.casetracker.domain.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

/**
 * 日期计算工具：工作日/自然日跟随真实日历。
 * 工作日规则：跳过周六、周日（法定节假日如需精确计算，可在此追加节假日表）。
 */
object CalendarUtil {

    private const val DAY_MS = 24L * 60 * 60 * 1000
    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")

    /** 时间戳 → 当天 0 点时间戳 */
    fun startOfDay(ts: Long): Long = ts / DAY_MS * DAY_MS

    /** 时间戳 → LocalDate */
    fun toLocalDate(ts: Long): LocalDate =
        LocalDate.ofEpochDay(ts / DAY_MS)

    /** LocalDate → 当天 0 点时间戳 */
    fun toEpochMillis(date: LocalDate): Long =
        date.atStartOfDay(zone).toInstant().toEpochMilli()

    /** 加 N 个自然日（含周末节假日不跳过） */
    fun addNaturalDays(date: LocalDate, days: Int): LocalDate =
        date.plusDays(days.toLong())

    /** 加 N 个工作日（跳过周六周日） */
    fun addWorkDays(date: LocalDate, days: Int): LocalDate {
        var result = date
        var remaining = days
        while (remaining > 0) {
            result = result.plusDays(1)
            if (result.dayOfWeek != DayOfWeek.SATURDAY && result.dayOfWeek != DayOfWeek.SUNDAY) {
                remaining--
            }
        }
        return result
    }

    /** 是否为工作日（周一~周五） */
    fun isWorkDay(date: LocalDate): Boolean =
        date.dayOfWeek != DayOfWeek.SATURDAY && date.dayOfWeek != DayOfWeek.SUNDAY

    /** 是否为周末 */
    fun isWeekend(date: LocalDate): Boolean = !isWorkDay(date)
}
