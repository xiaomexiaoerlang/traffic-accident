package com.casetracker.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.casetracker.domain.model.AccidentFlowTemplate
import com.casetracker.domain.model.CaseType
import com.casetracker.domain.model.CheckListTemplates
import com.casetracker.domain.model.FormatUtils
import com.casetracker.domain.model.NodeState
import com.casetracker.domain.model.ViolationFlowTemplate
import com.casetracker.theme.MiuiPrimary
import com.casetracker.theme.SuccessColor
import com.casetracker.theme.WarningColor

/**
 * v1.82 办案清单 · 右滑抽屉：
 * 案件详情页左上角清单按钮或屏幕左边缘右滑唤出，
 * 按流程节点列出「应办事项」勾选清单与关注点，勾选状态每案独立本地保存。
 */
@Composable
fun CaseChecklistDrawer(
    uiState: DetailUiState,
    onToggle: (nodeCode: String, itemIndex: Int, itemText: String, done: Boolean) -> Unit,
    onClose: () -> Unit
) {
    val case = uiState.case ?: return
    val template = CheckListTemplates.templateFor(case)
    // 节点顺序（code → 名称），统一事故/违法两种 NodeDef 类型为 Pair
    val pathNodes: List<Pair<String, String>> = when (case.caseType) {
        CaseType.ACCIDENT -> AccidentFlowTemplate.pathNodes(case).map { it.code to it.name }
        CaseType.VIOLATION -> ViolationFlowTemplate.pathNodes(case).map { it.code to it.name }
        else -> emptyList()
    }
    // 按流程路径顺序过滤出有清单的节点（跳过 V0 违法查处等无清单节点）
    val checklistNodes = pathNodes.filter { (code, _) -> template.containsKey(code) }
    // 当前节点：第一个未完成节点（已认定终态则无当前节点）
    val currentNodeCode = uiState.nodes.firstOrNull { it.state != NodeState.DONE }?.code
    val doneMap = uiState.checklistDone
    // 展开状态：默认展开当前节点，其余收起
    var expandedCode by remember(case.id) { mutableStateOf(currentNodeCode) }
    // v1.85 案卷目录：默认日常案件版（左）+ 执法考评版（右）；标题点击收纳；案卷目录默认折叠
    var volumeMode by remember(case.id) { mutableStateOf("daily") }
    var volumeCollapsed by remember(case.id) { mutableStateOf(true) }
    // v1.85 点击「办案清单」头部可收纳/展开下方全部内容（默认展开）
    var contentCollapsed by remember(case.id) { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(340.dp)
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 抽屉头部：点击「办案清单」收纳/展开下方内容；案件名称 + 进度
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MiuiPrimary)
                .clickable { contentCollapsed = !contentCollapsed }
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "办案清单",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
                androidx.compose.material3.Icon(
                    imageVector = if (contentCollapsed) {
                        Icons.Default.ExpandMore
                    } else {
                        Icons.Default.ExpandLess
                    },
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "关闭", tint = Color.White)
                }
            }
            Text(
                text = FormatUtils.unifiedTitle(case),
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )
            // 进度条 + 百分比
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color.White.copy(alpha = 0.3f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(case.progress / 100f)
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color.White)
                    )
                }
                Text(
                    text = "${case.progress}%",
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }

        // 节点清单列表（v1.85：头部收纳时隐藏下方全部内容）
        if (contentCollapsed) {
            Spacer(modifier = Modifier.weight(1f))
        } else {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)
        ) {
            // ===== v1.84 事故：台账式流程清单（10 节点，点左侧圆点 = 完成）=====
            if (case.caseType == CaseType.ACCIDENT) {
                item(key = "ledger_header") {
                    Text(
                        text = "流程进度",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 2.dp)
                    )
                }
                items(
                    CheckListTemplates.ACCIDENT_LEDGER.indices.toList(),
                    key = { "ledger_$it" }
                ) { index ->
                    val text = CheckListTemplates.ACCIDENT_LEDGER[index]
                    val code = "LEDGER_$index"
                    val isDone = index in doneMap[code].orEmpty()
                    val isCurrent = !isDone && index == CheckListTemplates.ACCIDENT_LEDGER.indices
                        .firstOrNull { it !in doneMap["LEDGER_$it"].orEmpty() }
                    LedgerRow(
                        text = text,
                        isDone = isDone,
                        isCurrent = isCurrent,
                        onClick = { onToggle(code, 0, text, !isDone) }
                    )
                }
            } else {
                items(checklistNodes, key = { it.first }) { (nodeCode, nodeName) ->
                    val check = template[nodeCode] ?: return@items
                    val doneSet = doneMap[nodeCode].orEmpty()
                    val doneCount = check.items.indices.count { it in doneSet }
                    val allDone = doneCount == check.items.size
                    val isCurrent = nodeCode == currentNodeCode
                    ChecklistNodeGroup(
                        name = nodeName,
                        items = check.items,
                        tip = check.tip,
                        doneSet = doneSet,
                        allDone = allDone,
                        doneCount = doneCount,
                        isCurrent = isCurrent,
                        expanded = expandedCode == nodeCode,
                        onToggleExpand = { expandedCode = if (expandedCode == nodeCode) null else nodeCode },
                        onToggleItem = { index, text, done ->
                            onToggle(nodeCode, index, text, done)
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            // ===== 案件专属板块 =====
            if (case.caseType == CaseType.ACCIDENT) {
                // 事故案件：道路交通事故案卷目录（双版本 + 标题点击收纳）
                val volumeList = if (volumeMode == "daily") {
                    CheckListTemplates.ACCIDENT_VOLUME_DAILY
                } else {
                    CheckListTemplates.ACCIDENT_VOLUME_APPRAISAL
                }
                val volumeCode = if (volumeMode == "daily") {
                    CheckListTemplates.VOLUME_DAILY_CODE
                } else {
                    CheckListTemplates.VOLUME_APPRAISAL_CODE
                }
                val volumeDone = doneMap[volumeCode].orEmpty()
                val volumeCount = volumeDone.count { it < volumeList.size }
                item(key = "volume_header") {
                    Column(modifier = Modifier.padding(top = 14.dp)) {
                        // 标题行：点击收纳下方
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { volumeCollapsed = !volumeCollapsed }
                                .padding(horizontal = 2.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = CheckListTemplates.VOLUME_TITLE,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                            )
                            androidx.compose.material3.Icon(
                                imageVector = if (volumeCollapsed) {
                                    Icons.Default.ExpandMore
                                } else {
                                    Icons.Default.ExpandLess
                                },
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            Text(
                                text = "已归档 $volumeCount/${volumeList.size}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MiuiPrimary,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(Color(0xFFE8F1FE))
                                    .padding(horizontal = 10.dp, vertical = 3.dp)
                            )
                        }
                        // 双版本切换：日常案件版（左，默认）| 执法考评版（右）
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp)
                                .clip(RoundedCornerShape(50))
                                .background(Color(0xFFF2F4F7))
                                .padding(2.dp)
                        ) {
                            VolumeModeTab(
                                text = "日常案件版",
                                selected = volumeMode == "daily",
                                onClick = { volumeMode = "daily" },
                                modifier = Modifier.weight(1f)
                            )
                            VolumeModeTab(
                                text = "执法考评版",
                                selected = volumeMode == "appraisal",
                                onClick = { volumeMode = "appraisal" },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Text(
                            text = "勾选 = 已归档入卷",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 2.dp, top = 4.dp, bottom = 2.dp)
                        )
                    }
                }
                if (!volumeCollapsed) {
                    items(volumeList.indices.toList(), key = { "vol_${volumeMode}_$it" }) { index ->
                        val text = volumeList[index]
                        val isDone = index in volumeDone
                        ChecklistToggleRow(
                            no = index + 1,
                            text = text,
                            isDone = isDone,
                            onClick = { onToggle(volumeCode, index, text, !isDone) }
                        )
                    }
                }
            } else if (case.caseType == CaseType.VIOLATION &&
                case.violationType == com.casetracker.domain.model.ViolationType.CRIMINAL
            ) {
                // 醉驾（刑事违法）案件：醉驾案件材料审核表
                val matDone = doneMap[CheckListTemplates.DRUNK_MATERIAL_CODE].orEmpty()
                val matCount = matDone.count { it < CheckListTemplates.DRUNK_MATERIALS.size }
                item(key = "drunk_header") {
                    SectionHeader(
                        title = CheckListTemplates.DRUNK_TITLE,
                        subtitle = "相关材料 · 勾选 = 材料已具备",
                        badge = "已备 $matCount/${CheckListTemplates.DRUNK_MATERIALS.size}"
                    )
                }
                items(
                    CheckListTemplates.DRUNK_MATERIALS.indices.toList(),
                    key = { "mat_$it" }
                ) { index ->
                    val text = CheckListTemplates.DRUNK_MATERIALS[index]
                    val isDone = index in matDone
                    ChecklistToggleRow(
                        no = index + 1,
                        text = text,
                        isDone = isDone,
                        onClick = { onToggle(CheckListTemplates.DRUNK_MATERIAL_CODE, index, text, !isDone) }
                    )
                }
                // 办案系统 3 项
                val sysDone = doneMap[CheckListTemplates.DRUNK_SYSTEM_CODE].orEmpty()
                item(key = "sys_header") {
                    Text(
                        text = "办案系统",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        modifier = Modifier.padding(top = 14.dp, bottom = 2.dp)
                    )
                }
                items(
                    CheckListTemplates.DRUNK_SYSTEM.indices.toList(),
                    key = { "sys_$it" }
                ) { index ->
                    val text = CheckListTemplates.DRUNK_SYSTEM[index]
                    val isDone = index in sysDone
                    ChecklistToggleRow(
                        no = index + 1,
                        text = text,
                        isDone = isDone,
                        onClick = { onToggle(CheckListTemplates.DRUNK_SYSTEM_CODE, index, text, !isDone) }
                    )
                }
                // 审核情况提示
                item(key = "audit_hint") {
                    Column(modifier = Modifier.padding(top = 14.dp)) {
                        Text(
                            text = "审核情况",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        listOf("移交单位领导审核", "接收单位领导审核", "大队领导审核").forEach { label ->
                            Text(
                                text = "⚠ $label · 未填写",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFE65100),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFFFFF8F0))
                                    .padding(horizontal = 8.dp, vertical = 7.dp)
                            )
                        }
                    }
                }
            }
        }
        }

        // 底部说明
        Text(
            text = "点按清单项勾选 · 点击节点名展开/收起",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        )
    }
}

/** 单个节点的清单分组：标题行（展开/收起）+ 勾选条目 + 关注点 */
@Composable
private fun ChecklistNodeGroup(
    name: String,
    items: List<String>,
    tip: String,
    doneSet: Set<Int>,
    allDone: Boolean,
    doneCount: Int,
    isCurrent: Boolean,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onToggleItem: (index: Int, text: String, done: Boolean) -> Unit
) {
    val cardBg = if (isCurrent) Color(0xFFF2F6FD) else Color.Transparent
    val borderColor = if (isCurrent) MiuiPrimary.copy(alpha = 0.18f) else Color.Transparent

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(cardBg)
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
    ) {
        // 标题行：点击切换展开
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggleExpand)
                .padding(horizontal = 12.dp, vertical = 12.dp)
        ) {
            // 节点状态点
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            allDone -> SuccessColor
                            isCurrent -> MiuiPrimary
                            else -> Color(0xFFD0D5DC)
                        }
                    )
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isCurrent) MiuiPrimary else MaterialTheme.colorScheme.onSurface
                )
                if (isCurrent) {
                    Text(
                        text = "当前节点",
                        style = MaterialTheme.typography.labelSmall,
                        color = MiuiPrimary
                    )
                }
            }
            Text(
                text = if (allDone) "已办 $doneCount/${items.size}" else "待办 $doneCount/${items.size}",
                style = MaterialTheme.typography.labelMedium,
                color = when {
                    allDone -> SuccessColor
                    isCurrent -> WarningColor
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) "收起" else "展开",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp)
            )
        }

        // 展开区：勾选条目 + 关注点
        if (expanded) {
            items.forEachIndexed { index, text ->
                val isDone = index in doneSet
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onToggleItem(index, text, !isDone) }
                        .padding(horizontal = 12.dp, vertical = 9.dp)
                ) {
                    if (isDone) {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(SuccessColor),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "✓",
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFFF3E0))
                                .border(2.dp, WarningColor, CircleShape)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isDone) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        textDecoration = if (isDone) TextDecoration.LineThrough else null
                    )
                }
            }
            if (tip.isNotBlank()) {
                Text(
                    text = "⚠ $tip",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFE65100),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFFFFF8F0))
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}

/** v1.83 案件专属板块标题：标题 + 副题 + 进度胶囊 */
@Composable
private fun SectionHeader(
    title: String,
    subtitle: String,
    badge: String
) {
    Column(modifier = Modifier.padding(top = 14.dp, bottom = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = badge,
                style = MaterialTheme.typography.labelMedium,
                color = MiuiPrimary,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFFE8F1FE))
                    .padding(horizontal = 10.dp, vertical = 3.dp)
            )
        }
        Text(
            text = subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp, bottom = 6.dp)
        )
    }
}

/** v1.83 板块勾选行：序号 + 文本 + 勾选状态（已办绿圈✓ / 待办橙框） */
@Composable
private fun ChecklistToggleRow(
    no: Int,
    text: String,
    isDone: Boolean,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 8.dp)
    ) {
        if (isDone) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(SuccessColor),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "✓",
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFFF3E0))
                    .border(2.dp, WarningColor, CircleShape)
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = "$no · $text",
            style = MaterialTheme.typography.bodyMedium,
            color = if (isDone) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            textDecoration = if (isDone) TextDecoration.LineThrough else null
        )
    }
}

/** v1.84 台账式流程行：左侧圆点点击 = 完成（√）/ 再点取消；当前节点高亮
 *  v1.86 修复：clickable 移至最外层（padding 之后），圆点命中区放大到 40dp，整行任意位置均可点击 */
@Composable
private fun LedgerRow(
    text: String,
    isDone: Boolean,
    isCurrent: Boolean,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isCurrent) Color(0xFFF2F6FD) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onClick)
                .padding(11.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            isDone -> SuccessColor
                            isCurrent -> MiuiPrimary
                            else -> Color(0xFFFFF3E0)
                        }
                    )
                    .then(
                        if (!isDone && !isCurrent) Modifier.border(2.dp, WarningColor, CircleShape) else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isDone) {
                    Text(
                        text = "✓",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isCurrent) androidx.compose.ui.text.font.FontWeight.Bold else null,
            color = when {
                isDone -> MaterialTheme.colorScheme.onSurfaceVariant
                isCurrent -> MiuiPrimary
                else -> MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.weight(1f)
        )
        when {
            isDone -> Text(
                text = "已办",
                style = MaterialTheme.typography.labelMedium,
                color = SuccessColor
            )
            isCurrent -> Text(
                text = "当前",
                style = MaterialTheme.typography.labelMedium,
                color = MiuiPrimary,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFFE8F1FE))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            )
            else -> Text(
                text = "待办",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** v1.84 案卷目录版本切换胶囊 */
@Composable
private fun VolumeModeTab(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) MiuiPrimary else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 5.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.Bold else null,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
