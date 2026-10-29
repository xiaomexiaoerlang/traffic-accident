package com.casetracker.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.casetracker.theme.CardCorner
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** 单个动作按钮固定宽度（dp） */
private const val ACTION_WIDTH_DP = 96
/** 按钮圆角：与卡片一致（v1.71 统一令牌 CardCorner，右缘弧度衔接卡片） */
private val ACTION_CORNER = CardCorner
/** 按钮间距（dp）：按钮间无间隙 */
private const val ACTION_GAP_DP = 0

/**
 * 左滑露出动作按钮的容器（v1.66 定稿 / v1.71 加动画 / v1.72 修复跟手）：
 * 按钮与卡片**上下等高**（先测量内容卡片高度，操作区再按该高度填充——LazyColumn 约束高度无限时 fillMaxHeight 会失效，
 * 故用 onSizeChanged 记高后显式 height 填充）、按钮间无间隙、右缘贴卡片边缘（弧度与卡片一致）。
 *
 * 滑动实现（v1.72）：Animatable.snapTo 跟手拖动（零延迟，修复 v1.71 动画值滞后导致"滑不动"），
 * 松手后 animateTo 以 spring 阻尼平滑吸附展开或回弹关闭。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SwipeActionsBox(
    modifier: Modifier = Modifier,
    actions: List<@Composable () -> Unit>,
    content: @Composable () -> Unit
) {
    val actionsWidth = (ACTION_WIDTH_DP * actions.size + ACTION_GAP_DP * (actions.size - 1)).dp
    val density = LocalDensity.current
    val actionsPx = with(density) { actionsWidth.toPx() }
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var cardHeightPx by remember { mutableIntStateOf(0) }

    Box(
        modifier = modifier
            .fillMaxWidth()
    ) {
        // 右侧操作区：高度 = 卡片实测高度；右缘贴卡片边缘（无留白），按钮间无间隙
        Row(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .height(with(density) { cardHeightPx.toDp() })
                .width(actionsWidth),
            horizontalArrangement = Arrangement.spacedBy(ACTION_GAP_DP.dp),
            verticalAlignment = Alignment.Top
        ) {
            actions.forEach { it() }
        }
        // 可滑动内容：测量其高度并回写，驱动操作区等高
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .onSizeChanged { cardHeightPx = it.height }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { },
                        onDragEnd = {
                            scope.launch {
                                offset.animateTo(
                                    targetValue = if (offset.value <= -actionsPx / 2) -actionsPx else 0f,
                                    animationSpec = spring(dampingRatio = 0.75f, stiffness = 320f)
                                )
                            }
                        },
                        onDragCancel = {
                            scope.launch {
                                offset.animateTo(
                                    targetValue = if (offset.value <= -actionsPx / 2) -actionsPx else 0f,
                                    animationSpec = spring(dampingRatio = 0.75f, stiffness = 320f)
                                )
                            }
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            // 跟手：snapTo 无动画延迟，拖动即走
                            scope.launch {
                                offset.snapTo(
                                    (offset.value + dragAmount).coerceIn(-actionsPx, 0f)
                                )
                            }
                        }
                    )
                }
        ) {
            content()
        }
    }
}

/**
 * 独立圆角矩形动作按钮（v1.66）：宽 96dp、**高度与卡片等高**、右缘弧度与卡片一致（16dp）、图标 + 白色文字；
 * 颜色由调用处传入（编辑/详情主题蓝 #1B7AE8、删除/移除柔和深红 #D64949，与软件主题协调）。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ActionBlockButton(
    label: String,
    background: Color,
    onClick: () -> Unit,
    icon: @Composable () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .width(ACTION_WIDTH_DP.dp)
            .fillMaxHeight()
            .clip(RoundedCornerShape(ACTION_CORNER))
            .background(background)
            .combinedClickable(onClick = onClick)
    ) {
        icon()
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
            fontWeight = FontWeight.Medium
        )
    }
}
