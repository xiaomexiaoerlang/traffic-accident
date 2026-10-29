package com.casetracker.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * MD3 圆角令牌（v1.71）：
 * 卡片 16dp（澎湃 OS 大圆角）、按钮/胶囊 8-12dp、弹层 20-28dp；
 * 左滑按钮右缘弧度与卡片一致（16dp），保证衔接无缝。
 */
val MiuiShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

/** 卡片圆角：全局唯一来源，左滑按钮右缘弧度同步引用 */
val CardCorner = 16.dp
