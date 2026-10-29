package com.casetracker.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 全局设置管理器（深色模式、振触感反馈、办案民警记忆等） */
object SettingsManager {

    enum class ThemeMode(val displayName: String) { SYSTEM("跟随系统"), LIGHT("浅色模式"), DARK("深色模式") }

    private const val PREFS = "casetracker_settings"
    private const val KEY_THEME = "theme_mode"
    private const val KEY_HAPTICS = "haptics_enabled"
    // v1.70：办案民警记忆（历史输入过的民警姓名，编辑页可点选复用）
    private const val KEY_OFFICERS = "officer_history"
    // v1.90.3：案件页悬浮按钮拖拽位置（px；未设置时为 -1 → 用默认位）
    private const val KEY_FAB_X = "fab_x"
    private const val KEY_FAB_Y = "fab_y"

    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _hapticsEnabled = MutableStateFlow(true)
    val hapticsEnabled: StateFlow<Boolean> = _hapticsEnabled.asStateFlow()

    private val _officers = MutableStateFlow<List<String>>(emptyList())
    val officers: StateFlow<List<String>> = _officers.asStateFlow()

    private val _fabPosition = MutableStateFlow<Pair<Float, Float>?>(null)
    /** 案件页悬浮按钮拖拽位置（px）；null = 未设置，用默认位 */
    val fabPosition: StateFlow<Pair<Float, Float>?> = _fabPosition.asStateFlow()

    fun load(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val name = prefs.getString(KEY_THEME, ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name
        _themeMode.value = runCatching { ThemeMode.valueOf(name) }.getOrDefault(ThemeMode.SYSTEM)
        _hapticsEnabled.value = prefs.getBoolean(KEY_HAPTICS, true)
        _officers.value = prefs.getString(KEY_OFFICERS, "")
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?: emptyList()
        val fx = prefs.getFloat(KEY_FAB_X, -1f)
        val fy = prefs.getFloat(KEY_FAB_Y, -1f)
        _fabPosition.value = if (fx >= 0f && fy >= 0f) fx to fy else null
    }

    fun setThemeMode(context: Context, mode: ThemeMode) {
        _themeMode.value = mode
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_THEME, mode.name).apply()
    }

    fun setHapticsEnabled(context: Context, enabled: Boolean) {
        _hapticsEnabled.value = enabled
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_HAPTICS, enabled).apply()
    }

    /** 记忆一个办案民警姓名（去重，最新在前，最多保留 20 个） */
    fun rememberOfficer(context: Context, name: String) {
        val clean = name.trim()
        if (clean.isBlank()) return
        val updated = (listOf(clean) + _officers.value.filter { it != clean }).distinct().take(20)
        _officers.value = updated
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_OFFICERS, updated.joinToString(",")).apply()
    }

    /** 删除一个已记忆的办案民警姓名 */
    fun forgetOfficer(context: Context, name: String) {
        val updated = _officers.value.filter { it != name }
        _officers.value = updated
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_OFFICERS, updated.joinToString(",")).apply()
    }

    /** 保存案件页悬浮按钮拖拽位置（px，相对内容区左上角） */
    fun saveFabPosition(context: Context, x: Float, y: Float) {
        _fabPosition.value = x to y
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putFloat(KEY_FAB_X, x).putFloat(KEY_FAB_Y, y).apply()
    }
}
