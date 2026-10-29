package com.casetracker.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.casetracker.theme.CardCorner
import com.casetracker.theme.Spacing

/**
 * 澎湃 OS / MIUI 风格卡片：16dp 大圆角（v1.71 统一令牌） + 细描边 + 柔和阴影 + 8dp 栅格内边距，
 * 信息分层清晰。onClick/onLongClick 通过外层 combinedClickable 处理，避免 Material3 Card 内部 onClick 吞掉点击事件。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MiuiCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(CardCorner)
    val cardModifier = modifier
        .clip(shape)
        .then(
            if (onClick != null || onLongClick != null) {
                Modifier.combinedClickable(
                    onClick = { onClick?.invoke() },
                    onLongClick = { onLongClick?.invoke() }
                )
            } else Modifier
        )
        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), shape)
    Card(
        modifier = cardModifier,
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp, pressedElevation = 3.dp)
    ) {
        Column(modifier = Modifier.padding(Spacing.dp16), content = content)
    }
}
