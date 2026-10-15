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
    val versionName: String = "v${com.casetracker.BuildConfig.VERSION_NAME} (${com.casetracker.BuildConfig.VERSION_CODE})",
    val showThemeDialog: Boolean = false,
    val showClearConfirm: Boolean = false,
    val showLegalDialog: Boolean = false,
    val message: String? = null,
)

sealed interface SettingsUiEvent {
    data object OpenThemeDialog : SettingsUiEvent
    data object DismissThemeDialog : SettingsUiEvent
    data class SetThemeMode(val mode: SettingsManager.ThemeMode) : SettingsUiEvent
    data object OpenClearConfirm : SettingsUiEvent
    data object DismissClearConfirm : SettingsUiEvent
    data object ClearAll : SettingsUiEvent
    data object ShowLegal : SettingsUiEvent
    data object DismissLegal : SettingsUiEvent
    data object ExportCsv : SettingsUiEvent
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
            is SettingsUiEvent.OpenClearConfirm ->
                _uiState.value = _uiState.value.copy(showClearConfirm = true)
            is SettingsUiEvent.DismissClearConfirm ->
                _uiState.value = _uiState.value.copy(showClearConfirm = false)
            is SettingsUiEvent.ClearAll -> clearAll()
            is SettingsUiEvent.ShowLegal ->
                _uiState.value = _uiState.value.copy(showLegalDialog = true)
            is SettingsUiEvent.DismissLegal ->
                _uiState.value = _uiState.value.copy(showLegalDialog = false)
            is SettingsUiEvent.ExportCsv -> exportCsv()
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
            val result = withContext(Dispatchers.IO) { writeCsv(appContext, cases) }
            _uiState.value = _uiState.value.copy(
                message = if (result.isBlank()) "导出失败" else "已导出：$result"
            )
        }
    }

    private fun writeCsv(context: Context, cases: List<Case>): String {
        val header = "事故编号,事故日期,事故时间,事故类型,处理程序,事故地点,处理单位,办案民警,本方当事人,对方当事人,是否逃逸,鉴定延期,状态,进度(%)"
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val sb = StringBuilder(header)
        cases.forEach { c ->
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
                    if (c.hitAndRun) "是" else "否",
                    if (c.appraisalExtended) "是" else "否",
                    c.status.displayName,
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

/** 法律依据文本 */
val LEGAL_BASIS: List<Pair<String, String>> = listOf(
    "《道路交通安全法》第73条" to "公安机关交通管理部门应当根据交通事故现场勘验、检查、调查情况和有关的检验、鉴定结论，及时制作交通事故认定书，作为处理交通事故的证据。",
    "《道路交通事故处理程序规定》第29条" to "交通肇事逃逸案件尚未侦破的，受害方书面要求出具道路交通事故认定书的，公安机关交通管理部门应当在接到书面要求后十日内制作道路交通事故认定书。",
    "《道路交通事故处理程序规定》第30条" to "对经过勘验、检查的交通事故现场，公安机关交通管理部门应当自勘查现场之日起五日内制作道路交通事故认定书。",
    "《道路交通事故处理程序规定》第37条" to "公安机关交通管理部门应当自现场调查之日起五日内指派或者委托专业技术人员、具备资格的鉴定机构进行检验、鉴定。",
    "《道路交通事故处理程序规定》第38条" to "检验、鉴定应当在二十日内完成；需要延期的，经设区的市公安机关交通管理部门批准可以延长二十日。",
    "《道路交通事故处理程序规定》第43条" to "公安机关交通管理部门应当自制作道路交通事故认定书之日起三日内送达当事人。",
    "《道路交通事故处理程序规定》第51条" to "当事人对道路交通事故认定有异议的，可以自道路交通事故认定书送达之日起三日内，向作出道路交通事故认定的公安机关交通管理部门提出书面复核申请。",
    "《道路交通事故处理程序规定》第53条" to "上一级公安机关交通管理部门收到当事人书面复核申请后，应当自受理之日起三十日内作出复核结论。"
)
