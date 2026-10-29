package com.casetracker.ui.edit

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.casetracker.domain.model.AccidentProcedure
import com.casetracker.domain.model.AccidentType
import com.casetracker.domain.model.CaseStatus
import com.casetracker.domain.model.CaseType
import com.casetracker.domain.model.ViolationType
import com.casetracker.theme.InfoColor
import com.casetracker.theme.SuccessColor
import com.casetracker.ui.components.MiuiButton
import com.casetracker.ui.components.MiuiCard
import com.casetracker.ui.components.MiuiTextField
import com.casetracker.ui.components.MiuiTopAppBar

@OptIn(
    ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    ExperimentalFoundationApi::class
)
@Composable
fun CaseEditScreen(
    caseId: Long?,
    viewModel: CaseEditViewModel = hiltViewModel(),
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val shouldGoBack by viewModel.shouldNavigateBack.collectAsStateWithLifecycle()
    // v1.81：案件名称长按修改对话框状态
    var showTitleEdit by remember { mutableStateOf(false) }
    var titleEditText by remember { mutableStateOf("") }

    LaunchedEffect(caseId) { viewModel.loadCase(caseId) }
    LaunchedEffect(shouldGoBack) {
        if (shouldGoBack) { onSaved(); viewModel.onNavigationConsumed() }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
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

                // 案件名称（自动生成预览，v1.81 长按可修改自定义名称）
                val autoTitle = com.casetracker.domain.model.FormatUtils
                    .buildCaseTitle(
                        uiState.accidentDate,
                        uiState.parties.firstOrNull { it.isNotBlank() } ?: "",
                        uiState.caseType,
                        uiState.description
                    )
                val previewTitle = uiState.titleOverride.ifBlank { autoTitle }
                MiuiCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = {},
                                onLongClick = {
                                    if (uiState.caseType == com.casetracker.domain.model.CaseType.ACCIDENT) {
                                        viewModel.onEvent(EditUiEvent.UpdateTitleOverride(previewTitle))
                                    }
                                    titleEditText = previewTitle
                                    showTitleEdit = true
                                }
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "案件名称（自动生成，长按可修改）",
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
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "长按修改名称",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
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

                    // 伤情情况（伤人事故时显示，可手动录入或由智能录入自动识别）
                    if (uiState.accidentType == AccidentType.INJURY) {
                        MiuiTextField(
                            value = uiState.injuryDetail,
                            onValueChange = { viewModel.onEvent(EditUiEvent.UpdateInjuryDetail(it)) },
                            label = "伤情情况",
                            placeholder = "如：郭伟头部擦伤、右腿骨折",
                            singleLine = false,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // 处理程序（v1.63：简易程序取消，仅「一般程序 / 一般程序快速处理」）
                    Text("处理程序", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(AccidentProcedure.GENERAL, AccidentProcedure.QUICK).forEach { proc ->
                            FilterChip(
                                selected = uiState.procedure == proc,
                                onClick = { viewModel.onEvent(EditUiEvent.UpdateProcedure(proc)) },
                                label = { Text(proc.displayName, style = MaterialTheme.typography.labelMedium) }
                            )
                        }
                    }
                    // 一般程序快速处理提示：保存后自动跳过鉴定与事故调查报告
                    if (uiState.procedure == AccidentProcedure.QUICK) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "选择后流程自动跳过鉴定与事故调查报告，自事故发生之日起 10 个工作日内出具事故认定书。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
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

                    // 办案民警（v1.70：输入后自动记忆，历史民警可点选复用、可删除）
                    MiuiTextField(
                        value = uiState.handlingOfficer,
                        onValueChange = { viewModel.onEvent(EditUiEvent.UpdateHandlingOfficer(it)) },
                        label = "办案民警",
                        placeholder = "请输入办案民警姓名（选填）",
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (uiState.officerOptions.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "历史民警（点击选用，× 删除记忆）",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        // 历史民警横向流式排布（FlowRow）
                        androidx.compose.foundation.layout.FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            uiState.officerOptions.forEach { name ->
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(
                                            if (uiState.handlingOfficer == name)
                                                MaterialTheme.colorScheme.primaryContainer
                                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                        )
                                        .clickable { viewModel.onEvent(EditUiEvent.SelectOfficer(name)) }
                                        .padding(start = 12.dp, top = 6.dp, bottom = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        name,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (uiState.handlingOfficer == name)
                                            MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface
                                    )
                                    IconButton(
                                        onClick = { viewModel.onEvent(EditUiEvent.ForgetOfficer(name)) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "删除记忆 $name",
                                            tint = MaterialTheme.colorScheme.outline,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
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

                // 违法类型（v1.66）：违法行为案件时，当事人信息下方选择 行政案件/刑事案件，详情流程按案型切换
                if (uiState.caseType == CaseType.VIOLATION) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("违法类型", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ViolationType.entries.forEach { type ->
                            FilterChip(
                                selected = uiState.violationType == type,
                                onClick = { viewModel.onEvent(EditUiEvent.UpdateViolationType(type)) },
                                label = { Text(type.displayName, style = MaterialTheme.typography.labelMedium) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (uiState.violationType == ViolationType.ADMINISTRATIVE)
                            "行政案件：违法查处 → 受案登记 → 立案受理 → 调查取证 → 处罚前告知 → 作出处罚决定 → 决定书送达 → 执行"
                        else
                            "刑事案件：违法查处 → 受案登记 → 刑事立案 → 侦查取证 → 侦查终结 → 移送审查起诉",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // v1.80 初始状态：事故案件状态跟随流程自动派生（处理中→已认定），仅违法等其他案件可手动选择
                Text("当前状态", style = MaterialTheme.typography.titleMedium)
                if (uiState.caseType == com.casetracker.domain.model.CaseType.ACCIDENT) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "事故案件状态将跟随流程自动更新：处理中 → 已认定（无需手动选择）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
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

    // v1.81：案件名称长按修改对话框（事故/违法均可修改）
    if (showTitleEdit) {
        AlertDialog(
            onDismissRequest = { showTitleEdit = false },
            title = { Text("修改案件名称") },
            text = {
                OutlinedTextField(
                    value = titleEditText,
                    onValueChange = { titleEditText = it },
                    singleLine = true,
                    label = { Text("案件名称") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.onEvent(EditUiEvent.UpdateTitleOverride(titleEditText.trim()))
                        showTitleEdit = false
                    }
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showTitleEdit = false }) { Text("取消") }
            }
        )
    }
}
