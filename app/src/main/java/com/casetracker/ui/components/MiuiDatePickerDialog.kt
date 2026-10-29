package com.casetracker.ui.components

import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * Material 3 日期选择对话框。
 *
 * @param initialMillis 初始选中日期（本地时区毫秒，可空）
 * @param onDateSelected 确认选择后回调，返回本地时区当日 0 点的毫秒与 "yyyy-MM-dd" 文本
 * @param onDismiss 取消/关闭
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MiuiDatePickerDialog(
    initialMillis: Long?,
    onDateSelected: (millis: Long, text: String) -> Unit,
    onDismiss: () -> Unit
) {
    // DatePicker 内部以 UTC 午夜表示日期，将本地毫秒换算为 UTC 毫秒作为初始值
    val initialUtc = initialMillis?.let { localToUtcMidnight(it) }
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initialUtc,
        yearRange = 2000..2035
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    state.selectedDateMillis?.let { utc ->
                        val local = utcMidnightToLocal(utc)
                        val text = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                            .format(java.util.Date(local))
                        onDateSelected(local, text)
                    }
                    onDismiss()
                }
            ) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    ) {
        DatePicker(state = state)
    }
}

/** 本地毫秒 -> UTC 午夜毫秒（供 DatePicker 初始值） */
private fun localToUtcMidnight(localMillis: Long): Long {
    val cal = Calendar.getInstance().apply {
        timeInMillis = localMillis
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    utc.set(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH), 0, 0, 0)
    utc.set(Calendar.MILLISECOND, 0)
    return utc.timeInMillis
}

/** UTC 午夜毫秒 -> 本地当日 0 点毫秒 */
private fun utcMidnightToLocal(utcMillis: Long): Long {
    val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcMillis }
    val local = Calendar.getInstance()
    local.set(
        utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH),
        0, 0, 0
    )
    local.set(Calendar.MILLISECOND, 0)
    return local.timeInMillis
}
