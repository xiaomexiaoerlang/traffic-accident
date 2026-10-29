package com.casetracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.casetracker.data.SettingsManager
import com.casetracker.theme.MiuiTheme
import com.casetracker.ui.navigation.AppNavigation
import dagger.hilt.android.AndroidEntryPoint
import java.io.File

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        SettingsManager.load(applicationContext)
        setContent {
            val themeMode by SettingsManager.themeMode.collectAsStateWithLifecycle()
            val darkTheme = when (themeMode) {
                SettingsManager.ThemeMode.SYSTEM -> isSystemInDarkTheme()
                SettingsManager.ThemeMode.LIGHT -> false
                SettingsManager.ThemeMode.DARK -> true
            }
            MiuiTheme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // v1.64：全局振触感反馈（设置开关控制所有点击振动）
                    com.casetracker.ui.components.GlobalHaptics {
                        AppNavigation()
                    }
                }
            }
            // v1.90.1：上次崩溃日志弹窗（便于真机排查）
            CrashLogDialog()
        }
    }
}

/** 若应用私有目录存在 crash.log，弹窗展示堆栈，供用户截图反馈；确认后删除文件。 */
@Composable
private fun CrashLogDialog() {
    val context = LocalContext.current
    var logText by remember { mutableStateOf<String?>(null) }
    val content = remember {
        runCatching {
            val f = File(context.getExternalFilesDir(null), "crash.log")
            if (f.exists()) f.readText() else null
        }.getOrNull()
    }
    if (logText == null && content != null) logText = content
    logText?.let { text ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("上次异常退出（请反馈）") },
            text = { Text(text.takeLast(1800), style = MaterialTheme.typography.bodySmall) },
            confirmButton = {
                TextButton(onClick = {
                    runCatching {
                        File(context.getExternalFilesDir(null), "crash.log").delete()
                    }
                    logText = null
                }) { Text("知道了") }
            }
        )
    }
}
