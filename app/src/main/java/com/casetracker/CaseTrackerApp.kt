package com.casetracker

import android.app.Application
import android.util.Log
import dagger.hilt.android.HiltAndroidApp
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@HiltAndroidApp
class CaseTrackerApp : Application() {

    override fun onCreate() {
        super.onCreate()
        installCrashCapture()
    }

    /**
     * v1.90.1：全局未捕获异常写入应用私有目录 crash.log（无需权限），
     * MainActivity 启动时检测到文件即弹窗展示堆栈，便于真机排查启动崩溃。
     * 捕获后仍交给系统默认处理器（不吞异常、不阻止崩溃）。
     */
    private fun installCrashCapture() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val file = File(getExternalFilesDir(null), "crash.log")
                val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    .format(Date())
                val content = buildString {
                    appendLine("===== CRASH $time =====")
                    appendLine("thread=${thread.name}")
                    append(Log.getStackTraceString(throwable))
                    appendLine()
                }
                file.appendText(content)
            }
            previous?.uncaughtException(thread, throwable)
                ?: run {
                    throwable.printStackTrace()
                    android.os.Process.killProcess(android.os.Process.myPid())
                }
        }
    }
}
