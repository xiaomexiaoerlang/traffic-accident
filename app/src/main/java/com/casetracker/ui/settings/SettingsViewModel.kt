package com.casetracker.ui.settings

import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.casetracker.data.SettingsManager
import com.casetracker.data.repository.CaseRepository
import com.casetracker.domain.model.Case
import com.casetracker.domain.model.CaseStatus
import com.casetracker.domain.model.CaseType
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class SettingsUiState(
    val themeMode: SettingsManager.ThemeMode = SettingsManager.ThemeMode.SYSTEM,
    val hapticsEnabled: Boolean = true,
    val versionName: String = "v${com.casetracker.BuildConfig.VERSION_NAME} (${com.casetracker.BuildConfig.VERSION_CODE})",
    val showThemeDialog: Boolean = false,
    val showClearConfirm: Boolean = false,
    val importing: Boolean = false,
    val message: String? = null,
)

sealed interface SettingsUiEvent {
    data object OpenThemeDialog : SettingsUiEvent
    data object DismissThemeDialog : SettingsUiEvent
    data class SetThemeMode(val mode: SettingsManager.ThemeMode) : SettingsUiEvent
    data class ToggleHaptics(val enabled: Boolean) : SettingsUiEvent
    data object OpenClearConfirm : SettingsUiEvent
    data object DismissClearConfirm : SettingsUiEvent
    data object ClearAll : SettingsUiEvent
    data object ExportCsv : SettingsUiEvent
    data class ImportLedger(val uri: android.net.Uri) : SettingsUiEvent
    data object ConsumeMessage : SettingsUiEvent
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: CaseRepository,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            SettingsManager.themeMode.collect { mode ->
                _uiState.value = _uiState.value.copy(themeMode = mode)
            }
        }
        viewModelScope.launch {
            SettingsManager.hapticsEnabled.collect { enabled ->
                _uiState.value = _uiState.value.copy(hapticsEnabled = enabled)
            }
        }
    }

    fun onEvent(event: SettingsUiEvent) {
        when (event) {
            is SettingsUiEvent.OpenThemeDialog ->
                _uiState.value = _uiState.value.copy(showThemeDialog = true)
            is SettingsUiEvent.DismissThemeDialog ->
                _uiState.value = _uiState.value.copy(showThemeDialog = false)
            is SettingsUiEvent.SetThemeMode -> {
                SettingsManager.setThemeMode(appContext, event.mode)
                _uiState.value = _uiState.value.copy(showThemeDialog = false)
            }
            is SettingsUiEvent.ToggleHaptics ->
                SettingsManager.setHapticsEnabled(appContext, event.enabled)
            is SettingsUiEvent.OpenClearConfirm ->
                _uiState.value = _uiState.value.copy(showClearConfirm = true)
            is SettingsUiEvent.DismissClearConfirm ->
                _uiState.value = _uiState.value.copy(showClearConfirm = false)
            is SettingsUiEvent.ClearAll -> clearAll()
            is SettingsUiEvent.ExportCsv -> exportCsv()
            is SettingsUiEvent.ImportLedger -> importLedger(event.uri)
            is SettingsUiEvent.ConsumeMessage ->
                _uiState.value = _uiState.value.copy(message = null)
        }
    }

    private fun clearAll() {
        viewModelScope.launch {
            repository.clearAll()
            _uiState.value = _uiState.value.copy(
                showClearConfirm = false,
                message = "已清除全部数据"
            )
        }
    }

    private fun exportCsv() {
        viewModelScope.launch {
            val cases = repository.getAllCases().first()
            val registered = repository.getRegisteredCaseIds().first()
            val result = withContext(Dispatchers.IO) { writeCsv(appContext, cases, registered) }
            _uiState.value = _uiState.value.copy(
                message = if (result.isBlank()) "导出失败" else "已导出：$result"
            )
        }
    }

    /** v1.85 事故台账批量导入：解析 .xlsx → 生成已认定事故 */
    private fun importLedger(uri: android.net.Uri) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(importing = true)
            val result = withContext(Dispatchers.IO) {
                try {
                    val rows = appContext.contentResolver.openInputStream(uri)?.use { stream ->
                        com.casetracker.data.importer.AccidentLedgerImporter.parse(stream)
                    } ?: emptyList()
                    if (rows.isEmpty()) {
                        "未识别到有效台账数据（需含事故时间列）"
                    } else {
                        val (imported, skipped) = repository.importAccidentLedger(rows)
                        "导入完成：成功 $imported 条，跳过重复 $skipped 条"
                    }
                } catch (e: Exception) {
                    "导入失败：${e.message ?: "文件解析错误"}"
                }
            }
            _uiState.value = _uiState.value.copy(importing = false, message = result)
        }
    }

    private fun writeCsv(context: Context, cases: List<Case>, registered: Set<Long>): String {
        val header = "事故编号,事故日期,事故时间,事故类型,处理程序,事故地点,处理单位,办案民警,本方当事人,对方当事人,伤情情况,是否逃逸,鉴定延期,状态,进度(%)"
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val sb = StringBuilder(header)
        cases.forEach { c ->
            // 状态跟随流程：事故 v1.74 去除受案登记后恒为处理中；违法按受案登记状态
            val statusText = when {
                c.status == CaseStatus.CLOSED -> "已认定"
                c.caseType == CaseType.ACCIDENT || c.id in registered -> "处理中"
                else -> "待受案"
            }
            sb.append('\n').append(
                listOf(
                    c.caseNumber,
                    if (c.accidentDate > 0) dateFormat.format(Date(c.accidentDate)) else "",
                    c.accidentTime,
                    c.accidentType.displayName,
                    c.procedure.displayName,
                    c.location,
                    c.handlerUnit,
                    c.handlingOfficer,
                    c.selfParty,
                    c.otherParty,
                    c.injuryDetail,
                    if (c.hitAndRun) "是" else "否",
                    if (c.appraisalExtended) "是" else "否",
                    statusText,
                    c.progress.toString()
                ).map { it.replace(",", "，") }.joinToString(",")
            )
        }

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val values = android.content.ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, "事故数据_${System.currentTimeMillis()}.csv")
                    put(MediaStore.Downloads.MIME_TYPE, "text/csv")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: return "导出失败"
                resolver.openOutputStream(uri)?.use { out ->
                    OutputStreamWriter(out).use { it.write(sb.toString()) }
                }
                "下载/事故数据_${System.currentTimeMillis()}.csv"
            } else {
                val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                    ?: return "当前系统版本请使用 Android 10 及以上导出"
                val file = java.io.File(dir, "事故数据_${System.currentTimeMillis()}.csv")
                file.writeText(sb.toString())
                "应用目录/${file.name}"
            }
        } catch (e: Exception) {
            "导出失败：${e.message}"
        }
    }

    companion object {
    }
}
