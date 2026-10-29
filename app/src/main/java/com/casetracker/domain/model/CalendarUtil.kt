package com.casetracker.domain.model

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 日期计算工具：工作日/自然日跟随真实日历。
 * 工作日规则：跳过周六、周日与法定节假日；调休上班日（周末补班）按工作日计算。
 * 节假日数据见 ChinaHoliday（收录 2023-2026 年国务院公布安排，其余年份仅跳过周末）。
 *
 * v1.75 时区修正：时间戳统一按 Asia/Shanghai 本地时区换算日期——
 * 案件日期（parseDate 存本地 0 点）与节点实际日期（完成时刻）此前被 toLocalDate
 * 按 UTC 整除换算，恒比上海日期早 1 天，导致整条时间线推算偏早 1 天，现已修正。
 */
object CalendarUtil {

    private const val DAY_MS = 24L * 60 * 60 * 1000
    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")

    /** 时间戳 → 当天 0 点时间戳 */
    fun startOfDay(ts: Long): Long = ts / DAY_MS * DAY_MS

    /** 时间戳 → 本地（Asia/Shanghai）日期（v1.75 修正：按本地时区而非 UTC 整除） */
    fun toLocalDate(ts: Long): LocalDate =
        Instant.ofEpochMilli(ts).atZone(zone).toLocalDate()

    // ===== v1.85 台账导入日期解析 =====

    /** 解析事故时间文本（"2026.9.2" / "2025-12-2" / 数字序列号）→ 当天 0 点时间戳；失败返回 0 */
    fun parseLedgerDate(text: String): Long {
        val t = text.trim()
        if (t.isBlank()) return 0L
        // Excel 日期序列号（如 45000）→ 1899-12-30 + N 天
        val num = t.toDoubleOrNull()
        if (num != null && num > 20000 && num < 60000) {
            return toEpochMillis(LocalDate.of(1899, 12, 30).plusDays(num.toLong()))
        }
        val parts = t.split('.', '-', '/').map { it.trim() }.filter { it.isNotBlank() }
        if (parts.size < 3) return 0L
        val year = parts[0].toIntOrNull() ?: return 0L
        val month = parts[1].toIntOrNull() ?: return 0L
        val day = parts[2].toIntOrNull() ?: return 0L
        return try {
            toEpochMillis(LocalDate.of(year, month, day))
        } catch (_: Exception) {
            0L
        }
    }

    /** 台账流程列的月.日（"1.8"）→ 补年份：早于事故日期按次年、否则当年；非日期返回 0 */
    fun resolveMonthDay(accidentDateMs: Long, value: String): Long {
        val t = value.trim()
        if (t.isBlank()) return 0L
        val parts = t.split('.', '-', '/').map { it.trim() }.filter { it.isNotBlank() }
        if (parts.size < 2) return 0L
        val month = parts[0].toIntOrNull() ?: return 0L
        val day = parts[1].toIntOrNull() ?: return 0L
        val accident = toLocalDate(accidentDateMs)
        var date = LocalDate.of(accident.year, month, day)
        // 早于（或等于）事故日期 → 次年（台账流程日期均在事故之后；同年同日视为事故当日归属当年）
        if (!date.isAfter(accident)) date = date.plusYears(1)
        return toEpochMillis(date)
    }

    /** LocalDate → 当天 0 点时间戳 */
    fun toEpochMillis(date: LocalDate): Long =
        date.atStartOfDay(zone).toInstant().toEpochMilli()

    /** 加 N 个自然日（含周末节假日不跳过） */
    fun addNaturalDays(date: LocalDate, days: Int): LocalDate =
        date.plusDays(days.toLong())

    /** 加 N 个工作日（跳过周末与法定节假日，调休上班日照常计算）
     *  语义：自 date 之**次日**起数 N 个工作日，date 当天不计入（"之日起不包括当天"） */
    fun addWorkDays(date: LocalDate, days: Int): LocalDate {
        var result = date
        var remaining = days
        while (remaining > 0) {
            result = result.plusDays(1)
            if (isWorkDay(result)) {
                remaining--
            }
        }
        return result
    }

    /** 是否为工作日（周一~周五且非法定节假日，或调休上班日） */
    fun isWorkDay(date: LocalDate): Boolean =
        ChinaHoliday.isMakeupWorkday(date) ||
            (date.dayOfWeek != DayOfWeek.SATURDAY &&
                date.dayOfWeek != DayOfWeek.SUNDAY &&
                !ChinaHoliday.isHoliday(date))

    /** 是否为休息日（周末或法定节假日，调休上班日除外） */
    fun isWeekend(date: LocalDate): Boolean = !isWorkDay(date)

    /** 两个时间戳之间的自然日天数差（to - from，精确到日；同一天为 0，未来为正） */
    fun daysBetween(from: Long, to: Long): Int =
        (toLocalDate(to).toEpochDay() - toLocalDate(from).toEpochDay()).toInt()
}
