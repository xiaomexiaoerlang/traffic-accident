package com.casetracker.ui.components

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import com.casetracker.data.SettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * v1.90 全局振触感反馈（迁移至 IndicationNodeFactory · Compose 1.7 API）：
 * v1.64 只覆盖系统 HapticFeedback 回调入口，而普通点击不会触发该系统回调，导致无振动；
 * v1.65 起包装全局 LocalIndication——任意可点击控件**按下瞬间**直接触发 25ms 短振动；
 * 涟漪绘制委托系统默认 Indication（视觉不丢失）；系统 HapticFeedback 覆盖保留（长按等场景）；
 * 开关状态由设置页「振触感反馈」控制（SettingsManager.hapticsEnabled），关闭后全局不再振动。
 */
@Composable
fun GlobalHaptics(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val baseIndication = LocalIndication.current
    val vibratingIndication = remember(baseIndication) {
        object : IndicationNodeFactory {
            override fun create(interactionSource: InteractionSource): DelegatableNode =
                HapticRippleNode(
                    context = context,
                    interactionSource = interactionSource,
                    baseNode = (baseIndication as? IndicationNodeFactory)?.create(interactionSource)
                )

            override fun equals(other: Any?): Boolean = other === this

            override fun hashCode(): Int = 0x7A7F
        }
    }
    CompositionLocalProvider(
        LocalIndication provides vibratingIndication,
        LocalHapticFeedback provides object : HapticFeedback {
            override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
                if (SettingsManager.hapticsEnabled.value) {
                    vibrateTick(context)
                }
            }
        }
    ) {
        content()
    }
}

private class HapticRippleNode(
    private val context: Context,
    private val interactionSource: InteractionSource,
    private val baseNode: DelegatableNode?
) : DelegatingNode() {

    init {
        // 系统涟漪节点挂为 delegate：由 Modifier.Node 链自动分发绘制，视觉不丢失
        baseNode?.let { delegate(it) }
    }

    private var wasPressed by mutableStateOf(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onAttach() {
        scope.launch {
            interactionSource.interactions.collect { interaction ->
                val isPress = interaction is PressInteraction.Press
                // 按下上升沿（false -> true）触发一次短振动；持续按住/重复重组不重复振动
                if (isPress && !wasPressed && SettingsManager.hapticsEnabled.value) {
                    vibrateTick(context)
                }
                wasPressed = isPress
            }
        }
    }

    override fun onDetach() {
        scope.cancel()
    }

}

/** 短振动 25ms（DEFAULT_AMPLITUDE），设备不支持振动时静默跳过 */
private fun vibrateTick(context: Context) {
    val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
    if (vibrator.hasVibrator()) {
        runCatching {
            vibrator.vibrate(VibrationEffect.createOneShot(25, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }
}
