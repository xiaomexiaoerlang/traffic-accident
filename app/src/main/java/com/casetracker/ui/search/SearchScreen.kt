package com.casetracker.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.casetracker.domain.model.Case
import com.casetracker.domain.model.CaseStatus
import com.casetracker.domain.model.CaseType
import com.casetracker.domain.model.FormatUtils
import com.casetracker.theme.DangerColor
import com.casetracker.ui.components.MiuiCard
import com.casetracker.ui.components.MiuiTopAppBar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SearchScreen(
    viewModel: SearchViewModel = hiltViewModel(),
    onOpenCase: (Long) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    Column(modifier = Modifier.fillMaxSize()) {
        MiuiTopAppBar(
            title = { Text("搜索", style = MaterialTheme.typography.titleLarge) }
        )

        OutlinedTextField(
            value = uiState.query,
            onValueChange = { viewModel.onQueryChange(it) },
            placeholder = { Text("搜索事故编号 / 地点 / 当事人 / 处理单位 / 民警") },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null)
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            )
        )

        when {
            uiState.isSearching -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            uiState.query.isBlank() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "输入关键词搜索事故与案件",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            uiState.results.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "未找到匹配结果",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.results, key = { it.id }) { case ->
                        SearchResultCard(
                            case = case,
                            dateFormat = dateFormat,
                            isRegistered = if (case.caseType == CaseType.ACCIDENT) true
                                           else case.id in uiState.violationRegisteredCaseIds,
                            onClick = { onOpenCase(case.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultCard(
    case: Case,
    dateFormat: SimpleDateFormat,
    isRegistered: Boolean,
    onClick: () -> Unit
) {
    val now = System.currentTimeMillis()
    val isOverdue = case.isOverdue(now)
    // 状态跟随流程：已认定 / 处理中（已完成受案登记）/ 待受案
    val statusText = when {
        case.status == CaseStatus.CLOSED -> "已认定"
        isRegistered -> "处理中"
        else -> "待受案"
    }

    MiuiCard(
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = case.caseType.displayName,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = case.caseNumber,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = statusText,
                style = MaterialTheme.typography.labelMedium,
                color = if (isOverdue) DangerColor else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = FormatUtils.unifiedTitle(case),
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(4.dp))
        val datePart = if (case.accidentDate > 0) dateFormat.format(Date(case.accidentDate)) else ""
        Text(
            text = listOfNotNull(datePart, case.location.ifBlank { null }).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        val people = buildList {
            if (case.selfParty.isNotBlank()) add(case.selfParty)
            if (case.otherParty.isNotBlank()) add(case.otherParty)
            if (case.handlingOfficer.isNotBlank()) add("民警：${case.handlingOfficer}")
        }
        if (people.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = people.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
