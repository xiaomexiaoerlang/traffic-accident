package com.casetracker.ui.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.casetracker.domain.model.AccidentProcedure
import com.casetracker.domain.model.AccidentType
import com.casetracker.domain.model.CaseStatus
import com.casetracker.domain.model.CaseType
import com.casetracker.theme.InfoColor
import com.casetracker.theme.SuccessColor
import com.casetracker.ui.components.MiuiButton
import com.casetracker.ui.components.MiuiCard
import com.casetracker.ui.components.MiuiTextField
import com.casetracker.ui.components.MiuiTopAppBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaseEditScreen(
    caseId: Long?,
    viewModel: CaseEditViewModel = hiltViewModel(),
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val shouldGoBack by viewModel.shouldNavigateBack.collectAsStateWithLifecycle()

    LaunchedEffect(caseId) { viewModel.loadCase(caseId) }
    LaunchedEffect(shouldGoBack) {
        if (shouldGoBack) { onSaved(); viewModel.onNavigationConsumed() }
    }

    Scaffold(
        topBar = {
            MiuiTopAppBar(
                title = {
                    Text(
                        if (uiState.isEditing) "编辑案件" else "新建案件",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { viewModel.onEvent(EditUiEvent.NavigateBack) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Spacer(modifier = Modifier.height(4.dp))

                // 智能录入卡片
                MiuiCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.width(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("智能录入", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "粘贴一段事故描述，自动识别日期、地点、当事人、民警等信息",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    MiuiTextField(
                        value = uiState.description,
                        onValueChange = { viewModel.onEvent(EditUiEvent.UpdateDescription(it)) },
                        label = "事故描述",
                        placeholder = "例如：2026年9月15日8点30分，张三驾驶小汽车在北京市海淀区中关村大街与李四发生碰撞，造成人员受伤，办案民警：王五",
                        singleLine = false,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    MiuiButton(
                        text = "智能解析并填充",
                        onClick = { viewModel.onEvent(EditUiEvent.ParseDescription(uiState.description)) },
                        isPrimary = false,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (uiState.parseMessage.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = uiState.parseMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (uiState.parseMessage.startsWith("✓")) SuccessColor else InfoColor
                        )
                    }
                }

                // 案件类型
                Text("案件类型", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CaseType.entries.forEach { type ->
                        FilterChip(
                            selected = uiState.caseType == type,
                            onClick = { viewModel.onEvent(EditUiEvent.UpdateCaseType(type)) },
                            label = { Text(type.displayName, style = MaterialTheme.typography.labelMedium) }
                        )
                    }
                }

                // 案件名称（自动生成预览，只读）
                val previewTitle = com.casetracker.domain.model.FormatUtils
                    .buildCaseTitle(
                        uiState.accidentDate,
                        uiState.parties.firstOrNull { it.isNotBlank() } ?: "",
                        uiState.caseType
                    )
                MiuiCard {
                    Text(
                        "案件名称（自动生成）",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = previewTitle.ifBlank { "填写事故日期与当事人后自动生成" },
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (previewTitle.isNotBlank()) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (uiState.caseType == CaseType.ACCIDENT) {
                    // 事故日期
                    MiuiTextField(
                        value = uiState.accidentDateText,
                        onValueChange = { viewModel.onEvent(EditUiEvent.UpdateAccidentDateText(it)) },
                        label = "事故日期",
                        placeholder = "例如：2026-09-10",
                        modifier = Modifier.fillMaxWidth()
                    )

                    // 事故时间
                    MiuiTextField(
                        value = uiState.accidentTime,
                        onValueChange = { viewModel.onEvent(EditUiEvent.UpdateAccidentTime(it)) },
                        label = "事故时间",
                        placeholder = "例如：5时10分许（选填）",
                        modifier = Modifier.fillMaxWidth()
                    )

                    // 事发地点（调整到事故时间下方）
                    MiuiTextField(
                        value = uiState.location,
                        onValueChange = { viewModel.onEvent(EditUiEvent.UpdateLocation(it)) },
                        label = "事发地点",
                        placeholder = "事故发生的道路/位置（选填）",
                        modifier = Modifier.fillMaxWidth()
                    )

                    // 事故类型
                    Text("事故类型", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AccidentType.entries.forEach { type ->
                            FilterChip(
                                selected = uiState.accidentType == type,
                                onClick = { viewModel.onEvent(EditUiEvent.UpdateAccidentType(type)) },
                                label = { Text(type.displayName, style = MaterialTheme.typography.labelMedium) }
                            )
                        }
                    }

                    // 处理程序
                    Text("处理程序", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AccidentProcedure.entries.forEach { proc ->
                            FilterChip(
                                selected = uiState.procedure == proc,
                                onClick = { viewModel.onEvent(EditUiEvent.UpdateProcedure(proc)) },
                                label = { Text(proc.displayName, style = MaterialTheme.typography.labelMedium) }
                            )
                        }
                    }

                    // 逃逸 / 鉴定延期
                    MiuiCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("肇事逃逸", style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    "对方存在逃逸行为",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = uiState.hitAndRun,
                                onCheckedChange = { viewModel.onEvent(EditUiEvent.UpdateHitAndRun(it)) },
                                colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("鉴定延期", style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    "检验鉴定延长 20 日（共 40 日）",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = uiState.appraisalExtended,
                                onCheckedChange = { viewModel.onEvent(EditUiEvent.UpdateAppraisalExtended(it)) },
                                colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary)
                            )
                        }
                    }

                    // 办案民警
                    MiuiTextField(
                        value = uiState.handlingOfficer,
                        onValueChange = { viewModel.onEvent(EditUiEvent.UpdateHandlingOfficer(it)) },
                        label = "办案民警",
                        placeholder = "请输入办案民警姓名（选填）",
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // 当事人信息（动态列表，不区分本方/对方）
                Text("当事人信息", style = MaterialTheme.typography.titleMedium)
                if (uiState.parties.isEmpty()) {
                    Text(
                        "暂无当事人，点击下方按钮添加",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                uiState.parties.forEachIndexed { index, party ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MiuiTextField(
                            value = party,
                            onValueChange = { viewModel.onEvent(EditUiEvent.UpdateParty(index, it)) },
                            label = "当事人 ${index + 1}",
                            placeholder = "请输入姓名",
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = { viewModel.onEvent(EditUiEvent.RemoveParty(index)) },
                            modifier = Modifier
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "删除当事人",
                                tint = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
                MiuiButton(
                    text = "添加当事人",
                    onClick = { viewModel.onEvent(EditUiEvent.AddParty) },
                    isPrimary = false,
                    modifier = Modifier.fillMaxWidth()
                )

                // 初始状态
                Text("当前状态", style = MaterialTheme.typography.titleMedium)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    CaseStatus.entries.forEach { status ->
                        FilterChip(
                            selected = uiState.status == status,
                            onClick = { viewModel.onEvent(EditUiEvent.UpdateStatus(status)) },
                            label = {
                                Text(
                                    status.displayName,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 保存按钮
                MiuiButton(
                    text = if (uiState.isSaving) "保存中..." else "保存",
                    onClick = { viewModel.onEvent(EditUiEvent.Save) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isSaving
                )
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
