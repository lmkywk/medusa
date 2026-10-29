package io.github.aw1y2z.sesame.ui.miuix

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.aw1y2z.sesame.data.plan.PlanTargets
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField

/** 任务组选择器：卡片行、风险圆点、统一基线，避免 AlertDialog 多行文字对不齐。 */
@Composable
fun TaskGroupPickerDialog(
    groups: List<TaskGroupSpec>,
    selectedId: String?,
    onSelect: (TaskGroupSpec) -> Unit,
    onCreateGroup: () -> Unit,
    onAdvanced: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(22.dp))
                .background(Color.White)
                .padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
            Text(
                text = "选择任务组",
                fontSize = MedusaType.CardTitle,
                fontWeight = FontWeight.Bold,
                color = MedusaColors.Ink
            )
            Text(
                text = "先选组，再调时间；执行目标内的任务按所选类型过滤",
                fontSize = MedusaType.Caption,
                color = MedusaColors.Muted,
                modifier = Modifier.padding(top = 4.dp)
            )
            Spacer(Modifier.height(10.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                groups.forEachIndexed { index, group ->
                    GroupPickerRow(
                        group = group,
                        selected = group.id == selectedId,
                        onClick = { onSelect(group) }
                    )
                    if (index != groups.lastIndex) {
                        GroupDialogSeparator()
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onCreateGroup)
                    .padding(horizontal = 10.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "＋ 新建任务组",
                    fontSize = MedusaType.Body,
                    fontWeight = FontWeight.SemiBold,
                    color = MedusaColors.GreenDeep,
                    modifier = Modifier.weight(1f)
                )
                Text("›", fontSize = MedusaType.CardTitle, color = MedusaColors.Muted)
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onAdvanced)
                    .padding(horizontal = 10.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "高级：直接选择执行目标",
                    fontSize = MedusaType.Body,
                    fontWeight = FontWeight.SemiBold,
                    color = MedusaColors.GreenDeep,
                    modifier = Modifier.weight(1f)
                )
                Text("›", fontSize = MedusaType.CardTitle, color = MedusaColors.Muted)
            }
            Spacer(Modifier.height(6.dp))
            MedusaActionButton(
                text = "取消",
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                tone = MedusaButtonTone.Secondary,
                height = 40.dp
            )
        }
    }
}

@Composable
private fun GroupPickerRow(group: TaskGroupSpec, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) MedusaColors.Green.copy(alpha = 0.06f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(10.dp).background(group.risk.color, CircleShape))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = group.name,
                    fontSize = MedusaType.Body,
                    fontWeight = FontWeight.SemiBold,
                    color = MedusaColors.Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = group.risk.label,
                    fontSize = MedusaType.Caption,
                    fontWeight = FontWeight.SemiBold,
                    color = group.risk.color
                )
            }
            Text(
                text = group.composition,
                fontSize = MedusaType.Caption,
                color = MedusaColors.Muted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )
            Row(
                Modifier.padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                GroupSmallTag("约 ${group.estimatedMinutes} 分钟")
                if (group.executesFullTarget) {
                    GroupSmallTag("原型·按全量", Color(0xFFB7791F))
                }
            }
        }
        if (selected) {
            Text(
                text = "✓",
                fontSize = MedusaType.Body,
                fontWeight = FontWeight.Bold,
                color = MedusaColors.GreenDeep,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

/** 任务勾选与排序：已选区按顺序执行，未选区可添加；只保存组合，不改执行器。 */
@Composable
fun TaskSelectionDialog(
    group: TaskGroupSpec,
    selected: List<String>,
    onSave: (List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    var chosen by remember(group.id, selected) { mutableStateOf(selected.toList()) }

    fun move(index: Int, delta: Int) {
        if (index + delta !in chosen.indices) return
        val list = chosen.toMutableList()
        val item = list.removeAt(index)
        list.add(index + delta, item)
        chosen = list
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(22.dp))
                .background(Color.White)
                .padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
            Text(
                text = group.name + " · 调整任务",
                fontSize = MedusaType.CardTitle,
                fontWeight = FontWeight.Bold,
                color = MedusaColors.Ink
            )
            Text(
                text = "执行目标「" + PlanTargets.labelOf(group.target) + "」" +
                    if (group.mechanisms.isEmpty()) "；未限定类型，整域全跑"
                    else "；只跑 " + group.mechanisms.joinToString("、") { TaskGroups.mechanismLabel(it) },
                fontSize = MedusaType.Caption,
                color = MedusaColors.Muted,
                modifier = Modifier.padding(top = 4.dp)
            )
            Spacer(Modifier.height(12.dp))

            Text(
                text = "已选 " + chosen.size + " 项 · 按顺序执行",
                fontSize = MedusaType.Caption,
                fontWeight = FontWeight.SemiBold,
                color = MedusaColors.Ink
            )
            Spacer(Modifier.height(4.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 250.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                if (chosen.isEmpty()) {
                    Text(
                        text = "还没有选择任务",
                        fontSize = MedusaType.Caption,
                        color = MedusaColors.Muted,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                chosen.forEachIndexed { index, task ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(MedusaColors.Green.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = (index + 1).toString(),
                                fontSize = MedusaType.Caption,
                                fontWeight = FontWeight.SemiBold,
                                color = MedusaColors.GreenDeep
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = task,
                            fontSize = MedusaType.Body,
                            color = MedusaColors.Ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        TaskOrderButton("↑", enabled = index > 0) { move(index, -1) }
                        TaskOrderButton("↓", enabled = index < chosen.lastIndex) { move(index, 1) }
                        Text(
                            text = "移除",
                            fontSize = MedusaType.Caption,
                            color = MedusaColors.Stopped,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { chosen = chosen.filterNot { it == task } }
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Text(
                text = "可添加",
                fontSize = MedusaType.Caption,
                fontWeight = FontWeight.SemiBold,
                color = MedusaColors.Ink
            )
            Spacer(Modifier.height(4.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 180.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                val available = group.tasks.filterNot { chosen.contains(it) }
                if (available.isEmpty()) {
                    Text(
                        text = "该组的任务都已加入",
                        fontSize = MedusaType.Caption,
                        color = MedusaColors.Muted,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                available.forEach { task ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { chosen = chosen + task }
                            .padding(vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(MedusaColors.Line.copy(alpha = 0.6f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("+", fontSize = MedusaType.Caption, color = MedusaColors.Muted)
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = task,
                            fontSize = MedusaType.Body,
                            color = MedusaColors.Ink,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "添加",
                            fontSize = MedusaType.Caption,
                            fontWeight = FontWeight.SemiBold,
                            color = MedusaColors.GreenDeep
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            MedusaDialogActions(
                confirmText = "保存组合",
                onConfirm = { onSave(chosen) },
                onDismiss = onDismiss
            )
        }
    }
}

/** 高级目标选择：明确显示当前目标，并提供“返回任务组”，避免进了高级页像没有选择。 */
@Composable
fun TaskTargetPickerDialog(
    targets: List<String>,
    selectedTarget: String?,
    currentGroupName: String? = null,
    onSelect: (String) -> Unit,
    onBackToGroups: () -> Unit,
    onDismiss: () -> Unit
) {
    var picked by remember(selectedTarget) { mutableStateOf(selectedTarget) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(22.dp))
                .background(Color.White)
                .padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
            Text(
                text = "选择执行目标（高级）",
                fontSize = MedusaType.CardTitle,
                fontWeight = FontWeight.Bold,
                color = MedusaColors.Ink
            )
            Text(
                text = "先选目标，再点确定；也可以返回任务组",
                fontSize = MedusaType.Caption,
                color = MedusaColors.Muted,
                modifier = Modifier.padding(top = 4.dp)
            )
            if (!currentGroupName.isNullOrBlank()) {
                Text(
                    text = "当前：任务组「" + currentGroupName + "」；选下面的目标会切换为直接执行",
                    fontSize = MedusaType.Caption,
                    color = MedusaColors.GreenDeep,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Spacer(Modifier.height(10.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                targets.forEachIndexed { index, target ->
                    val selected = target == picked
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selected) MedusaColors.Green.copy(alpha = 0.06f) else Color.Transparent)
                            .clickable { picked = target }
                            .padding(horizontal = 10.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .size(9.dp)
                                .background(
                                    if (selected) MedusaColors.GreenDeep else MedusaColors.Line,
                                    CircleShape
                                )
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = PlanTargets.labelOf(target),
                            fontSize = MedusaType.Body,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = MedusaColors.Ink,
                            modifier = Modifier.weight(1f)
                        )
                        if (selected) {
                            Text(
                                text = "✓",
                                fontSize = MedusaType.Body,
                                fontWeight = FontWeight.Bold,
                                color = MedusaColors.GreenDeep
                            )
                        }
                    }
                    if (index != targets.lastIndex) {
                        GroupDialogSeparator()
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            MedusaActionButton(
                text = "返回任务组",
                onClick = onBackToGroups,
                modifier = Modifier.fillMaxWidth(),
                tone = MedusaButtonTone.Secondary,
                height = 40.dp
            )
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MedusaActionButton(
                    text = "取消",
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    tone = MedusaButtonTone.Ghost,
                    height = 40.dp
                )
                MedusaActionButton(
                    text = "确定",
                    onClick = { picked?.let(onSelect) },
                    modifier = Modifier.weight(1f),
                    enabled = picked != null,
                    height = 40.dp
                )
            }
        }
    }
}

/** 新建任务组：以当前选中的组为基础，命名后保存为自定义组，再继续调整任务。 */
@Composable
fun TaskGroupCreateDialog(
    base: TaskGroupSpec,
    onCreate: (name: String, risk: GroupRisk, minutes: Int, mechanisms: List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember(base.id) { mutableStateOf(base.name + " 自定义") }
    var risk by remember(base.id) { mutableStateOf(base.risk) }
    var minutes by remember(base.id) { mutableIntStateOf(base.estimatedMinutes) }
    var mechanisms by remember(base.id) { mutableStateOf(base.mechanisms) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(22.dp))
                .background(Color.White)
                .padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
            Text(
                text = "新建任务组",
                fontSize = MedusaType.CardTitle,
                fontWeight = FontWeight.Bold,
                color = MedusaColors.Ink
            )
            Text(
                text = "基础目标：" + PlanTargets.labelOf(base.target) +
                    " · 创建后可继续调整任务与顺序",
                fontSize = MedusaType.Caption,
                color = MedusaColors.Muted,
                modifier = Modifier.padding(top = 4.dp)
            )
            Spacer(Modifier.height(12.dp))
            TextField(
                value = name,
                onValueChange = { name = it },
                label = "任务组名称",
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "初始任务：" + base.tasks.joinToString(" · "),
                fontSize = MedusaType.Caption,
                color = MedusaColors.Ink
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "风险等级",
                fontSize = MedusaType.Caption,
                fontWeight = FontWeight.SemiBold,
                color = MedusaColors.Ink
            )
            Row(
                Modifier.padding(top = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GroupRisk.values().forEach { item ->
                    GroupChoiceChip(item.label, risk == item, item.color) { risk = item }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = "按类型执行（不选＝整域全跑）",
                fontSize = MedusaType.Caption,
                fontWeight = FontWeight.SemiBold,
                color = MedusaColors.Ink
            )
            Column(Modifier.padding(top = 5.dp)) {
                TaskGroups.mechanismOptions.chunked(4).forEach { rowItems ->
                    Row(
                        Modifier.padding(bottom = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowItems.forEach { (code, label) ->
                            val selected = code in mechanisms
                            GroupChoiceChip(label, selected, MedusaColors.GreenDeep) {
                                mechanisms = if (selected) mechanisms - code else mechanisms + code
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = "预计时长",
                fontSize = MedusaType.Caption,
                fontWeight = FontWeight.SemiBold,
                color = MedusaColors.Ink
            )
            Row(
                Modifier.padding(top = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(2, 5, 10, 15).forEach { item ->
                    GroupChoiceChip("$item 分钟", minutes == item, MedusaColors.GreenDeep) {
                        minutes = item
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            MedusaDialogActions(
                confirmText = "创建任务组",
                onConfirm = { onCreate(name.trim(), risk, minutes, mechanisms) },
                onDismiss = onDismiss,
                confirmEnabled = name.trim().isNotEmpty()
            )
        }
    }
}

@Composable
private fun GroupChoiceChip(
    text: String,
    selected: Boolean,
    tone: Color,
    onClick: () -> Unit
) {
    Text(
        text = text,
        fontSize = MedusaType.Caption,
        fontWeight = FontWeight.SemiBold,
        color = if (selected) tone else MedusaColors.Muted,
        modifier = Modifier
            .clip(RoundedCornerShape(99.dp))
            .background(if (selected) tone.copy(alpha = 0.12f) else Color(0xFFF2F4F7))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    )
}

@Composable
private fun TaskOrderButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Text(
        text = text,
        fontSize = MedusaType.Body,
        fontWeight = FontWeight.SemiBold,
        color = if (enabled) MedusaColors.GreenDeep else Color(0xFFC5CCD6),
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 7.dp, vertical = 4.dp)
    )
}

@Composable
private fun GroupSmallTag(text: String, tone: Color = MedusaColors.Muted) {
    Text(
        text = text,
        fontSize = MedusaType.Caption,
        color = tone,
        modifier = Modifier
            .clip(RoundedCornerShape(99.dp))
            .background(tone.copy(alpha = 0.08f))
            .padding(horizontal = 7.dp, vertical = 2.dp)
    )
}

@Composable
private fun GroupDialogSeparator() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp)
            .height(1.dp)
            .background(MedusaColors.Line.copy(alpha = 0.6f))
    )
}
