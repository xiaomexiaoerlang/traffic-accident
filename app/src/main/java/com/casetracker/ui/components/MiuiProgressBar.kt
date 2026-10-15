package com.casetracker.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.casetracker.theme.DangerColor
import com.casetracker.theme.SuccessColor
import com.casetracker.theme.WarningColor

@Composable
fun MiuiProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    isOverdue: Boolean = false
) {
    val animatedProgress = remember { Animatable(0f) }
    LaunchedEffect(progress) {
        animatedProgress.animateTo(
            targetValue = progress.coerceIn(0f, 1f),
            animationSpec = spring(dampingRatio = 0.85f, stiffness = 300f)
        )
    }

    val color = when {
        isOverdue -> DangerColor
        progress < 0.3f -> MaterialTheme.colorScheme.primary
        progress < 0.7f -> WarningColor
        else -> SuccessColor
    }

    LinearProgressIndicator(
        progress = { animatedProgress.value },
        modifier = modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp)),
        color = color,
        trackColor = MaterialTheme.colorScheme.surfaceVariant,
    )
}
